package me.hugo.thankmas.savethekweebecs.extension

import dev.kezz.miniphrase.MiniPhraseContext
import dev.kezz.miniphrase.audience.sendTranslated
import dev.kezz.miniphrase.tag.TagResolverBuilder
import me.hugo.thankmas.player.showTitle
import me.hugo.thankmas.savethekweebecs.game.arena.Arena
import me.hugo.thankmas.savethekweebecs.game.arena.ArenaState
import me.hugo.thankmas.savethekweebecs.game.map.MapRegistry
import me.hugo.thankmas.savethekweebecs.music.SoundManager
import me.hugo.thankmas.savethekweebecs.team.TeamManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.RenderType
import org.bukkit.scoreboard.Team
import org.koin.java.KoinJavaComponent
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val mapRegistry: MapRegistry by KoinJavaComponent.inject(MapRegistry::class.java)
private val soundManager: SoundManager by KoinJavaComponent.inject(SoundManager::class.java)

context(MiniPhraseContext)
public fun Arena.start() {
    if (this.teamPlayers().size < arenaMap.minPlayers) {
        arenaTime = arenaMap.defaultCountdown
        arenaState = ArenaState.WAITING

        announceTranslation("arena.notEnoughPeople")
        return
    }

    arenaTime = 300
    arenaState = ArenaState.IN_GAME

    playersPerTeam.forEach { (team, players) ->
        var spawnPointIndex = 0
        val spawnPoints = arenaMap.getSpawnpoints(team.id)

        players.mapNotNull { it.player() }.forEach { teamPlayer ->
            teamPlayer.reset(GameMode.SURVIVAL)

            teamPlayer.teleport(spawnPoints[spawnPointIndex].toLocation(world))

            teamPlayer.sendTranslated("arena.start.${team.id}") {

                Placeholder.unparsed("team_icon", team.chatIcon)
            }

            teamPlayer.playSound(Sound.ENTITY_ENDERMAN_TELEPORT)
            teamPlayer.showTitle(
                "arena.start.title",
                Title.Times.times(
                    0.5.seconds.toJavaDuration(),
                    2.0.seconds.toJavaDuration(),
                    0.5.seconds.toJavaDuration()
                )
            )

            team.giveItems(teamPlayer)

            val playerData = teamPlayer.playerData()

            val selectedVisual = playerData.selectedTeamVisuals[team] ?: team.defaultPlayerVisual

            teamPlayer.inventory.helmet = selectedVisual.craftHead(teamPlayer)
            loadTeamColors(teamPlayer)

            playerData.setSkin(selectedVisual.skin)

            soundManager.playTrack(SoundManager.IN_GAME_MUSIC, teamPlayer)

            spawnPointIndex++

            if (spawnPointIndex >= spawnPoints.size) {
                spawnPointIndex = 0
            }
        }
    }
}

public fun Arena.end(winnerTeam: TeamManager.Team) {
    this.winnerTeam = winnerTeam
    this.arenaTime = 10
    this.arenaState = ArenaState.FINISHING

    // Remove any ender pearls to avoid any delayed teleports.
    this.world.entities.filterIsInstance<Projectile>().forEach { it.remove() }

    playersPerTeam.forEach { (_, players) ->
        players.mapNotNull { it.player() }.forEach { teamPlayer ->
            teamPlayer.reset(GameMode.ADVENTURE)

            soundManager.stopTrack(teamPlayer)
            soundManager.playSoundEffect("save_the_kweebecs.victory", teamPlayer)

            teamPlayer.showTitle(
                if (winnerTeam == teamPlayer.playerData()?.currentTeam) "arena.win.title"
                else "arena.lost.title",
                Title.Times.times(
                    0.2.seconds.toJavaDuration(),
                    3.5.seconds.toJavaDuration(),
                    0.2.seconds.toJavaDuration()
                )
            )

            teamPlayer.playerData().resetSkin()
        }
    }

    announceTranslation("arena.win.${winnerTeam.id}") {
        Placeholder.unparsed("team_icon", winnerTeam.chatIcon)
    }
}

public fun Arena.reset() {
    arenaState = ArenaState.RESETTING

    arenaPlayers().mapNotNull { it.player() }.forEach { player ->
        player.playerData().apply {
            currentArena = null
            currentTeam = null
        }
        mapRegistry.sendToHub(player)
    }

    createWorld(false)
}

public fun Arena.loadTeamColors(player: Player) {
    val scoreboard = player.scoreboard

    val playerTeam = player.playerData().currentTeam
    val attackerTeam = arenaMap.attackerTeam
    val defenderTeam = arenaMap.defenderTeam

    val hisTeam = if (attackerTeam == playerTeam) attackerTeam else defenderTeam
    val otherTeam = if (attackerTeam != playerTeam) attackerTeam else defenderTeam

    val ownTeam: Team = (scoreboard.getTeam("own") ?: scoreboard.registerNewTeam("own")).apply {
        color(NamedTextColor.GREEN)
        suffix(Component.text(" ${hisTeam.teamIcon}").color(NamedTextColor.WHITE))
    }

    val enemyTeam: Team = (scoreboard.getTeam("enemy") ?: scoreboard.registerNewTeam("enemy")).apply {
        color(NamedTextColor.RED)
        suffix(Component.text(" ${otherTeam.teamIcon}").color(NamedTextColor.WHITE))
    }

    val healthObjective = scoreboard.getObjective("showHealth") ?: scoreboard.registerNewObjective(
        "showHealth",
        Criteria.HEALTH,
        Component.text("❤", NamedTextColor.RED),
        RenderType.INTEGER
    )

    healthObjective.displaySlot = DisplaySlot.BELOW_NAME

    playersPerTeam.forEach { (team, players) ->
        val isOwnTeam = team == player.playerData()?.currentTeam

        players.mapNotNull { it.player() }.forEach { player ->
            val playerName = player.name

            if (isOwnTeam) ownTeam.addEntry(playerName)
            else enemyTeam.addEntry(playerName)

            healthObjective.getScore(player).score = 20
        }
    }
}

public fun Arena.announce(message: Component) {
    arenaPlayers().forEach {
        it.player()?.sendMessage(message)
    }
}

context(MiniPhraseContext)
public fun Arena.announceTranslation(key: String, tags: (TagResolverBuilder.() -> Unit)? = null) {
    arenaPlayers().forEach {
        val player = it.player() ?: return@forEach
        player.sendTranslated(key, player.locale(), tags)
    }
}

public fun Arena.playSound(sound: Sound) {
    arenaPlayers().forEach {
        it.player()?.let { player ->
            player.playSound(player.location, sound, 1.0f, 1.0f)
        }
    }
}

public fun Arena.hasStarted(): Boolean {
    return this.arenaState != ArenaState.WAITING && this.arenaState != ArenaState.STARTING
}

public fun Arena.isInGame(): Boolean {
    return this.arenaState == ArenaState.IN_GAME
}