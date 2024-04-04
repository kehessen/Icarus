package me.kehessen.customplugin

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.entity.Egg
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ShapedRecipe

class SmokeGrenade : Listener {
    private val smokeGrenade = CustomItem(
        Material.GRAY_DYE,
        "§r§l§cSmoke Grenade",
        "§7Right click to throw"
    )
    private val radius = 4.0

    fun start() {
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("CustomPlugin")!!)
        val recipe = ShapedRecipe(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CustomPlugin")!!, "smoke_grenade"),
            smokeGrenade
        )
        recipe.shape("BC", "GS")
        recipe.setIngredient('G', Material.GUNPOWDER)
        recipe.setIngredient('S', Material.SUGAR)
        recipe.setIngredient('B', Material.BONE_MEAL)
        recipe.setIngredient('C', Material.COAL)
        Bukkit.addRecipe(recipe)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!event.player.hasDiscoveredRecipe(
                NamespacedKey(
                    Bukkit.getPluginManager().getPlugin("CustomPlugin")!!,
                    "smoke_grenade"
                )
            )
        ) {
            event.player.discoverRecipe(
                NamespacedKey(
                    Bukkit.getPluginManager().getPlugin("CustomPlugin")!!,
                    "smoke_grenade"
                )
            )
        }
    }

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {
        if (event.item == null) return
        if (event.item!!.itemMeta!!.displayName == smokeGrenade.itemMeta!!.displayName && event.action == Action.RIGHT_CLICK_AIR) {
            val egg = event.player.launchProjectile(Egg::class.java)
            egg.scoreboardTags.add("smoke_grenade")
            egg.velocity = event.player.location.direction.multiply(1.5)
            event.item!!.amount -= 1
        }
    }

    @EventHandler
    fun onEggBreak(event: ProjectileHitEvent) {
        if (event.entity.scoreboardTags.contains("smoke_grenade")) {
            val loc = event.entity.location
            val world = loc.world
            world!!.spawnParticle(
                Particle.CAMPFIRE_COSY_SMOKE,
                loc,
                (2000 * radius).toInt(),
                radius,
                radius,
                radius,
                0.01
            )
            event.isCancelled = true
        }
    }
}