package me.kehessen.icarus

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.entity.Wither
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.sin

class Airstrike(config: FileConfiguration) : Listener {
    private val missileAmount = config.getInt("Airstrike.missile-amount")
    private val missileDelay = config.getInt("Airstrike.missile-delay").toLong()
    private val missileYield = config.getDouble("Airstrike.missile-yield").toFloat()
    private val initialDelay = config.getInt("Airstrike.initial-delay").toLong()

    internal val item = CustomItem(
        Material.BOW, "§r§c§lAirstrike", "§r§7Shoot to call an airstrike"
    )

    internal fun start() {
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("Icarus")!!)

        val recipe = ShapedRecipe(NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "airstrike"), item)
        recipe.shape("III", "BSB", "IRI")
        recipe.setIngredient('I', Material.IRON_BLOCK)
        recipe.setIngredient('B', Material.BLAZE_ROD)
        recipe.setIngredient('S', Material.NETHER_STAR)
        recipe.setIngredient('R', Material.REDSTONE_BLOCK)
        Bukkit.addRecipe(recipe)
    }

    @EventHandler
    private fun onShot(event: ProjectileLaunchEvent) {
        if (event.entity !is Arrow) return
        if (event.entity.shooter !is Player) return
        val player = event.entity.shooter as Player
        if (player.inventory.itemInMainHand.itemMeta!!.displayName == item.itemMeta!!.displayName || player.inventory.itemInOffHand.itemMeta!!.displayName == item.itemMeta!!.displayName) {
            val arrow = event.entity as Arrow
            arrow.addScoreboardTag("airstrikeArrow")
            arrow.color = org.bukkit.Color.RED
            arrow.isGlowing = true

            if (player.gameMode == org.bukkit.GameMode.CREATIVE) return
            if (player.inventory.itemInMainHand.itemMeta!!.displayName == item.itemMeta!!.displayName) player.inventory.itemInMainHand.amount--
            else if (player.inventory.itemInOffHand.itemMeta!!.displayName == item.itemMeta!!.displayName) player.inventory.itemInOffHand.amount--
            else Bukkit.getLogger()
                .warning("Airstrike was called, but ${player.name} didn't have the item in their hand.")
        }
    }

    @EventHandler
    private fun onArrowHit(event: ProjectileHitEvent) {
        if (event.entity !is Arrow) return
        val position = event.entity.location.clone()
        if (event.entity.scoreboardTags.contains("airstrikeArrow")) {
            for (i in 0 until 360 step 60) {
                val x = cos(Math.toRadians(i.toDouble())) / 4
                val z = sin(Math.toRadians(i.toDouble())) / 4
                val startPosition = position.clone().add(x, 0.0, z)
                val endPosition = startPosition.clone().add(0.0, 75.0, 0.0)
                Utils.drawLine(startPosition, endPosition, Particle.DRIP_LAVA, 300)
            }
            for (i in 0 until missileAmount) {
                Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("Icarus")!!, Runnable {
                    val missile = position.world!!.spawnArrow(
                        position.clone().add(0.0, 150.0, 0.0), position.direction, 0.0f, 0.0f
                    )
                    missile.addScoreboardTag("airstrikeMissile")
                    missile.velocity = Vector(0.0, -10.0, 0.0)
                    missile.isGlowing = true
                    missile.color = org.bukkit.Color.RED
                }, initialDelay + i * missileDelay)
            }
        }
        if (event.entity.scoreboardTags.contains("airstrikeMissile")) {
            val missile = event.entity
            val location = missile.location
            val world = missile.world
            Utils.drawLine(location, location.clone().add(0.0, 100.0, 0.0), Particle.LAVA, 300)
            world.createExplosion(location, missileYield, true, true)
            missile.remove()
        }

        event.entity.remove()
    }

    @EventHandler
    private fun onWitherKill(event: EntityDeathEvent) {
        if (event.entity !is Wither) return
        if (event.entity.killer !is Player) return
        val player = event.entity.killer as Player
        if (!player.hasDiscoveredRecipe(NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "airstrike"))) {
            player.discoverRecipe(NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "airstrike"))
        }
    }
}