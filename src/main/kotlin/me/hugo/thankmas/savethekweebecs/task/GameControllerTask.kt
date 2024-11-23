package me.hugo.thankmas.savethekweebecs.task

import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.listener.PlayerSpawnpointOnJoin
import me.hugo.thankmas.player.playSound
import me.hugo.thankmas.player.player
import me.hugo.thankmas.player.reset
import me.hugo.thankmas.player.showTitle
import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.extension.*
import me.hugo.thankmas.savethekweebecs.game.arena.Arena
import me.hugo.thankmas.savethekweebecs.game.arena.ArenaRegistry
import me.hugo.thankmas.savethekweebecs.game.arena.ArenaState
import me.hugo.thankmas.savethekweebecs.util.InstantFirework
import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable
import org.koin.core.component.inject
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Main Save The Kweebecs game loop.
 * Takes care of every event or game countdown.
 */
public class GameControllerTask : TranslatedComponent, BukkitRunnable() {

    private val spawnpointOnJoin: PlayerSpawnpointOnJoin by inject()
    private val arenaRegistry: ArenaRegistry by inject()
    private val teleporting: MutableSet<UUID> = mutableSetOf()

    override fun run() {
        SaveTheKweebecs.instance().playerDataManager.getAllPlayerData()
            .asSequence().filter { it.currentArena == null }
            .mapNotNull { it.onlinePlayerOrNull }
            .filter {
                it.uniqueId !in teleporting && it.location.y <= 10
            }.forEach {
                teleporting += it.uniqueId

                it.teleportAsync(spawnpointOnJoin.spawnpoint).thenRun {
                    teleporting -= it.uniqueId
                }
            }

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
                        unparsed("count", time.toString())
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

                    val selectedVisual = team.skins.random()
                    player.inventory.helmet = selectedVisual.displayItem.buildItem(player)

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
                        unparsed("respawn_time", newTime.toString())
                    }

                    player.playSound(Sound.BLOCK_NOTE_BLOCK_HAT)
                }
            }
        }
    }

}