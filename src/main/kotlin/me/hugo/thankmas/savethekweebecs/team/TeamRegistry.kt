package me.hugo.thankmas.savethekweebecs.team

import me.hugo.thankmas.config.ConfigurationProvider
import me.hugo.thankmas.registry.AutoCompletableMapRegistry
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Registry of every team that can be
 * played as in Save The Kweebecs.
 */
@Single
public class TeamRegistry : AutoCompletableMapRegistry<MapTeam>(MapTeam::class.java), KoinComponent {

    private val configProvider: ConfigurationProvider by inject()

    init {
        val config = configProvider.getOrLoad("save_the_kweebecs/teams.yml")

        config.getKeys(false).forEach { teamId -> register(teamId, MapTeam(teamId, config)) }
    }
}