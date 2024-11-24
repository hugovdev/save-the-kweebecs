package me.hugo.thankmas.savethekweebecs.game.map

import com.infernalsuite.aswm.api.exceptions.SlimeException
import com.infernalsuite.aswm.api.world.SlimeWorld
import me.hugo.thankmas.config.string
import me.hugo.thankmas.location.MapPoint
import me.hugo.thankmas.markers.registry.MarkerRegistry
import me.hugo.thankmas.region.WeakRegion
import me.hugo.thankmas.region.types.MushroomJumpPad
import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.game.arena.Arena
import me.hugo.thankmas.savethekweebecs.game.arena.ArenaRegistry
import me.hugo.thankmas.savethekweebecs.game.events.ArenaEvent
import me.hugo.thankmas.savethekweebecs.team.MapTeam
import me.hugo.thankmas.savethekweebecs.team.TeamRegistry
import me.hugo.thankmas.world.SlimeWorldRegistry
import me.hugo.thankmas.world.s3.S3WorldSynchronizer
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Represents a Save The Kweebecs map.
 * All the data related to a map is saved here:
 * - Locations
 * - Attacker and defender teams.
 * - Minimum and Maximum players.
 * - Event timings and default countdowns.
 */
public class ArenaMap(mapsConfig: FileConfiguration, private val configName: String) : KoinComponent {

    private val arenaRegistry: ArenaRegistry by inject()
    private val s3WorldSynchronizer: S3WorldSynchronizer by inject()
    private val slimeWorldRegistry: SlimeWorldRegistry by inject()

    private val teamRegistry: TeamRegistry by inject()
    private val markerRegistry: MarkerRegistry by inject()

    /** SlimeWorld to be cloned by every Arena. */
    public val slimeWorld: SlimeWorld
        get() = slimeWorldRegistry.get(slimeFileName)

    private val slimeFileName = mapsConfig.string("$configName.slime-world")

    /** Name used to identify the map internally. */
    public var mapName: String = mapsConfig.getString("$configName.map-name") ?: configName

    /** Team that defends NPCs. */
    public var defenderTeam: MapTeam =
        teamRegistry.getOrNull(mapsConfig.string("$configName.defender-team")) ?: teamRegistry.get("trork")

    /** Team that has to save every NPC. */
    public var attackerTeam: MapTeam =
        teamRegistry.getOrNull(mapsConfig.string("$configName.attacker-team")) ?: teamRegistry.get("kweebec")

    /** List of events and the time before they occur. */
    public var events: MutableList<Pair<ArenaEvent, Int>> =
        mapsConfig.getStringList("$configName.events").mapNotNull { ArenaEvent.deserialize(it) }.toMutableList()

    /** The minimum players required by this map to play. */
    public val minPlayers: Int = mapsConfig.getInt("$configName.min-players", 6)

    /** The maximum amount of players this map can hold. */
    public val maxPlayers: Int = mapsConfig.getInt("$configName.max-players", 12)

    /** Time of day this map is played in. */
    public val time: Long = mapsConfig.getLong("$configName.time", 0L)

    /** Where the countdown starts at when the game is starting. */
    public val defaultCountdown: Int = mapsConfig.getInt("$configName.default-countdown", 60)

    /** Whether a game in this map should become available when loaded. */
    public val isAvailable: Boolean = mapsConfig.getBoolean("$configName.available", true)

    /** Locations for the waiting lobby and spectator spawnpoint. */
    public lateinit var lobbySpawnpoint: MapPoint
        private set
    public lateinit var spectatorSpawnpoint: MapPoint
        private set

    /** List of spawnpoints where attackers spawn. */
    private lateinit var attackerSpawnpoints: List<MapPoint>

    /** List of spawnpoints where attackers spawn. */
    private lateinit var defenderSpawnpoints: List<MapPoint>

    /** List of spawnpoints where kidnapped NPCs spawn. */
    public lateinit var kidnappedSpawnpoints: List<MapPoint>
        private set

    public lateinit var weakRegions: List<WeakRegion>
        private set

    init {
        val main = SaveTheKweebecs.instance()

        main.logger.info("Loading map $configName...")

        Bukkit.getScheduler().runTaskAsynchronously(SaveTheKweebecs.instance(), Runnable {
            s3WorldSynchronizer.downloadSlimeFile(
                "save_the_kweebecs/$slimeFileName",
                slimeWorldRegistry.slimeWorldContainer.resolve("$slimeFileName.slime")
            )

            // Load the markers and the slime world.
            try {
                markerRegistry.loadSlimeWorldMarkers(slimeFileName)
            } catch (e: SlimeException) {
                main.logger.info("There was a problem trying to load the world: $slimeFileName")
                e.printStackTrace()
            }

            main.logger.info("Slime map $slimeFileName was loaded successfully!")
            main.logger.info("Fetching marker data...")

            attackerSpawnpoints =
                markerRegistry.getMarkerForType("attacker_spawnpoint", slimeFileName).map { it.location }
            defenderSpawnpoints =
                markerRegistry.getMarkerForType("defender_spawnpoint", slimeFileName).map { it.location }
            kidnappedSpawnpoints =
                markerRegistry.getMarkerForType("kidnapped_spawnpoint", slimeFileName).map { it.location }

            lobbySpawnpoint = markerRegistry.getMarkerForType("lobby_spawnpoint", slimeFileName).first().location
            spectatorSpawnpoint =
                markerRegistry.getMarkerForType("spectator_spawnpoint", slimeFileName).first().location

            weakRegions = markerRegistry.getMarkerForType("jump_pad", slimeFileName).map { MushroomJumpPad(it) }

            main.logger.info("Map ${this.configName} has been loaded correctly and is now valid!")

            if (isAvailable) {
                // Register a game for the arena by default!
                Bukkit.getScheduler().runTask(SaveTheKweebecs.instance(), Runnable {
                    val arena = Arena(this, mapName)
                    arenaRegistry.register(arena.arenaUUID, arena)

                    main.logger.info("Game in map ${this.configName} has been created and made available!")
                })
            }
        })
    }

    public fun getSpawnpoints(teamId: String): List<MapPoint> = if (attackerTeam.id == teamId) attackerSpawnpoints
    else defenderSpawnpoints

}