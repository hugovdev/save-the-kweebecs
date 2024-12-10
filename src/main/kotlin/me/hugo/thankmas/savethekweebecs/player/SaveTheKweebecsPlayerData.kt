package me.hugo.thankmas.savethekweebecs.player

import com.destroystokyo.paper.profile.ProfileProperty
import dev.kezz.miniphrase.audience.sendTranslated
import me.hugo.thankmas.database.PlayerData
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.music.MusicManager
import me.hugo.thankmas.player.cosmetics.CosmeticsPlayerData
import me.hugo.thankmas.player.player
import me.hugo.thankmas.player.translate
import me.hugo.thankmas.player.updateBoardTags
import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.extension.arena
import me.hugo.thankmas.savethekweebecs.extension.hasStarted
import me.hugo.thankmas.savethekweebecs.extension.playerData
import me.hugo.thankmas.savethekweebecs.game.arena.Arena
import me.hugo.thankmas.savethekweebecs.game.map.MapRegistry
import me.hugo.thankmas.savethekweebecs.scoreboard.KweebecScoreboardManager
import me.hugo.thankmas.savethekweebecs.team.MapTeam
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import org.koin.core.component.inject
import java.util.*

/**
 * A class containing all the current stats and data for [playerUUID].
 */
public class SaveTheKweebecsPlayerData(playerUUID: UUID, instance: SaveTheKweebecs) :
    CosmeticsPlayerData<SaveTheKweebecsPlayerData>(playerUUID, instance, doesUpdateCosmetic = {
        it.playerData().currentArena == null
    }), TranslatedComponent {

    private val musicManager: MusicManager by inject()
    private val mapRegistry: MapRegistry by inject()
    private val scoreboardManager: KweebecScoreboardManager by inject()

    public var currentArena: Arena? = null
    public var currentTeam: MapTeam? = null
    public var lastAttack: PlayerAttack? = null

    private var playerSkin: ProfileProperty? = null

    /** Special team id implementation to keep teams in-game in mind. */
    override val teamIdSupplier: () -> String = {
        // Order players by rank weight if not in an arena, by team id if inside an arena!
        val rankIndex = 99 - (getPrimaryGroupOrNull()?.weight?.orElse(0) ?: 0)
        "${currentTeam?.id ?: rankIndex}-$playerUUID"
    }

    /** Special player tag color implementation to keep teams in-game in mind. */
    override val namedTextColor: ((viewer: Player, preferredLocale: Locale?) -> NamedTextColor) =
        textColor@{ viewer, preferredLocale ->
            val currentArena = currentArena

            // If the player isn't in an arena or isn't sharing the arena with
            // the viewer, then we render the normal rank color!
            if (currentArena == null || !currentArena.hasStarted() || viewer.arena() != currentArena) {
                return@textColor getTagColor(preferredLocale)
            }

            // Red for enemies, green for teammates!
            return@textColor if (viewer.playerData().currentTeam == currentTeam) {
                NamedTextColor.GREEN
            } else NamedTextColor.RED
        }

    /** Special player tag prefix implementation to keep teams in-game in mind. */
    override val prefixSupplier: ((viewer: Player, preferredLocale: Locale?) -> Component) =
        prefix@{ viewer, preferredLocale ->
            val currentArena = currentArena

            // If the player isn't in an arena or isn't sharing the arena with
            // the viewer, then we render the normal rank prefix!
            return@prefix if (currentArena == null || !currentArena.hasStarted() || viewer.arena() != currentArena) {
                getRankPrefix(preferredLocale)
            } else Component.empty()
        }

    public var kills: Int = 0
        set(value) {
            field = value
            onlinePlayer.updateBoardTags("kills")
        }

    public var deaths: Int = 0
        set(value) {
            field = value
            onlinePlayer.updateBoardTags("deaths")
        }

    public var coins: Int = 0
        private set

    init {
        transaction {
            val playerData = PlayerData.selectAll().where { PlayerData.uuid eq playerUUID.toString() }.singleOrNull()

            loadCurrency(playerData)
            loadCosmetics(playerData)
        }
    }

    public fun resetCoins() {
        coins = 0
    }

    public fun addCoins(amount: Int, reason: String) {
        coins += amount

        val isNegative = amount < 0
        val displayedAmount = if (isNegative) amount * -1 else amount

        playerUUID.player()?.let {
            it.updateBoardTags("coins")
            it.sendTranslated(if (isNegative) "arena.gold.minus" else "arena.gold.plus") {
                unparsed("amount", displayedAmount.toString())
                inserting("reason", onlinePlayer.translate("arena.gold.reason.$reason"))
            }
        }
    }

    override fun setLocale(newLocale: Locale) {
        super.setLocale(newLocale)

        scoreboardManager.getTemplate(lastBoardId).printBoard(player = onlinePlayer, locale = newLocale)

        val itemSetManager: ItemSetRegistry by inject()

        val arena = currentArena
        itemSetManager.giveSetNullable(
            if (arena != null) arena.state.itemSetKey else "lobby",
            onlinePlayer,
            newLocale
        )
    }

    override fun onPrepared(player: Player) {
        super.onPrepared(player)

        player.isPersistent = false

        mapRegistry.sendToHub(player, false)

        playerSkin = player.playerProfile.properties.firstOrNull { it.name == "textures" }

        val instance = SaveTheKweebecs.instance()

        Bukkit.getServer().onlinePlayers.forEach {
            if (it.arena() == null) it.updateBoardTags("all_players")

            if (it.world === player.world) {
                it.showPlayer(instance, player)
                player.showPlayer(instance, it)
                return@forEach
            }

            it.hidePlayer(instance, player)
            player.hidePlayer(instance, it)
        }
    }

    protected override fun save() {
        val playerId = playerUUID.toString()

        transaction {
            // Update or insert this player's selected stuff!
            PlayerData.upsert {
                it[uuid] = playerId
                it[selectedCosmetic] = this@SaveTheKweebecsPlayerData.selectedCosmetic.value?.id ?: ""
                it[currency] = currency
            }
        }
    }

    override fun onSave(player: Player) {
        super.onSave(player)

        currentArena?.leave(player, true)
        musicManager.stopTrack(player)
    }

    public fun resetSkin() {
        val skin = playerSkin ?: return
        setSkin(skin)
    }

    public fun setSkin(skin: ProfileProperty) {
        val player = onlinePlayerOrNull ?: return

        player.playerProfile = player.playerProfile.apply {
            setProperty(skin)
        }
    }

    public data class PlayerAttack(val attacker: UUID, val time: Long = System.currentTimeMillis())
}