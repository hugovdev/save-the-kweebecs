package me.hugo.thankmas.savethekweebecs

import me.hugo.thankmas.ThankmasPlugin
import me.hugo.thankmas.commands.TranslationsCommands
import me.hugo.thankmas.config.string
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.listener.PlayerDataLoader
import me.hugo.thankmas.listener.PlayerLocaleDetector
import me.hugo.thankmas.listener.PlayerSpawnpointOnJoin
import me.hugo.thankmas.markers.registry.MarkerRegistry
import me.hugo.thankmas.player.PlayerDataManager
import me.hugo.thankmas.player.rank.PlayerGroupChange
import me.hugo.thankmas.savethekweebecs.commands.LobbyCommand
import me.hugo.thankmas.savethekweebecs.commands.SaveTheKweebecsCommand
import me.hugo.thankmas.savethekweebecs.dependencyinjection.SaveTheKweebecsModules
import me.hugo.thankmas.savethekweebecs.extension.arena
import me.hugo.thankmas.savethekweebecs.game.map.MapRegistry
import me.hugo.thankmas.savethekweebecs.listeners.ArenaListener
import me.hugo.thankmas.savethekweebecs.music.SoundManager
import me.hugo.thankmas.savethekweebecs.player.SaveTheKweebecsPlayerData
import me.hugo.thankmas.savethekweebecs.team.TeamRegistry
import me.hugo.thankmas.scoreboard.ScoreboardTemplateManager
import org.bukkit.Bukkit
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.core.parameter.parametersOf
import org.koin.ksp.generated.module
import revxrsal.commands.bukkit.BukkitCommandHandler

public class SaveTheKweebecs : ThankmasPlugin<SaveTheKweebecsPlayerData>(listOf("save_the_kweebecs")) {

    private val mapRegistry: MapRegistry by inject()
    private val teamRegistry: TeamRegistry by inject()
    private val markerRegistry: MarkerRegistry by inject()

    private val spawnpointOnJoin: PlayerSpawnpointOnJoin by inject { parametersOf(hubWorldName, "lobby_spawnpoint") }

    private val soundManager: SoundManager by inject()

    override val scoreboardTemplateManager: ScoreboardTemplateManager<SaveTheKweebecsPlayerData> by inject {
        parametersOf(
            this
        )
    }
    override val playerDataManager: PlayerDataManager<SaveTheKweebecsPlayerData> =
        PlayerDataManager { SaveTheKweebecsPlayerData(it, this) }

    private val itemSetManager: ItemSetRegistry by inject { parametersOf(configProvider.getOrLoad("save_the_kweebecs/config.yml")) }

    private var hubWorldName: String = "world"

    private lateinit var commandHandler: BukkitCommandHandler

    public companion object {
        private lateinit var main: SaveTheKweebecs

        public fun instance(): SaveTheKweebecs {
            return main
        }
    }

    override fun onLoad() {
        super.onLoad()

        main = this
        loadKoinModules(SaveTheKweebecsModules().module)

        val scopeWorld = configProvider.getOrLoad("save_the_kweebecs/config.yml").string("hub-world")

        Bukkit.unloadWorld(hubWorldName, false)

        s3WorldSynchronizer.downloadWorld(
            scopeWorld,
            Bukkit.getWorldContainer().resolve(hubWorldName).also { it.mkdirs() })

        markerRegistry.loadWorldMarkers(hubWorldName)
    }

    override fun onEnable() {
        super.onEnable()

        logger.info("Starting Save The Kweebecs 2.0...")

        logger.info("Starting dependencies and injections!")
        loadKoinModules(SaveTheKweebecsModules().module)

        this.scoreboardTemplateManager.initialize()

        logger.info("Registering item sets...")
        logger.info("Registered ${itemSetManager.size()} item sets!")

        commandHandler = BukkitCommandHandler.create(this)

        teamRegistry.registerCompletions(commandHandler)
        mapRegistry.registerCompletions(commandHandler)

        commandHandler.register(TranslationsCommands(this.playerDataManager))
        commandHandler.register(SaveTheKweebecsCommand())
        commandHandler.register(LobbyCommand())

        commandHandler.registerBrigadier()

        val pluginManager = Bukkit.getPluginManager()

        pluginManager.registerEvents(ArenaListener(), this)

        // Player data loaders and spawnpoints
        pluginManager.registerEvents(PlayerDataLoader(this, this.playerDataManager), this)
        pluginManager.registerEvents(spawnpointOnJoin, this)

        pluginManager.registerEvents(PlayerLocaleDetector(this.playerDataManager), this)

        // Register luck perms events!
        PlayerGroupChange(this.playerDataManager, shouldUpdate = { player ->
            player.arena() == null
        })

        soundManager.runTaskTimer(instance(), 0L, 2L)

        Bukkit.getScoreboardManager().mainScoreboard.teams.forEach { it.unregister() }
        Bukkit.getScoreboardManager().mainScoreboard.objectives.forEach { it.unregister() }

        println("Starting Game Manager... Maps: ${mapRegistry.size()}")
    }

    override fun onDisable() {
        commandHandler.unregisterAllCommands()

        Bukkit.getScoreboardManager().mainScoreboard.teams.forEach { it.unregister() }
        Bukkit.getScoreboardManager().mainScoreboard.objectives.forEach { it.unregister() }
    }


}