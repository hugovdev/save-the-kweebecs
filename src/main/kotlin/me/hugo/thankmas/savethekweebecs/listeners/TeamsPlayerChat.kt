package me.hugo.thankmas.savethekweebecs.listeners

import dev.kezz.miniphrase.audience.sendTranslated
import io.papermc.paper.event.player.AsyncChatEvent
import me.hugo.thankmas.ThankmasPlugin
import me.hugo.thankmas.lang.Translated
import me.hugo.thankmas.player.player
import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.extension.arena
import me.hugo.thankmas.savethekweebecs.extension.hasStarted
import me.hugo.thankmas.savethekweebecs.extension.playerData
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/**
 * Special chat implementation that adds team icons
 *  in front of player names when playing.
 */
public class TeamsPlayerChat : Listener, Translated {

    @EventHandler
    public fun onPlayerChat(event: AsyncChatEvent) {
        val player = event.player
        val arena = player.arena()

        event.viewers().clear()

        // Add console back to keep chat logs!
        event.viewers().add(Bukkit.getConsoleSender())

        // Establish the recipients:
        // - Arena Players
        // - Lobby Players
        event.viewers().addAll(arena?.arenaPlayers()?.mapNotNull { it.player() }
            ?: Bukkit.getOnlinePlayers().filter { it.arena() == null })

        val team = player.playerData().currentTeam

        // If arena has started and player has no team, they're a spectator!
        if (arena != null && arena.hasStarted() && team == null) {
            player.sendTranslated("global.chat.cant_speak")
            event.isCancelled = true

            return
        }

        event.renderer { source, sourceName, message, viewer ->
            if (viewer is Player) {
                val sourceData = SaveTheKweebecs.instance().playerDataManager.getPlayerData(source.uniqueId)

                val messageBuilder = Component.text()

                // If a player has a team assigned, add their team icon in front of their nametag!
                if (arena?.hasStarted() == true && team != null) messageBuilder.append(
                    Component.text(team.chatIcon).append(Component.space())
                )

                messageBuilder.append(
                    ThankmasPlugin.instance().globalTranslations.translate(
                        "rank.${sourceData.getPrimaryGroupName()}.chat",
                        viewer.locale()
                    ) {
                        inserting("message", message)
                        inserting("player", sourceName)
                        inserting("nametag", sourceData.getNameTag(viewer.locale()))
                    })

                messageBuilder.build()
            } else Component.text(("[${arena?.displayName ?: "Lobby"}] ${source.name}: ")).append(message)
        }
    }

}