package me.kehessen.icarus

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

class CustomItem(itemType: Material, displayName: String, vararg lore: String) :
    ItemStack(itemType) {
    init {
        val meta: ItemMeta = itemMeta!!
        meta.setDisplayName(displayName)
        meta.lore = lore.toMutableList()
        itemMeta = meta
    }
}