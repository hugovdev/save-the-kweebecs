package me.hugo.thankmas.savethekweebecs.game.arena

import dev.kezz.miniphrase.MiniPhraseContext
import dev.kezz.miniphrase.audience.sendTranslated
import live.minehub.polarpaper.Config
import live.minehub.polarpaper.Polar
import me.hugo.thankmas.items.TranslatableItem
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.location.MapPoint
import me.hugo.thankmas.player.player
import me.hugo.thankmas.player.reset
import me.hugo.thankmas.player.updateBoardTags
import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.extension.announceTranslation
import me.hugo.thankmas.savethekweebecs.extension.end
import me.hugo.thankmas.savethekweebecs.extension.hasStarted
import me.hugo.thankmas.savethekweebecs.extension.playerData
import me.hugo.thankmas.savethekweebecs.game.events.ArenaEvent
import me.hugo.thankmas.savethekweebecs.game.map.ArenaMap
import me.hugo.thankmas.savethekweebecs.game.map.MapRegistry
import me.hugo.thankmas.savethekweebecs.team.MapTeam
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.api.trait.trait.Equipment
import net.citizensnpcs.trait.*
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.*
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.inject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap


/**
 * A representation of a playable game of Save The
 * Kweebecs in [arenaMap] with [displayName] as a name.
 */
public class Arena(public val arenaMap: ArenaMap, public val displayName: String) : TranslatedComponent {

    private val main = SaveTheKweebecs.instance()

    private val mapRegistry: MapRegistry by inject()
    private val scoreboardManager: me.hugo.thankmas.savethekweebecs.scoreboard.KweebecScoreboardManager by inject()
    private val itemManager: ItemSetRegistry by inject()

    public val uuid: UUID = UUID.randomUUID()

    public lateinit var world: World

    public var state: ArenaState = ArenaState.RESETTING
        set(state) {
            field = state

            if (state == ArenaState.RESETTING) return
            arenaPlayers().mapNotNull { it.player() }.forEach { setCurrentBoard(it) }
        }

    public var winnerTeam: MapTeam? = null

    public var time: Int = arenaMap.defaultCountdown

    public var eventIndex: Int = 0
    public val currentEvent: ArenaEvent?
        get() = arenaMap.events.getOrNull(eventIndex)?.first

    public val playersPerTeam: MutableMap<MapTeam, MutableList<UUID>> = mutableMapOf(
        Pair(arenaMap.defenderTeam, mutableListOf()),
        Pair(arenaMap.attackerTeam, mutableListOf())
    )

    private val spectators: MutableList<UUID> = mutableListOf()

    public val deadPlayers: ConcurrentMap<Player, Int> = ConcurrentHashMap()
    public val remainingNPCs: MutableMap<NPC, Boolean> = mutableMapOf()

    public var lastIcon: MutableMap<String, ItemStack> = mutableMapOf()

    init {
        main.logger.info("Creating game with map ${arenaMap.mapName} with display name $displayName...")
        createWorld(true)
        main.logger.info("$displayName is now available!")
    }

    /**
     * Attempts to add [player] to this game.
     *
     * It will fail if:
     * - The game has already started.
     * - The game is full.
     * - The player is already in a game.
     */
    public fun joinArena(player: Player) {
        if (hasStarted()) {
            player.sendTranslated("arena.join.started") {
                unparsed("arena_name", displayName)
            }
            return
        }

        if (teamPlayers().size >= arenaMap.maxPlayers) {
            player.sendTranslated("arena.join.full") {
                unparsed("arena_name", displayName)
            }
            return
        }

        val playerData = player.playerData()

        if (playerData.currentArena != null) {
            player.sendTranslated("arena.join.alreadyInArena")
            return
        }

        val lobbyLocation = arenaMap.lobbySpawnpoint.toLocation(world)

        player.reset(GameMode.ADVENTURE)
        player.teleport(lobbyLocation)

        playerData.currentArena = this

        // If every team has the same amount of players use prioritize attackers!
        val team = if (playersPerTeam.values.map { it.size }.distinct().size == 1) arenaMap.attackerTeam
        else playersPerTeam.keys.minBy { playersPerTeam[it]?.size ?: 0 }

        addPlayerTo(player, team)

        announceTranslation("arena.join.global") {
            unparsed("player_name", player.name)
            unparsed("current_players", arenaPlayers().size.toString())
            unparsed("max_players", arenaMap.maxPlayers.toString())
        }

        itemManager.giveSetNullable(state.itemSetKey, player)

        if (teamPlayers().size >= arenaMap.minPlayers && state == ArenaState.WAITING)
            state = ArenaState.STARTING
        else updateBoard("players", "max_players")

        setCurrentBoard(player)
    }

    /**
     * Removes [player] from the current game.
     *
     * If [disconnect] is false it will reset their
     * skin if needed, and it will send them to hub.
     */
    public fun leave(player: Player, disconnect: Boolean = false) {
        val playerData = player.playerData()

        if (!hasStarted()) {
            playerData.currentTeam?.let { removePlayerFrom(player, it) }

            announceTranslation("arena.leave.global") {
                unparsed("player_name", player.name)
                unparsed("current_players", arenaPlayers().size.toString())
                unparsed("max_players", arenaMap.maxPlayers.toString())
            }
        } else {
            player.damage(player.health)
            deadPlayers.remove(player)

            if (!disconnect) playerData.resetSkin()

            playerData.currentTeam?.let { removePlayerFrom(player, it) }

            if (state != ArenaState.FINISHING && state != ArenaState.RESETTING) {
                val teamsWithPlayers = playersPerTeam.filter { it.value.isNotEmpty() }.map { it.key }
                if (teamsWithPlayers.size == 1) this.end(teamsWithPlayers.first())
            }
        }

        if (!disconnect) {
            playerData.currentArena = null
            mapRegistry.sendToHub(player)
        }
    }

    /**
     * Empties the player pools.
     * Unloads previous game worlds.
     *
     * Creates a new world with the proper gamerule setup
     * and spawns/restores NPCs.
     *
     * Also resets [state], [time], [winnerTeam]
     * and [eventIndex].
     */
    public fun createWorld(firstTime: Boolean = true) {
        playersPerTeam.values.forEach { it.clear() }
        spectators.clear()
        deadPlayers.clear()

        if (!firstTime) Bukkit.unloadWorld(world, false)

        val arenaName = uuid.toString()

        Polar.loadWorld(
            arenaMap.polarWorld, arenaName, Config(
                "file",
                false,
                false,
                arenaMap.lobbySpawnpoint.toLocation(null),
                Difficulty.NORMAL,
                false,
                false,
                true,
                WorldType.NORMAL,
                World.Environment.NORMAL
            )
        )

        val newWorld = requireNotNull(Bukkit.getWorld(arenaName))
        { "Arena $uuid couldn't generate its world correctly!" }

        newWorld.setGameRule(GameRule.RANDOM_TICK_SPEED, 0)
        newWorld.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
        newWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
        newWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        newWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
        newWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false)
        newWorld.setGameRule(GameRule.DO_FIRE_TICK, false)

        newWorld.time = arenaMap.time

        world = newWorld

        if (firstTime) arenaMap.kidnappedSpawnpoints.forEach { remainingNPCs[createKidnappedNPC(it)] = false }
        else remainingNPCs.keys.forEach {
            remainingNPCs[it] = false
            it.spawn(it.storedLocation.toLocation(world))
        }

        state = ArenaState.WAITING
        time = arenaMap.defaultCountdown
        winnerTeam = null
        eventIndex = 0
    }

    /**
     * Creates an NPC in [mapPoint] for this arena.
     */
    private fun createKidnappedNPC(mapPoint: MapPoint): NPC {
        val attackerTeam = arenaMap.attackerTeam
        val npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "")

        npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false)
        npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false)
        npc.data().setPersistent(NPC.Metadata.ALWAYS_USE_NAME_HOLOGRAM, true)

        npc.data().setPersistent("arena", uuid.toString())

        val visualToUse = attackerTeam.skins.random()

        npc.getOrAddTrait(SkinTrait::class.java)?.apply {
            setSkinPersistent(
                attackerTeam.id,
                visualToUse.skin.signature,
                visualToUse.skin.value
            )
        }

        npc.getOrAddTrait(ScoreboardTrait::class.java).color = ChatColor.RED

        npc.getOrAddTrait(Equipment::class.java)
            .set(Equipment.EquipmentSlot.HELMET, visualToUse.displayItem.buildItem(Locale.ENGLISH))
        npc.getOrAddTrait(CurrentLocation::class.java)

        npc.getOrAddTrait(LookClose::class.java).apply { lookClose(true) }

        npc.getOrAddTrait(HologramTrait::class.java).apply {
            lineHeight = -0.28

            addLine(
                LegacyComponentSerializer.legacySection().serialize(
                    miniPhrase.translate("arena.npc.name.${attackerTeam.id}", Locale.ENGLISH)
                )
            )

            addLine(
                LegacyComponentSerializer.legacySection().serialize(
                    miniPhrase.translate("arena.npc.action.${attackerTeam.id}", Locale.ENGLISH)
                )
            )

            setMargin(0, "bottom", 0.65)
        }

        npc.spawn(mapPoint.toLocation(world))

        return npc
    }

    /**
     * Updates [player]'s scoreboard to the one that
     * is being used in the current [state].
     */
    private fun setCurrentBoard(player: Player) {
        scoreboardManager.getTemplate(state.name.lowercase()).printBoard(player)
    }

    /**
     * Updates the tags [tags] in every player's board.
     */
    public fun updateBoard(vararg tags: String) {
        arenaPlayers().mapNotNull { it.player() }.forEach { it.updateBoardTags(*tags) }
    }

    /**
     * Returns a list of the participants in this arena.
     */
    public fun teamPlayers(): List<UUID> {
        return playersPerTeam.values.flatten()
    }

    /**
     * Returns a list of every player in this arena, playing
     * or not playing.
     */
    public fun arenaPlayers(): List<UUID> {
        return teamPlayers().plus(spectators)
    }

    /**
     * Adds [player] to [team].
     */
    private fun addPlayerTo(player: Player, team: MapTeam) {
        addPlayerTo(player.uniqueId, team)
    }

    /**
     * Adds [uuid] to [team].
     */
    private fun addPlayerTo(uuid: UUID, team: MapTeam) {
        playersPerTeam.computeIfAbsent(team) { mutableListOf() }.add(uuid)
        uuid.playerData().currentTeam = team
    }

    /**
     * Removes [player] from [team].
     */
    private fun removePlayerFrom(player: Player, team: MapTeam) {
        removePlayerFrom(player.uniqueId, team)
    }

    /**
     * Removes [uuid] from [team].
     */
    private fun removePlayerFrom(uuid: UUID, team: MapTeam) {
        playersPerTeam[team]?.remove(uuid)
        uuid.playerData().currentTeam = null
    }

    /**
     * Creates an ItemStack that represents
     * the arena in [locale] language.
     */
    context(MiniPhraseContext)
    public fun getCurrentIcon(locale: Locale): ItemStack = TranslatableItem(
        material = state.material,
        name = "menu.arenas.arenaIcon.name",
        lore = "menu.arenas.arenaIcon.lore"
    ).buildItem(locale) {
        unparsed("display_name", displayName)
        inserting("arena_state", state.getFriendlyName(locale))
        unparsed("map_name", arenaMap.mapName)
        unparsed("team_size", (arenaMap.maxPlayers / 2).toString())
        unparsed("current_players", teamPlayers().size.toString())
        unparsed("max_players", arenaMap.maxPlayers.toString())
        unparsed("arena_short_uuid", uuid.toString().split("-").first())
    }

}