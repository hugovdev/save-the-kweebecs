package me.hugo.thankmas.savethekweebecs.team

import dev.kezz.miniphrase.audience.sendTranslated
import me.hugo.thankmas.config.string
import me.hugo.thankmas.gui.Icon
import me.hugo.thankmas.gui.Menu
import me.hugo.thankmas.items.TranslatableItem
import me.hugo.thankmas.items.addToLore
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.player.playSound
import me.hugo.thankmas.player.skinProperty
import me.hugo.thankmas.player.translateList
import me.hugo.thankmas.savethekweebecs.extension.playerData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.koin.core.component.inject

/**
 * A team that can be played as in Save The Kweebecs and all its data:
 *
 * - id
 * - List of available skins
 * - Chat and team icons
 * - Items their kit has
 * - Items they can buy.
 */
public class MapTeam(
    public val id: String,
    public val skins: List<TeamSkin>,
    public val chatIcon: String,
    public val teamIcon: String,
    public var kitItems: Map<Int, TranslatableItem> = emptyMap(),
    public var shopItems: Map<String, TeamShopItem> = emptyMap()
) : TranslatedComponent {

    private val itemSetManager: ItemSetRegistry by inject()
    private val shopMenu = Menu("menu.shop.title", 9 * 6, mutableMapOf(), Menu.MenuFormat.STK_SHOP, miniPhrase)

    /** Loads all this team's information from a config file. */
    public constructor(teamId: String, config: FileConfiguration) : this(
        teamId,
        config.getConfigurationSection("$teamId.player-skin-variants")?.getKeys(false)?.map {
            val visualPath = "$teamId.player-skin-variants.$it"

            TeamSkin(
                it,
                skinProperty(config, "$visualPath.skin"),
                config.string("$visualPath.head-model")
            )
        } ?: emptyList(),
        config.string("$teamId.chat-icon"),
        config.string("$teamId.team-icon"),
        config.getConfigurationSection("$teamId.items")?.getKeys(false)
            ?.associate { slot -> slot.toInt() to TranslatableItem(config, "$teamId.items.$slot") }
            ?: emptyMap(),
        config.getConfigurationSection("$teamId.shop-items")?.getKeys(false)?.associateWith { key ->
            TeamShopItem("$teamId.shop-items.$key", config)
        } ?: emptyMap()
    )

    init {
        shopItems.values.forEach { shopMenu.addIcon(it.getIcon()) }
    }

    /**
     * Gives [player] the items in this team's kit.
     *
     * If [giveArenaItemSet] is true it will also give
     * the clickable item set "arena". Used mainly for the shop.
     */
    public fun giveItems(
        player: Player,
        clearInventory: Boolean = false,
        giveArenaItemSet: Boolean = true
    ) {
        val inventory = player.inventory

        if (clearInventory) {
            inventory.clear()
            inventory.setArmorContents(null)
        }

        kitItems.forEach { inventory.setItem(it.key, it.value.buildItem(player)) }
        if (giveArenaItemSet) itemSetManager.giveSetNullable("arena", player)
    }

    /** Opens this team's shop menu to [player]. */
    public fun openShopMenu(player: Player) {
        shopMenu.open(player)
    }

    /** Gives [player] the shop item with id [key]. */
    public fun giveShopItem(player: Player, key: String) {
        player.inventory.addItem(shopItems.getValue(key).item.buildItem(player))
    }
}

/**
 * Item that can be bought in the shop for
 * certain [MapTeam].
 */
public data class TeamShopItem(val item: TranslatableItem, val cost: Int) : TranslatedComponent {

    /** Retries this shop item from a config file. */
    public constructor(path: String, config: FileConfiguration) : this(
        TranslatableItem(config, "$path.item"),
        config.getInt("$path.cost")
    )

    /**
     * Returns a clickable icon used to buy this shop item.
     */
    public fun getIcon(): Icon {
        return Icon({ context, _ ->
            val clicker = context.clicker

            val playerData = clicker.playerData()
            val canBuy = playerData.coins >= cost

            if (!canBuy) {
                clicker.sendTranslated("menu.shop.poor")
                clicker.playSound(Sound.ENTITY_ENDERMAN_TELEPORT)

                return@Icon
            }

            val vanillaItem = item.buildItem(clicker)

            clicker.sendTranslated("menu.shop.item_bought") {
                inserting(
                    "item",
                    Component.text(
                        PlainTextComponentSerializer.plainText()
                            .serialize(vanillaItem.itemMeta?.displayName() ?: vanillaItem.displayName()),
                        NamedTextColor.GREEN
                    )
                )
                unparsed("amount", item.amount.toString())
            }
            playerData.addCoins(cost * -1, "bought_item")

            clicker.inventory.addItem(vanillaItem)
            clicker.playSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP)

            clicker.closeInventory()
        }) {

            val isAvailable = it.playerData().coins >= cost
            val vanillaItem = item.buildItem(it)

            vanillaItem.clone().addToLore(
                it.translateList(
                    if (isAvailable)
                        "menu.shop.icon.availableLore"
                    else "menu.shop.icon.notAvailableLore",
                ) {
                    unparsed("cost", cost.toString())
                })
        }
    }
}