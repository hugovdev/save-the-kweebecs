package me.hugo.thankmas.savethekweebecs.game.arena

import me.hugo.thankmas.registry.MapBasedRegistry
import org.koin.core.annotation.Single
import java.util.*

@Single
public class ArenaRegistry : MapBasedRegistry<UUID, Arena>()