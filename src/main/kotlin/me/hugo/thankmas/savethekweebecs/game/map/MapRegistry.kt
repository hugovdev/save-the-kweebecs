package me.hugo.thankmas.savethekweebecs.game.map

import me.hugo.thankmas.ThankmasPlugin
import me.hugo.thankmas.config.ConfigurationProvider
import me.hugo.thankmas.gui.Icon
import me.hugo.thankmas.gui.Menu
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.items.loreTranslatable
import me.hugo.thankmas.items.name
import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.listener.PlayerSpawnpointOnJoin
import me.hugo.thankmas.music.MusicManager
import me.hugo.thankmas.player.reset
import me.hugo.thankmas.registry.AutoCompletableMapRegistry
import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.extension.playerData
import me.hugo.thankmas.savethekweebecs.game.arena.Arena
import me.hugo.thankmas.savethekweebecs.game.arena.ArenaRegistry
import me.hugo.thankmas.savethekweebecs.scoreboard.KweebecScoreboardManager
import me.hugo.thankmas.savethekweebecs.task.GameControllerTask
import net.kyori.adventure.text.Component
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.koin.core.annotation.Single
import org.koin.core.component.inject

/** Registry of all the maps available for this STK. */
@Single
public class MapRegistry : AutoCompletableMapRegistry<ArenaMap>(ArenaMap::class.java), TranslatedComponent {

    private val main: SaveTheKweebecs = ThankmasPlugin.instance<ThankmasPlugin<*>>()

    private val itemManager: ItemSetRegistry by inject()
    private val musicManager: MusicManager by inject()
    private val scoreboardManager: KweebecScoreboardManager by inject()
    private val arenaRegistry: ArenaRegistry by inject()

    private val configProvider: ConfigurationProvider by inject()
    private val spawnpointOnJoin: PlayerSpawnpointOnJoin by inject()

    private val arenaMenu = Menu("menu.arenas.title", 54, mutableMapOf(), null, miniPhrase)

    init {
        val config = configProvider.getOrLoad("save_the_kweebecs/maps.yml")

        config.getKeys(false).forEach {
            register(it, ArenaMap(config, it))
        }

        GameControllerTask().runTaskTimer(main, 0L, 20L)
    }

    /** Adds an icon for [arena] in the [arenaMenu]. */
    public fun addMenuIcon(arena: Arena) {
        val map = arena.arenaMap

        if (map.mapSelectorSlot != -1) {
            arenaMenu.setIcon(map.mapSelectorSlot, Icon({ context, _ -> arena.joinArena(context.clicker) }) {
                val availableArena: Arena = arenaRegistry.getValues().first { it.arenaMap == map }

                map.mapSelectorIcon.buildItem(it.locale())
                    .name(Component.text(availableArena.displayName, availableArena.state.color))
                    .loreTranslatable("menu.arenas.arenaIcon.lore", it.locale()) {
                        unparsed("display_name", availableArena.displayName)
                        inserting("arena_state", availableArena.state.getFriendlyName(it.locale()))
                        unparsed("map_name", map.mapName)
                        unparsed("team_size", (map.maxPlayers / 2).toString())
                        unparsed("current_players", availableArena.teamPlayers().size.toString())
                        unparsed("max_players", map.maxPlayers.toString())
                        unparsed("arena_short_uuid", availableArena.uuid.toString().split("-").first())
                    }
            }.listen { arena.iconToken })
        }
    }

    /** Opens the arena menu to [player]. */
    public fun openArenasMenu(player: Player) {
        arenaMenu.open(player)
    }

    /**
     * Sends [player] to hub removing their scoreboard entries,
     * teleporting them, resetting their inventory, stats and
     * sounds and giving them the configurable "lobby" ItemSet.
     */
    public fun sendToHub(player: Player, teleport: Boolean = true) {
        if (!player.isOnline) return

        if (teleport) player.teleport(spawnpointOnJoin.spawnpoint)
        player.reset(GameMode.ADVENTURE)

        player.playerData().apply {
            kills = 0
            deaths = 0
            resetCoins()
        }

        player.playerData().giveCosmetic()
        musicManager.stopTrack(player)

        scoreboardManager.getTemplate("lobby").printBoard(player)
        itemManager.giveSet("lobby", player)
    }
}