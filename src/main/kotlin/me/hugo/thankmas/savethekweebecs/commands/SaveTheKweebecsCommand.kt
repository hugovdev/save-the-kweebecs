package me.hugo.thankmas.savethekweebecs.commands

import dev.kezz.miniphrase.audience.sendTranslated
import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.extension.arena
import me.hugo.thankmas.savethekweebecs.extension.hasStarted
import me.hugo.thankmas.savethekweebecs.extension.playerData
import me.hugo.thankmas.savethekweebecs.game.arena.Arena
import me.hugo.thankmas.savethekweebecs.game.arena.ArenaRegistry
import me.hugo.thankmas.savethekweebecs.game.map.ArenaMap
import me.hugo.thankmas.savethekweebecs.game.map.MapRegistry
import me.hugo.thankmas.savethekweebecs.scoreboard.KweebecScoreboardManager
import me.hugo.thankmas.savethekweebecs.team.MapTeam
import me.hugo.thankmas.savethekweebecs.team.TeamRegistry
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import org.koin.core.component.inject
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.DefaultFor
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.util.*

@Command("savethekweebecs", "stk")
@Description("Main SaveTheKweebecs command.")
public class SaveTheKweebecsCommand : TranslatedComponent {

    private val main = SaveTheKweebecs.instance()

    private val mapRegistry: MapRegistry by inject()
    private val arenaRegistry: ArenaRegistry by inject()

    private val scoreboardManager: KweebecScoreboardManager by inject()
    private val teamRegistry: TeamRegistry by inject()

    @DefaultFor("savethekweebecs", "stk", "savethekweebecs help", "stk help")
    @Description("Help for the the main STK plugin.")
    private fun help(sender: Player) {
        sender.sendTranslated("system.help")
    }

    @Subcommand("auto-join")
    @Description("Auto-joins a SaveTheKweebecs map!")
    private fun autoJoin(sender: Player) {
        // Picks the fullest available arena and joins!
        arenaRegistry.getValues().filter { it.teamPlayers().size < it.arenaMap.maxPlayers }
            .maxByOrNull { it.teamPlayers().size }?.joinArena(sender)
    }

    @Subcommand("arenas")
    @Description("Opens an arena selector menu!")
    private fun openArenasMenu(sender: Player) {
        mapRegistry.openArenasMenu(sender)
    }

    @Subcommand("transformations")
    @Description("Opens the transformations selector menu!")
    private fun openBannerSelector(sender: Player) {
        val arena = sender.arena()
        if (arena?.hasStarted() == true) return

        //sender.playerData()?.transformationsMenu?.open(sender)
    }

    @Subcommand("list")
    @Description("Lists arenas.")
    private fun listArenas(sender: Player) {
        sender.sendTranslated("arena.list.header")

        arenaRegistry.getValues().forEach {
            val unformattedString = (miniPhrase.translationRegistry["arena.list.member", sender.locale()]
                ?: miniPhrase.translationRegistry["arena.list.member", miniPhrase.defaultLocale])?.replace(
                "<arena_uuid>",
                it.uuid.toString()
            ) ?: return

            sender.sendMessage(
                miniPhrase.miniMessage.deserialize(
                    unformattedString,
                    Placeholder.parsed("display_name", it.displayName),
                    Placeholder.component("arena_state", it.state.getFriendlyName(sender.locale())),
                    Placeholder.parsed("team_size", (it.arenaMap.maxPlayers / 2).toString()),
                    Placeholder.parsed("map_name", it.arenaMap.mapName),
                    Placeholder.parsed("current_players", it.teamPlayers().size.toString()),
                    Placeholder.parsed("max_players", it.arenaMap.maxPlayers.toString())
                )
            )
        }
    }

    @Subcommand("join")
    @Description("Joins an arena.")
    private fun listArenas(sender: Player, uuid: String) {
        try {
            val arena = arenaRegistry.getOrNull(UUID.fromString(uuid))

            if (arena == null) {
                sender.sendTranslated("arena.join.noExist")
                return
            }

            arena.joinArena(sender)
        } catch (exception: IllegalArgumentException) {
            sender.sendTranslated("arena.join.noExist")
        }
    }

    @Subcommand("leave")
    @Description("Leave the arena you're in!")
    private fun leaveArena(sender: Player) {
        val currentArena = sender.playerData().currentArena

        if (currentArena == null) {
            sender.sendTranslated("arena.leave.notInArena")
            return
        }

        currentArena.leave(sender)
    }

    @Subcommand("shop")
    @Description("Opens the shop for the player!")
    private fun openShop(sender: Player) {
        val currentArena = sender.playerData().currentArena

        if (currentArena == null) {
            sender.sendTranslated("arena.leave.notInArena")
            return
        }

        val playerData = sender.playerData()
        val team = playerData.currentTeam ?: return

        if (team.shopItems.isEmpty()) return

         team.openShopMenu(sender)
    }

    @DefaultFor("savethekweebecs admin", "stk admin")
    @Description("Help for the admin system.")
    @CommandPermission("savethekweebecs.admin")
    private fun helpAdmin(sender: Player) {
        sender.sendTranslated("system.admin.help")
    }

    @Subcommand("admin settimer")
    @Description("Sets the arena timer!")
    @CommandPermission("savethekweebecs.admin")
    private fun setHub(sender: Player, seconds: Int) {
        sender.arena()?.time = seconds
    }

    @Subcommand("admin openarena")
    @Description("Creates an arena using the chosen map.")
    @CommandPermission("savethekweebecs.admin")
    private fun createArena(sender: Player, map: ArenaMap, displayName: String) {
        val arena = Arena(map, displayName)

        arenaRegistry.register(arena.uuid, arena)

        sender.sendMessage(
            Component.text(
                "Successfully created arena in map \"${map.mapName}\" with display name \"$displayName\"!",
                NamedTextColor.GREEN
            )
        )
    }

    @DefaultFor("savethekweebecs admin kit", "stk admin kit")
    @Description("Help for the kit system.")
    @CommandPermission("savethekweebecs.admin")
    private fun helpKit(sender: Player) {
        sender.sendTranslated("system.kit.help")
    }

    @Subcommand("admin kit get")
    @Description("Gives the kit for the team!")
    @CommandPermission("savethekweebecs.admin")
    private fun getKit(sender: Player, team: MapTeam) {
        val items = team.kitItems

        if (items.isEmpty()) {
            sender.sendTranslated("system.kit.noKit") {
                unparsed("team", team.id)
            }
            return
        }

        team.giveItems(sender, clearInventory = true, giveArenaItemSet = false)
    }

    @DefaultFor("savethekweebecs admin shop", "stk admin shop")
    @Description("Help for the shop system.")
    @CommandPermission("savethekweebecs.admin")
    private fun helpShop(sender: Player) {
        sender.sendTranslated("system.shop.help")
    }

    @Subcommand("admin shop get")
    @Description("Get the item from a team's shop.")
    @CommandPermission("savethekweebecs.admin")
    private fun getShopItem(sender: Player, key: String, team: MapTeam) {
        team.giveShopItem(sender, key)
    }
}