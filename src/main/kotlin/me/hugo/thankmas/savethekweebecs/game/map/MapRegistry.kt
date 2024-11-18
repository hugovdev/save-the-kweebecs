package me.hugo.thankmas.savethekweebecs.game.map

import me.hugo.thankmas.config.ConfigurationProvider
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.registry.MapBasedRegistry
import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.extension.playerData
import me.hugo.thankmas.savethekweebecs.extension.reset
import me.hugo.thankmas.savethekweebecs.music.SoundManager
import me.hugo.thankmas.savethekweebecs.scoreboard.KweebecScoreboardManager
import me.hugo.thankmas.savethekweebecs.task.GameControllerTask
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Registry of all the maps available for this STK. */
@Single
public class MapRegistry : MapBasedRegistry<String, ArenaMap>(), KoinComponent {

    private val main: SaveTheKweebecs = SaveTheKweebecs.instance()

    private val itemManager: ItemSetRegistry by inject()
    private val soundManager: SoundManager by inject()
    private val scoreboardManager: KweebecScoreboardManager by inject()

    private val configProvider: ConfigurationProvider by inject()

    /** The main hub location. */
    public val hubLocation: Location? = null

    init {
        val config = configProvider.getOrLoad("save_the_kweebecs/maps.yml")

        config.getKeys(false).forEach { register(it, ArenaMap(config, it)) }

        GameControllerTask().runTaskTimer(main, 0L, 20L)
    }

    /**
     * Sends [player] to hub removing their scoreboard entries,
     * teleporting them, resetting their inventory, stats and
     * sounds and giving them the configurable "lobby" ItemSet.
     */
    public fun sendToHub(player: Player) {
        if (!player.isOnline) return

        removeScoreboardEntries(player)

        hubLocation?.let { player.teleport(it) }
        player.reset(GameMode.ADVENTURE)

        player.playerData().apply {
            kills = 0
            deaths = 0
            resetCoins()
        }

        soundManager.stopTrack(player)

        scoreboardManager.getTemplate("lobby").printBoard(player)
        itemManager.giveSet("lobby", player)
    }

    /**
     * Removes the scoreboard teams used when playing
     * a game of Save The Kweebecs.
     */
    private fun removeScoreboardEntries(player: Player) {
        val scoreboard = player.scoreboard

        scoreboard.getTeam("own")?.let { team ->
            team.removeEntries(team.entries)
            team.unregister()
        }

        scoreboard.getTeam("enemy")?.let { team ->
            team.removeEntries(team.entries)
            team.unregister()
        }

        scoreboard.clearSlot(DisplaySlot.BELOW_NAME)
    }
}