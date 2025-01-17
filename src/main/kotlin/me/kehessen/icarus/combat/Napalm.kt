package me.kehessen.icarus.combat

import me.kehessen.icarus.util.CustomItem
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ShapedRecipe

class Napalm(config: FileConfiguration, private val base: Base) : Listener {

    internal val item = CustomItem(Material.FIRE_CHARGE, "§cNapalm", "§7Right click to launch Napalm")
    private val enabled: Boolean = config.getBoolean("Napalm.enable")
    private val radius: Int = config.getInt("Napalm.radius")
    private val amount: Int = config.getInt("Napalm.amount")
    private val delay: Long = config.getInt("Napalm.delay").toLong()
    private val protectBase: Boolean = config.getBoolean("Base.protect-from-napalm")

    // + leaves, but checking in function, so I don't have to add every type to the set
    private val ignitableBlocks = setOf(Material.AIR, Material.SHORT_GRASS, Material.TALL_GRASS)

    fun start() {
        if (!enabled) return
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("Icarus")!!)
        addRecipe()
    }

    private fun addRecipe() {
        val recipe = ShapedRecipe(NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "napalm"), item)
        recipe.shape("   ", "BGB", "CFC")
        recipe.setIngredient('B', Material.BLAZE_POWDER)
        recipe.setIngredient('G', Material.GHAST_TEAR)
        recipe.setIngredient('C', Material.FIRE_CHARGE)
        recipe.setIngredient('F', Material.FLINT_AND_STEEL)
        Bukkit.addRecipe(recipe)
    }

    private fun launchNapalm(player: Player) {
        var i = 0
        Bukkit.getScheduler().runTaskTimer(Bukkit.getPluginManager().getPlugin("Icarus")!!, Runnable {
            if (i > amount) return@Runnable
            i++
            val fireball = player.launchProjectile(org.bukkit.entity.Snowball::class.java)
            fireball.isVisualFire = true
            fireball.scoreboardTags.add("napalm")
            player.playSound(player.location, Sound.ENTITY_GHAST_SHOOT, 1f, 1f)
        }, 0, delay)
    }


    private fun setFire(center: Location, radius: Int) {
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val currentLocation: Location = center.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                    if (center.distance(currentLocation) > radius) continue
                    val block: Block = currentLocation.block
                    val aboveBlock: Block = currentLocation.clone().add(0.0, 1.0, 0.0).block
                    if ((ignitableBlocks.contains(aboveBlock.type) || aboveBlock.type.name.contains("LEAVES")) && block.type != Material.AIR) {
                        aboveBlock.type = Material.FIRE
                    }
                }
            }
        }
    }

    @EventHandler
    private fun onRightClick(event: PlayerInteractEvent) {
        if (event.item?.itemMeta?.lore == null) return
        if (event.player.hasCooldown(event.item!!.type)) return
        if (event.action == Action.RIGHT_CLICK_AIR) {
            if (event.item!!.itemMeta!!.lore == item.itemMeta!!.lore) {
                if (!event.player.isGliding) {
                    event.player.sendMessage("§cCan only be used while flying")
                    return
                }
                launchNapalm(event.player)
                event.player.setCooldown(event.item!!.type, (delay * amount).toInt())
                if (event.player.gameMode != GameMode.CREATIVE) {
                    event.item!!.amount--
                }
            }
        }
    }

    @EventHandler
    private fun onProjectileHit(event: ProjectileHitEvent) {
        if (event.entity.scoreboardTags.contains("napalm")) {
            if (event.hitEntity != null) {
                event.hitEntity!!.fireTicks = 150
            }
            if (!protectBase && !base.isProtected(event.entity.location)) {
                setFire(event.entity.location, radius)
            }
            event.entity.remove()
        }
    }

    @EventHandler
    private fun onJoin(event: PlayerJoinEvent) {
        if (!event.player.hasDiscoveredRecipe(
                NamespacedKey(
                    Bukkit.getPluginManager().getPlugin("Icarus")!!,
                    "napalm"
                )
            )
        ) {
            event.player.discoverRecipe(NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "napalm"))
        }
    }
}
