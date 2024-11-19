package me.hugo.thankmas.savethekweebecs.team

import com.destroystokyo.paper.profile.ProfileProperty
import me.hugo.thankmas.items.TranslatableItem
import org.bukkit.Material
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemFlag

/**
 * Skin and custom head that can be selected
 * as a transformation for certain [MapTeam].
 */
public class TeamSkin(public val key: String, public val skin: ProfileProperty, headModel: String) {
    public val displayItem: TranslatableItem = TranslatableItem(
        material = Material.CARVED_PUMPKIN,
        flags = listOf(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP),
        name = "global.cosmetic.head.$key.hat_name",
        equipabbleSlot = EquipmentSlot.HEAD,
        model = headModel
    )
}