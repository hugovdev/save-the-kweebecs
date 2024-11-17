package me.hugo.thankmas.savethekweebecs.listeners

import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.extension.arena
import me.hugo.thankmas.savethekweebecs.extension.playerData
import me.hugo.thankmas.savethekweebecs.extension.updateBoardTags
import me.hugo.thankmas.savethekweebecs.game.map.MapRegistry
import me.hugo.thankmas.savethekweebecs.music.SoundManager
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Single
public class JoinLeaveListener : KoinComponent, Listener {

    private val playerManager
        get() = SaveTheKweebecs.instance().playerManager

    private val mapRegistry: MapRegistry by inject()
    private val soundManager: SoundManager by inject()

    public var onlinePlayers: Int = Bukkit.getOnlinePlayers().size

    @EventHandler
    public fun onPlayerJoin(event: PlayerJoinEvent) {
        event.joinMessage(null)
        val player = event.player

        // Don't save any player data on worlds, etc.
        player.isPersistent = false

        mapRegistry.sendToHub(player)
        onlinePlayers++

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

    @EventHandler
    public fun onPlayerQuit(event: PlayerQuitEvent) {
        event.quitMessage(null)
        val player = event.player

        player.playerData()?.currentArena?.leave(player, true)
        playerManager.removePlayerData(player.uniqueId)
        onlinePlayers--

        Bukkit.getOnlinePlayers().filter { it != player }.filter { it.arena() == null }
            .forEach { it.updateBoardTags("all_players") }

        soundManager.stopTrack(player)
    }
}