package me.hugo.thankmas.savethekweebecs.game.map

import me.hugo.thankmas.config.ConfigurationProvider
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.listener.PlayerSpawnpointOnJoin
import me.hugo.thankmas.music.MusicManager
import me.hugo.thankmas.player.reset
import me.hugo.thankmas.registry.AutoCompletableMapRegistry
import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.extension.playerData
import me.hugo.thankmas.savethekweebecs.scoreboard.KweebecScoreboardManager
import me.hugo.thankmas.savethekweebecs.task.GameControllerTask
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Registry of all the maps available for this STK. */
@Single
public class MapRegistry : AutoCompletableMapRegistry<ArenaMap>(ArenaMap::class.java), KoinComponent {

    private val main: SaveTheKweebecs = SaveTheKweebecs.instance()

    private val itemManager: ItemSetRegistry by inject()
    private val musicManager: MusicManager by inject()
    private val scoreboardManager: KweebecScoreboardManager by inject()

    private val configProvider: ConfigurationProvider by inject()
    private val spawnpointOnJoin: PlayerSpawnpointOnJoin by inject()

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