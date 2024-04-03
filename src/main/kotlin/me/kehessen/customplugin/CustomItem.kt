package me.kehessen.customplugin

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

class CustomItem(private val itemType: Material, private val displayName: String, private vararg val lore: String) :
    ItemStack(itemType) {
    init {
        val meta: ItemMeta = itemMeta!!
        meta.setDisplayName(displayName)
        meta.lore = lore.toMutableList()
        itemMeta = meta
    }

    private fun getItem(): ItemStack {
        val item = ItemStack(itemType)
        val meta = item.itemMeta
        meta!!.setDisplayName(displayName)
        meta.lore = lore.toMutableList()
        item.itemMeta = meta
        return item
    }

}