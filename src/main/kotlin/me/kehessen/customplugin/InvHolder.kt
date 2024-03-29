package me.kehessen.customplugin

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class InvHolder : InventoryHolder {
    override fun getInventory(): Inventory {
        return null!!
    }
}