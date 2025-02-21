package me.kehessen.icarus.misc

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe

class LightBulbCrafting(val config: FileConfiguration) : Listener {
    private val enabled = config.getBoolean("Other.enable-lightbulb")
    private val amount = config.getInt("Other.lightbulb-amount")
    val item = ItemStack(Material.LIGHT, amount)

    private fun addLightBulbRecipe() {
        if (!enabled) return
        
        val recipe = ShapedRecipe(NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "LightBulb"), item)
        recipe.shape(" G ", "GTG", " G ")
        recipe.setIngredient('G', Material.GLASS_PANE)
        recipe.setIngredient('T', Material.TORCH)
        Bukkit.addRecipe(recipe)
    }

    fun start() {
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("Icarus")!!)
        addLightBulbRecipe()
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        val key = NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "LightBulb")
        if (event.player.hasDiscoveredRecipe(key) || !enabled) return
        event.player.discoverRecipe(key)
    }
}