package me.hugo.thankmas.savethekweebecs.task

import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.player.showTitle
import me.hugo.thankmas.savethekweebecs.extension.*
import me.hugo.thankmas.savethekweebecs.game.arena.Arena
import me.hugo.thankmas.savethekweebecs.game.arena.ArenaRegistry
import me.hugo.thankmas.savethekweebecs.game.arena.ArenaState
import me.hugo.thankmas.savethekweebecs.util.InstantFirework
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Main Save The Kweebecs game loop.
 * Takes care of every event or game countdown.
 */
public class GameControllerTask : TranslatedComponent, BukkitRunnable() {

    private val arenaRegistry: ArenaRegistry by inject()

    override fun run() {
        //Bukkit.getOnlinePlayers().filter { it.playerData()?.currentArena == null && it.location.y <= 10 }
        //    .forEach { player ->
        //        arenaRegistry.hubLocation?.let { player.teleport(it) }
        //    }

        arenaRegistry.getValues().forEach { arena ->
            val arenaState = arena.arenaState
            if (arenaState == ArenaState.WAITING || arenaState == ArenaState.RESETTING) return@forEach

            arena.arenaTime--

            val time = arena.arenaTime

            if (time == 0) {
                when (arenaState) {
                    ArenaState.STARTING -> arena.start()
                    ArenaState.IN_GAME -> {
                        val event = arena.currentEvent

                        if (event != null) {
                            event.eventRun.invoke(this, arena)
                            arena.eventIndex++

                            arena.arenaMap.events.getOrNull(arena.eventIndex)?.second?.let { arena.arenaTime = it }
                            arena.updateBoard("next_event", "time")
                        } else arena.end(arena.arenaMap.defenderTeam)
                    }

                    ArenaState.FINISHING -> arena.reset()
                    else -> {}
                }
            } else {
                arena.updateBoard("next_event", if (arenaState == ArenaState.IN_GAME) "time" else "count")

                if (arenaState == ArenaState.FINISHING) {
                    arena.playersPerTeam[arena.winnerTeam]?.mapNotNull { it.player() }?.randomOrNull()?.let {
                        InstantFirework(
                            FireworkEffect.builder().withColor(Color.ORANGE, Color.YELLOW).trail(true)
                                .withFade(Color.RED, Color.ORANGE).build(), it.location
                        )
                    }
                }

                if (time <= 60 && (time % 10 == 0 || time <= 5)) {
                    val translationName = if (arenaState != ArenaState.IN_GAME) arenaState.name.lowercase()
                    else "event.${arena.currentEvent?.name?.lowercase()}"

                    arena.playSound(Sound.BLOCK_NOTE_BLOCK_HAT)
                    arena.announceTranslation(if (time == 1) "arena.$translationName.second" else "arena.$translationName.seconds") {
                        Placeholder.unparsed("count", time.toString())
                    }
                }

                respawnPlayers(arena)
            }
        }
    }

    /**
     * Goes through every dead player and updates their
     * respawning screen or respawns them if ready.
     */
    private fun respawnPlayers(arena: Arena) {
        if (arena.arenaState == ArenaState.IN_GAME) {
            arena.deadPlayers.forEach deadPlayers@{ (player, secondsLeft) ->
                val newTime = secondsLeft - 1
                arena.deadPlayers[player] = newTime

                val playerData = player.playerData()
                val team = playerData.currentTeam ?: return@deadPlayers

                if (newTime == 0) {
                    arena.deadPlayers.remove(player)

                    val respawnLocation = arena.arenaMap.getSpawnpoints(team.id).random().toLocation(arena.world)

                    player.teleport(respawnLocation)
                    player.reset(GameMode.SURVIVAL)
                    team.giveItems(player)

                    val selectedVisual =
                        playerData.selectedTeamVisuals[team] ?: team.defaultPlayerVisual
                    player.inventory.helmet = selectedVisual.craftHead(player)

                    player.showTitle(
                        "arena.respawned.title", Title.Times.times(
                            0.seconds.toJavaDuration(),
                            1.5.seconds.toJavaDuration(),
                            0.25.seconds.toJavaDuration()
                        )
                    )

                    player.playSound(Sound.ENTITY_ENDERMAN_TELEPORT)

                } else {
                    player.showTitle(
                        "arena.respawning.title", Title.Times.times(
                            0.seconds.toJavaDuration(),
                            1.0.seconds.toJavaDuration(),
                            0.25.seconds.toJavaDuration()
                        )
                    ) {
                        Placeholder.unparsed("respawn_time", newTime.toString())
                    }

                    player.playSound(Sound.BLOCK_NOTE_BLOCK_HAT)
                }
            }
        }
    }

}