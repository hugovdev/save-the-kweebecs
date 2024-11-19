package me.hugo.thankmas.savethekweebecs.extension

import me.hugo.thankmas.savethekweebecs.SaveTheKweebecs
import me.hugo.thankmas.savethekweebecs.game.arena.Arena
import me.hugo.thankmas.savethekweebecs.player.SaveTheKweebecsPlayerData
import me.hugo.thankmas.savethekweebecs.scoreboard.KweebecScoreboardManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*

private val playerManager
    get() = SaveTheKweebecs.instance().playerManager

public fun UUID.player(): Player? = Bukkit.getPlayer(this)
public fun UUID.playerData(): SaveTheKweebecsPlayerData = playerManager.getPlayerData(this)
public fun Player.arena(): Arena? = playerManager.getPlayerData(this.uniqueId).currentArena
public fun Player.playerData(): SaveTheKweebecsPlayerData = playerManager.getPlayerData(this.uniqueId)

public fun Inventory.firstIf(predicate: (ItemStack) -> Boolean): Pair<Int, ItemStack>? {
    for (slot in 0 until size) {
        val item = getItem(slot) ?: continue
        if (predicate(item)) return Pair(slot, item)
    }

    return null
}

/** @returns every online player with an active scoreboard. */
public fun playersWithBoard(): List<Player> {
    return Bukkit.getOnlinePlayers()
        .filter { SaveTheKweebecs.instance().playerManager.getPlayerDataOrNull(it.uniqueId)?.getBoardOrNull() != null }
}

/** Updates this player's board lines that contains [tags]. */
public fun Player.updateBoardTags(vararg tags: String) {
    val scoreboardManager: KweebecScoreboardManager = SaveTheKweebecs.instance().scoreboardManager
    val playerData = SaveTheKweebecs.instance().playerManager.getPlayerData(uniqueId)

    playerData.getBoardOrNull() ?: return

    scoreboardManager.getTemplate(playerData.lastBoardId).updateLinesForTag(this, *tags)
}

/** Updates this player's board lines that contains [tags]. */
public fun updateBoardTags(vararg tags: String) {
    playersWithBoard().forEach { it.updateBoardTags(*tags) }
}

public fun Player.intelligentGive(item: ItemStack) {
    inventory.addItem(item)
    /*val nmsItem: Item = CraftItemStack.asNMSCopy(item).item

    val slot: Int = if (MaterialTags.HELMETS.isTagged(item)) {
        39
    } else if (MaterialTags.CHESTPLATES.isTagged(item)) {
        38
    } else if (MaterialTags.LEGGINGS.isTagged(item)) {
        37
    } else if (MaterialTags.BOOTS.isTagged(item)) {
        36
    } else if (MaterialTags.SWORDS.isTagged(item)) {
        inventory.firstIf { MaterialTags.SWORDS.isTagged(it) }?.first ?: inventory.firstEmpty()
    } else if (MaterialTags.BOWS.isTagged(item)) {
        inventory.firstIf { MaterialTags.BOWS.isTagged(it) }?.first ?: inventory.firstEmpty()
    } else inventory.firstEmpty()

    val finalSlot = if (nmsItem is ArmorItem) {
        val originalItem = inventory.getItem(slot)

        if (originalItem == null) slot
        else {
            val originalNmsItem = CraftItemStack.asNMSCopy(originalItem).item as ArmorItem?

            if (originalNmsItem == null || originalNmsItem.defense >= nmsItem.defense) {
                inventory.firstEmpty()
            } else slot
        }
    } else if (MaterialTags.SWORDS.isTagged(item)) {
        val originalItem = inventory.getItem(slot)

        if (originalItem == null) slot
        else {
            val originalNmsItem = CraftItemStack.asNMSCopy(originalItem).item

            val originalDamage =
                originalNmsItem.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND)[Attributes.ATTACK_DAMAGE].sumOf(
                    AttributeModifier::getAmount
                )

            val newDamage =
                nmsItem.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND)[Attributes.ATTACK_DAMAGE].sumOf(
                    AttributeModifier::getAmount
                )

            if (originalDamage >= newDamage) {
                val originalEnchants = originalItem.itemMeta?.hasEnchants()

                if (item.itemMeta?.hasEnchants() == true && (originalEnchants == null || originalEnchants == false)) slot
                else if (originalItem.enchantments.entries.sumOf { it.value } < item.enchantments.entries.sumOf { it.value }) slot
                else null
            } else slot
        }
    } else if (MaterialTags.BOWS.isTagged(item)) {
        val originalItem = inventory.getItem(slot)

        if (originalItem == null) slot
        else {
            val originalEnchants = originalItem.itemMeta?.hasEnchants()

            if (item.itemMeta?.hasEnchants() == true && (originalEnchants == null || originalEnchants == false)) slot
            else if (originalItem.enchantments.entries.sumOf { it.value } < item.enchantments.entries.sumOf { it.value }) slot
            else null
        }
    } else null

    if (finalSlot == null) inventory.addItem(item)
    else {
        playSound(Sound.ITEM_ARMOR_EQUIP_CHAIN)
        inventory.setItem(finalSlot, item)
    }*/
}

public fun Player.playSound(sound: Sound): Unit = playSound(location, sound, 1.0f, 1.0f)

public fun Player.reset(gameMode: GameMode) {
    setGameMode(gameMode)
    health = getAttribute(Attribute.MAX_HEALTH)?.baseValue ?: 20.0
    foodLevel = 20
    exp = 0.0f
    level = 0
    arrowsInBody = 0

    fireTicks = 0

    closeInventory()

    inventory.clear()
    inventory.setArmorContents(null)

    inventory.heldItemSlot = 0

    activePotionEffects.forEach { removePotionEffect(it.type) }
}