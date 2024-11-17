package me.hugo.thankmas.savethekweebecs.game.arena

import me.hugo.thankmas.registry.MapBasedRegistry
import org.koin.core.annotation.Single
import java.util.*

@Single
public class ArenaRegistry : MapBasedRegistry<UUID, Arena>() {

    override fun register(key: UUID, value: Arena, replace: Boolean) {
        super.register(key, value, replace)

        // TODO: Update blah blah blah
    }

}