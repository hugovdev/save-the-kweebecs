package me.hugo.thankmas.savethekweebecs.player

import com.destroystokyo.paper.profile.ProfileProperty
import dev.kezz.miniphrase.audience.sendTranslated
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.player.player
import me.hugo.thankmas.player.rank.RankedPlayerData
import me.hugo.thankmas.player.translate
import me.hugo.thankmas.player.updateBoardTags
import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.extension.arena
import me.hugo.thankmas.savethekweebecs.game.arena.Arena
import me.hugo.thankmas.savethekweebecs.game.map.MapRegistry
import me.hugo.thankmas.savethekweebecs.music.SoundManager
import me.hugo.thankmas.savethekweebecs.scoreboard.KweebecScoreboardManager
import me.hugo.thankmas.savethekweebecs.team.MapTeam
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.koin.core.component.inject
import java.util.*

/**
 * A class containing all the current stats and data for [playerUUID].
 */
public class SaveTheKweebecsPlayerData(playerUUID: UUID, instance: SaveTheKweebecs) :
    RankedPlayerData<SaveTheKweebecsPlayerData>(playerUUID, instance.playerDataManager), TranslatedComponent {

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
            playerUUID.player()?.updateBoardTags("kills")
        }

    public var deaths: Int = 0
        set(value) {
            field = value
            playerUUID.player()?.updateBoardTags("deaths")
        }

    private var coins: Int = 0

    public fun getCoins(): Int {
        return coins
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

    override fun onSave() {
        super.onSave()

        val player = onlinePlayerOrNull ?: return

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