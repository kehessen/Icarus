package me.kehessen.customplugin

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.Listener
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack


class MenuHandler : Listener {

    fun createInventory(rows: Int, name: String): Inventory {
        if (rows > 6) throw IllegalArgumentException("Inventory can't have more than 6 rows")
        return Bukkit.createInventory(null, rows * 9, name)
    }

    fun createItem(material: Material, inv: Inventory, slot: Int, name: String, vararg itemLore: String) {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta!!.setDisplayName(name)
        val lore = arrayListOf<String>()
        itemLore.forEach { lore.add(it) }
        meta.lore = lore
        item.setItemMeta(meta)

        inv.setItem(slot, item)
    }

}