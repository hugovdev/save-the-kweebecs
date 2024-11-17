package me.hugo.thankmas.savethekweebecs

import com.infernalsuite.aswm.api.SlimePlugin
import me.hugo.thankmas.ThankmasPlugin
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.listener.PlayerDataLoader
import me.hugo.thankmas.listener.PlayerLocaleDetector
import me.hugo.thankmas.listener.PlayerSpawnpointOnJoin
import me.hugo.thankmas.player.PlayerDataManager
import me.hugo.thankmas.savethekweebecs.commands.LobbyCommand
import me.hugo.thankmas.savethekweebecs.commands.SaveTheKweebecsCommand
import me.hugo.thankmas.savethekweebecs.dependencyinjection.SaveTheKweebecsModules
import me.hugo.thankmas.savethekweebecs.game.map.ArenaMap
import me.hugo.thankmas.savethekweebecs.game.map.MapRegistry
import me.hugo.thankmas.savethekweebecs.listeners.ArenaListener
import me.hugo.thankmas.savethekweebecs.listeners.JoinLeaveListener
import me.hugo.thankmas.savethekweebecs.music.SoundManager
import me.hugo.thankmas.savethekweebecs.player.SaveTheKweebecsPlayerData
import me.hugo.thankmas.savethekweebecs.scoreboard.KweebecScoreboardManager
import me.hugo.thankmas.savethekweebecs.team.TeamManager
import me.hugo.thankmas.savethekweebecs.text.TextPopUpManager
import org.bukkit.Bukkit
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.ksp.generated.module
import revxrsal.commands.autocomplete.SuggestionProvider
import revxrsal.commands.bukkit.BukkitCommandHandler
import revxrsal.commands.command.CommandActor
import revxrsal.commands.command.CommandParameter
import revxrsal.commands.exception.CommandErrorException

public class SaveTheKweebecs : ThankmasPlugin(listOf("save_the_kweebecs")) {

    private val mapRegistry: MapRegistry by inject()
    private val teamManager: TeamManager by inject()

    public val scoreboardManager: KweebecScoreboardManager by inject()

    private val soundManager: SoundManager by inject()
    private val textPopUp: TextPopUpManager by inject()

    private val itemSetManager: ItemSetRegistry by inject { parametersOf(configProvider.getOrLoad("save_the_kweebecs/config.yml")) }

    private val joinLeaveListener: JoinLeaveListener by inject()

    public val playerManager: PlayerDataManager<SaveTheKweebecsPlayerData> = PlayerDataManager { SaveTheKweebecsPlayerData(it, this) }

    private var hubWorldName: String = "world"

    private lateinit var commandHandler: BukkitCommandHandler
    public lateinit var slimePlugin: SlimePlugin

    public companion object {
        private lateinit var main: SaveTheKweebecs

        public fun instance(): SaveTheKweebecs {
            return main
        }
    }

    override fun onEnable() {
        main = this
        logger.info("Starting Save The Kweebecs 2.0...")

        logger.info("Starting dependencies and injections!")
        startKoin {
            modules(SaveTheKweebecsModules().module)
        }

        val pluginManager = Bukkit.getPluginManager()
        slimePlugin = pluginManager.getPlugin("SlimeWorldManager") as SlimePlugin

        saveDefaultConfig()

        scoreboardManager.initialize()

        logger.info("Registering item sets...")
        logger.info("Registered ${itemSetManager.size()} item sets!")

        commandHandler = BukkitCommandHandler.create(this)

        commandHandler.registerValueResolver(TeamManager.Team::class.java) { context -> teamManager.teams[context.pop()] }
        commandHandler.autoCompleter.registerParameterSuggestions(TeamManager.Team::class.java,
            SuggestionProvider.of { teamManager.teams.keys })

        commandHandler.registerParameterValidator(TeamManager::class.java) { value, _: CommandParameter?, _: CommandActor? ->
            if (value == null) {
                throw CommandErrorException("This team doesn't exist!")
            }
        }

        commandHandler.registerValueResolver(ArenaMap::class.java) { context -> mapRegistry.get(context.pop()) }
        commandHandler.autoCompleter.registerParameterSuggestions(ArenaMap::class.java,
            SuggestionProvider.of { mapRegistry.getKeys() })

        commandHandler.registerParameterValidator(ArenaMap::class.java) { value, _: CommandParameter?, _: CommandActor? ->
            if (value == null) {
                throw CommandErrorException("This map doesn't exist!")
            }
        }

        commandHandler.register(SaveTheKweebecsCommand())
        commandHandler.register(LobbyCommand())
        commandHandler.registerBrigadier()

        pluginManager.registerEvents(joinLeaveListener, this)
        pluginManager.registerEvents(ArenaListener(), this)

        // Player data loaders and spawnpoints
        pluginManager.registerEvents(PlayerDataLoader(this, playerManager), this)
        pluginManager.registerEvents(PlayerSpawnpointOnJoin(hubWorldName, "lobby_spawnpoint"), this)

        pluginManager.registerEvents(PlayerLocaleDetector(playerManager), this)

        soundManager.runTaskTimer(instance(), 0L, 2L)
        textPopUp.runTaskTimer(instance(), 0L, 5L)

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