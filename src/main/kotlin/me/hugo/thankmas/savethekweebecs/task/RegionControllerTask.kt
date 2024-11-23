package me.hugo.thankmas.savethekweebecs.task

import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.player.player
import me.hugo.thankmas.savethekweebecs.extension.playerData
import me.hugo.thankmas.savethekweebecs.game.arena.ArenaRegistry
import org.bukkit.scheduler.BukkitRunnable
import org.koin.core.component.inject

public class RegionControllerTask : TranslatedComponent, BukkitRunnable() {

    private val arenaRegistry: ArenaRegistry by inject()

    override fun run() {
        arenaRegistry.getValues().forEach { arena ->
            arena.arenaPlayers().forEach players@{ player ->
                arena.arenaMap.weakRegions.forEach { region ->
                    val onlinePlayer = player.player() ?: return@players

                    val playerData = onlinePlayer.playerData()

                    if (onlinePlayer.location in region) playerData.updateOnRegion(region)
                    else playerData.leaveRegion(region)
                }
            }
        }
    }

}