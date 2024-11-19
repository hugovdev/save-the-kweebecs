package me.hugo.thankmas.savethekweebecs.player

import com.destroystokyo.paper.profile.ProfileProperty
import dev.kezz.miniphrase.audience.sendTranslated
import me.hugo.thankmas.ThankmasPlugin
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.player.player
import me.hugo.thankmas.player.rank.RankedPlayerData
import me.hugo.thankmas.player.translate
import me.hugo.thankmas.player.updateBoardTags
import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.extension.arena
import me.hugo.thankmas.savethekweebecs.extension.hasStarted
import me.hugo.thankmas.savethekweebecs.extension.playerData
import me.hugo.thankmas.savethekweebecs.game.arena.Arena
import me.hugo.thankmas.savethekweebecs.game.map.MapRegistry
import me.hugo.thankmas.savethekweebecs.music.SoundManager
import me.hugo.thankmas.savethekweebecs.scoreboard.KweebecScoreboardManager
import me.hugo.thankmas.savethekweebecs.team.MapTeam
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.koin.core.component.inject
import java.util.*

/**
 * A class containing all the current stats and data for [playerUUID].
 */
public class SaveTheKweebecsPlayerData(playerUUID: UUID, instance: SaveTheKweebecs) :
    RankedPlayerData<SaveTheKweebecsPlayerData>(playerUUID, instance.playerDataManager, false), TranslatedComponent {

    private val soundManager: SoundManager by inject()
    private val mapRegistry: MapRegistry by inject()
    private val scoreboardManager: KweebecScoreboardManager by inject()

    public var currentArena: Arena? = null
    public var currentTeam: MapTeam? = null
    public var lastAttack: PlayerAttack? = null

    private var playerSkin: ProfileProperty? = null

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

    public fun resetCoins() {
        coins = 0
    }

    override fun initializeBoard(title: String?, locale: Locale?, player: Player?): Player {
        val finalPlayer = super.initializeBoard(title, locale, player)

        // Setup player nametags to show their rank!
        playerNameTag = PlayerNameTag(
            playerUUID,
            {
                // Order players by rank weight if not in an arena, by team id if inside an arena!
                val rankIndex = 99 - (getPrimaryGroupOrNull()?.weight?.orElse(0) ?: 0)
                "${currentTeam?.id ?: rankIndex}-$playerUUID"
            },
            { viewer, preferredLocale ->
                val currentArena = currentArena

                // If the player isn't in an arena or isn't sharing the arena with
                // the viewer, then we render the normal rank color!
                if (currentArena == null || !currentArena.hasStarted() || viewer.arena() != currentArena) {
                    return@PlayerNameTag getTagColor(preferredLocale)
                }

                // Red for enemies, green for teammates!
                if (viewer.playerData().currentTeam == currentTeam) {
                    NamedTextColor.GREEN
                } else NamedTextColor.RED
            },
            { viewer, preferredLocale ->
                val currentArena = currentArena

                // If the player isn't in an arena or isn't sharing the arena with
                // the viewer, then we render the normal rank prefix!
                if (currentArena == null || !currentArena.hasStarted() || viewer.arena() != currentArena) {
                    return@PlayerNameTag getRankPrefix(preferredLocale)
                } else Component.empty()
            },
            suffixSupplier,
            belowNameSupplier
        )

        return finalPlayer
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
            if (arena != null) arena.arenaState.itemSetKey else "lobby",
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

    override fun onSave(player: Player) {
        super.onSave(player)

        currentArena?.leave(player, true)
        soundManager.stopTrack(player)
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