package me.hugo.thankmas.savethekweebecs.game.events

import dev.kezz.miniphrase.MiniPhraseContext
import dev.kezz.miniphrase.audience.sendTranslated
import me.hugo.thankmas.items.flags
import me.hugo.thankmas.items.loreTranslatable
import me.hugo.thankmas.items.nameTranslatable
import me.hugo.thankmas.savethekweebecs.extension.end
import me.hugo.thankmas.savethekweebecs.extension.player
import me.hugo.thankmas.savethekweebecs.game.arena.Arena
import org.bukkit.Material
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

/**
 * Special event that can occur in-game when playing
 * in Save The Kweebecs.
 *
 * [eventRun] runs when the event occurs.
 */
public enum class ArenaEvent(
    public val eventRun: MiniPhraseContext.(arena: Arena) -> Unit
) {

    PATCH_UP({ arena ->
        arena.playersPerTeam[arena.arenaMap.defenderTeam]?.mapNotNull { it.player() }?.forEach { player ->
            player.inventory.addItem(
                ItemStack(Material.BAMBOO_PLANKS, 2)
                    .nameTranslatable("arena.event.patch_up.blocksName", player.locale())
                    .loreTranslatable("arena.event.patch_up.blocksLore", player.locale())
                    .flags(ItemFlag.HIDE_ATTRIBUTES)
            )

            player.sendTranslated("arena.event.patch_up.receive")
        }
    }),
    PETS({ arena ->
        arena.end(arena.arenaMap.defenderTeam)
    }),
    TRORKS_WIN({ arena ->
        arena.end(arena.arenaMap.defenderTeam)
    });

    public companion object {
        /**
         * Returns an [ArenaEvent] when given a
         * [serializedEvent] string.
         *
         * Returns null if the string doesn't follow the
         * format used by [serialize]
         */
        public fun deserialize(serializedEvent: String): Pair<ArenaEvent, Int>? {
            val split = serializedEvent.split(" , ")

            return if (split.size < 2) null
            else Pair(valueOf(split[0]), split[1].toInt())
        }
    }

    /**
     * Serializes this [ArenaEvent] into a string
     * with [seconds] as the time it takes for
     * the event to occur.
     */
    public fun serialize(seconds: Int): String {
        return "$name , $seconds"
    }
}