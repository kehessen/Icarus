package me.kehessen.icarus

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class SmokeGrenade(val config: FileConfiguration) : Listener {
    val smokeGrenade = CustomItem(
        Material.GRAY_DYE, "§r§l§cSmoke Grenade", "§7Right click to throw"
    )

    // load from config
    private var radius: Double = config.getDouble("SmokeGrenade.radius")
    private val cooldown: Long = config.getInt("SmokeGrenade.cooldown").toLong()

    fun start() {
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("Icarus")!!)
        radius = Bukkit.getPluginManager().getPlugin("Icarus")!!.config.getDouble("SmokeGrenade.radius")
        val recipe = ShapedRecipe(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "smoke_grenade"), smokeGrenade
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
                    Bukkit.getPluginManager().getPlugin("Icarus")!!, "smoke_grenade"
                )
            )
        ) {
            event.player.discoverRecipe(
                NamespacedKey(
                    Bukkit.getPluginManager().getPlugin("Icarus")!!, "smoke_grenade"
                )
            )
        }
    }

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {
        if (event.item == null || event.item!!.itemMeta == null) return
        if (event.item!!.itemMeta!!.displayName == smokeGrenade.itemMeta!!.displayName && !event.player.hasCooldown(
                smokeGrenade.type
            ) && (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK)
        ) {
            val proj = event.player.launchProjectile(Snowball::class.java)
            proj.scoreboardTags.add("smoke_grenade")
            proj.velocity = event.player.location.direction.multiply(5)
            if (event.player.gameMode != org.bukkit.GameMode.CREATIVE) {
                event.item!!.amount -= 1
                event.player.setCooldown(smokeGrenade.type, cooldown.toInt())
            }
        }
    }

    @EventHandler
    fun onImpact(event: ProjectileHitEvent) {
        if (event.entity.scoreboardTags.contains("smoke_grenade")) {
            val loc = event.entity.location.add(0.0, radius / 2, 0.0)
            val world = loc.world
            world!!.spawnParticle(
                Particle.CAMPFIRE_COSY_SMOKE, loc, (1000 * radius).toInt(), radius, radius / 2, radius, 0.01
            )
            event.entity.getNearbyEntities(radius, radius, radius).forEach {
                if (it !is LivingEntity) return@forEach
                it.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 50, 1, true, false))
                it.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 50, 1, true, false))
                it.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 50, 1, true, false))
            }
        }
    }
}