package me.hugo.thankmas.savethekweebecs.game.map

import live.minehub.polarpaper.PolarWorld
import me.hugo.thankmas.ThankmasPlugin
import me.hugo.thankmas.config.string
import me.hugo.thankmas.items.TranslatableItem
import me.hugo.thankmas.location.MapPoint
import me.hugo.thankmas.region.WeakRegion
import me.hugo.thankmas.region.types.MushroomJumpPad
import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.game.arena.Arena
import me.hugo.thankmas.savethekweebecs.game.arena.ArenaRegistry
import me.hugo.thankmas.savethekweebecs.game.events.ArenaEvent
import me.hugo.thankmas.savethekweebecs.team.MapTeam
import me.hugo.thankmas.savethekweebecs.team.TeamRegistry
import me.hugo.thankmas.world.registry.PolarWorldRegistry
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

    private val mapRegistry: MapRegistry by inject()
    private val arenaRegistry: ArenaRegistry by inject()
    private val s3WorldSynchronizer: S3WorldSynchronizer by inject()
    private val polarWorldRegistry: PolarWorldRegistry by inject()

    private val teamRegistry: TeamRegistry by inject()

    /** SlimeWorld to be cloned by every Arena. */
    public val polarWorld: PolarWorld
        get() = polarWorldRegistry.get(polarFileName)

    private val polarFileName = mapsConfig.string("$configName.polar-world")

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

    /** Item used for the map selector menu in STK. */
    public val mapSelectorIcon: TranslatableItem = TranslatableItem(mapsConfig, "$configName.selector-icon")

    /** Where in the map selector menu does this map go. */
    public val mapSelectorSlot: Int = mapsConfig.getInt("$configName.selector-slot", -1)

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
        val main = ThankmasPlugin.instance<SaveTheKweebecs>()

        main.logger.info("Loading map $configName...")

        Bukkit.getScheduler().runTaskAsynchronously(ThankmasPlugin.instance<SaveTheKweebecs>(), Runnable {
            if (!polarWorldRegistry.polarWorldContainer.exists()) polarWorldRegistry.polarWorldContainer.mkdirs()
            s3WorldSynchronizer.downloadFile(
                "save_the_kweebecs/$polarFileName",
                "polar",
                polarWorldRegistry.polarWorldContainer.resolve("$polarFileName.polar")
            )

            // Load the markers and the polar world.
            try {
                polarWorldRegistry.getOrLoadWithMarkers(polarFileName)
            } catch (e: Exception) {
                main.logger.info("There was a problem trying to load the world: $polarFileName")
                e.printStackTrace()
            }

            main.logger.info("Polar map $polarFileName was loaded successfully!")
            main.logger.info("Fetching marker data...")

            attackerSpawnpoints =
                polarWorldRegistry.getMarkerForType(polarFileName, "attacker_spawnpoint").map { it.location }
            defenderSpawnpoints =
                polarWorldRegistry.getMarkerForType(polarFileName, "defender_spawnpoint").map { it.location }
            kidnappedSpawnpoints =
                polarWorldRegistry.getMarkerForType(polarFileName, "kidnapped_spawnpoint").map { it.location }

            lobbySpawnpoint = polarWorldRegistry.getMarkerForType(polarFileName, "lobby_spawnpoint").first().location
            spectatorSpawnpoint =
                polarWorldRegistry.getMarkerForType(polarFileName, "spectator_spawnpoint").first().location

            weakRegions = polarWorldRegistry.getMarkerForType(polarFileName, "jump_pad").map { MushroomJumpPad(it) }

            main.logger.info("Map ${this.configName} has been loaded correctly and is now valid!")

            if (isAvailable) {
                // Register a game for the arena by default!
                Bukkit.getScheduler().runTask(ThankmasPlugin.instance<SaveTheKweebecs>(), Runnable {
                    val arena = Arena(this, mapName)
                    arenaRegistry.register(arena.uuid, arena)

                    mapRegistry.addMenuIcon(arena)

                    main.logger.info("Game in map ${this.configName} has been created and made available!")
                })
            }
        })
    }

    public fun getSpawnpoints(teamId: String): List<MapPoint> = if (attackerTeam.id == teamId) attackerSpawnpoints
    else defenderSpawnpoints

}