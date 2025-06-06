package me.hugo.thankmas.savethekweebecs.extension

import me.hugo.thankmas.ThankmasPlugin
import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.game.arena.Arena
import me.hugo.thankmas.savethekweebecs.player.SaveTheKweebecsPlayerData
import org.bukkit.entity.Player
import java.util.*

private val playerManager
    get() = ThankmasPlugin.instance<SaveTheKweebecs>().playerDataManager

public fun Player.arena(): Arena? = playerManager.getPlayerData(this.uniqueId).currentArena

public fun UUID.playerData(): SaveTheKweebecsPlayerData = playerManager.getPlayerData(this)
public fun Player.playerData(): SaveTheKweebecsPlayerData = playerManager.getPlayerData(this.uniqueId)

public fun Player.giveGameItems() {

}