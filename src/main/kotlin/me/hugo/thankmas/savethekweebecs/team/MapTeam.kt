package me.hugo.thankmas.savethekweebecs.team

import me.hugo.thankmas.config.string
import me.hugo.thankmas.gui.Icon
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.items.putLore
import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.player.skinProperty
import me.hugo.thankmas.player.translateList
import me.hugo.thankmas.savethekweebecs.extension.playerData
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
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
    public var kitItems: Map<Int, ItemStack> = mapOf(),
    public var shopItems: MutableList<TeamShopItem> = mutableListOf()
) : KoinComponent {

    private val itemSetManager: ItemSetRegistry by inject()

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
            ?.associate { slot -> slot.toInt() to config.getItemStack("$teamId.items.$slot")!! }
            ?: mapOf(),
        config.getConfigurationSection("$teamId.shop-items")?.getKeys(false)
            ?.map { key ->
                TeamShopItem(
                    key,
                    config.getItemStack("$teamId.shop-items.$key.item") ?: ItemStack(Material.BEDROCK),
                    config.getInt("$teamId.shop-items.$key.cost")
                )
            }?.toMutableList() ?: mutableListOf()
    )

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

        kitItems.forEach { inventory.setItem(it.key, it.value) }
        if (giveArenaItemSet) itemSetManager.giveSetNullable("arena", player)
    }
}

/**
 * Item that can be bought in the shop for
 * certain [MapTeam].
 */
public data class TeamShopItem(val key: String, val item: ItemStack, val cost: Int) : TranslatedComponent {
    /**
     * Returns a clickable icon used to buy this shop item.
     */
    public fun getIcon(player: Player): Icon {
        val isAvailable = (player.playerData().coins ?: 0) >= cost
        val translatedLore = player.translateList(
            if (isAvailable)
                "menu.shop.icon.availableLore"
            else "menu.shop.icon.notAvailableLore",
        ) {
            unparsed("cost", cost.toString())
        }

        return Icon({ clicker, _ ->
            /* val playerData = clicker.playerData() ?: return@addClickAction
             val canBuy = (clicker.playerData()?.getCoins() ?: 0) >= cost

             if (!canBuy) {
                 clicker.sendTranslated("menu.shop.poor")
                 clicker.playSound(Sound.ENTITY_ENDERMAN_TELEPORT)

                 return@addClickAction
             }

             clicker.sendTranslated(
                 "menu.shop.item_bought",
                 component(
                     "item",
                     Component.text(
                         PlainTextComponentSerializer.plainText()
                             .serialize(item.itemMeta?.displayName() ?: item.displayName()), NamedTextColor.GREEN
                     )
                 ),
                 unparsed("amount", item.amount.toString())
             )
             playerData.addCoins(cost * -1, "bought_item")

             clicker.intelligentGive(item)
             clicker.playSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP)

             clicker.closeInventory()*/
        }) {
            ItemStack(item)
                .putLore(item.itemMeta?.lore()?.plus(translatedLore) ?: translatedLore)
        }
    }
}