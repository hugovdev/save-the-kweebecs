package me.hugo.thankmas.savethekweebecs.util

import org.bukkit.FireworkEffect
import org.bukkit.Location
import org.bukkit.entity.Firework

/**
 * Firework that instantly spawns.
 */
public class InstantFirework(effect: FireworkEffect, location: Location) {
    init {
        val firework = location.world.spawn(location, Firework::class.java)
        val meta = firework.fireworkMeta
        meta.addEffect(effect)
        firework.fireworkMeta = meta
    }
}