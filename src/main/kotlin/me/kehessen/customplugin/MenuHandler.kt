package me.kehessen.customplugin

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.Listener
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack


class MenuHandler : Listener {

    fun createInventory(rows: Int, name: String, owner: InventoryHolder): Inventory {
        if (rows > 6) throw IllegalArgumentException("Inventory can't have more than 6 rows")
        return Bukkit.createInventory(owner, rows * 9, name)
    }

    fun createItem(material: Material, inv: Inventory, slot: Int, name: String, vararg itemLore: String) {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta!!.setDisplayName("§r$name")
        val lore = arrayListOf<String>()
        itemLore.forEach { lore.add(it) }
        meta.lore = lore
        item.setItemMeta(meta)

        inv.setItem(slot, item)
    }

    fun fillInventory(inv: Inventory, material: Material) {
        for (i in 0 until inv.size) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, ItemStack(material))
            }
        }
    }

    fun fillWithoutName(inv: Inventory, material: Material, vararg except: Int = intArrayOf(-1)) {
        for (i in 0 until inv.size) {
            if (except.contains(i)) continue
            if (inv.getItem(i) == null) {
                val item = ItemStack(material)
                val meta = item.itemMeta
                meta!!.setDisplayName("§r")
                item.itemMeta = meta
                inv.setItem(i, item)
            }
        }
    }

}