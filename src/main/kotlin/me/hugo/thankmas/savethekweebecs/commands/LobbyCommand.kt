package me.hugo.thankmas.savethekweebecs.commands

import me.hugo.thankmas.ThankmasPlugin
import me.hugo.thankmas.savethekweebecs.extension.arena
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description

public class LobbyCommand {

    @Command("lobby", "hub", "leave", "back")
    @Description("Leave an arena or go back to the main hub.")
    private fun sendToLobby(sender: Player) {
        val arena = sender.arena()

        if (arena != null) {
            arena.leave(sender)
        } else {
            ThankmasPlugin.instance<ThankmasPlugin<*>>().playerDataManager.getPlayerData(sender.uniqueId).saveSafely(sender) {
                sender.kick(Component.text("Sending back to hub..."))
            }
        }
    }

}