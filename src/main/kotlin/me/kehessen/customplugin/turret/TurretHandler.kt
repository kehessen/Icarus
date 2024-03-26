package me.kehessen.customplugin.turret

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused", "DuplicatedCode")
class TurretHandler(private val plugin: JavaPlugin, config: FileConfiguration) : CommandExecutor {

    private val shootDelay: Long = 3 //config.getLong("Turret.shoot-delay")
    private val particleDelay: Long = 2 //config.getLong("Turret.particle-delay")
    private val particleAmount = 20 //config.getInt("Turret.particle-amount")
    private val particleType = Particle.FLAME
    private val particleSpread = 0.2 //config.getDouble("Turret.particle-spread")

    private var activeTurrets = arrayListOf<ArmorStand>()

    private val turretReach = 500 //config.getInt("Turret.reach")
    private val arrowDamage = 0.5 //config.getDouble("Turret.damage")

    // blocks per tick
    private val speedMultiplier: Float = 5f //config.getInt("Turret.arrow-speed-multiplier")

    private var activeArrows = hashMapOf<Arrow, Int>()
    private val arrowLifeTime = 20 / shootDelay.toInt() * 3

    private val onlinePlayers = Bukkit.getOnlinePlayers()
    private val targets = mutableSetOf<Player>()
    private val arrowsToRemove = mutableListOf<Arrow>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // /removeallturrets
        if (command.name == "removeallturrets") {
            activeTurrets.forEach { turret ->
                sender.sendMessage("§a Removed turret with ID ${turret.entityId}")
                turret.remove()
            }
            Bukkit.getWorld("world")!!.entities.forEach { entity ->
                if (entity is Arrow && entity.scoreboardTags.contains("TurretArrow")) {
                    entity.remove()
                }
            }
            activeArrows.clear()
            activeTurrets.clear()
            return true
        }

        // /spawnturret
        if (Bukkit.getPlayer(sender.name) !is Player) return false
        val player = Bukkit.getPlayer(sender.name)
        val armorStand =
            player!!.world.spawn(player.location, ArmorStand::class.java)
        armorStand.setGravity(false)
        armorStand.isInvulnerable = true
        armorStand.customName = "§cTurret"
        armorStand.isCustomNameVisible = true
        armorStand.isVisible = true
        armorStand.removeWhenFarAway = false

        armorStand.addScoreboardTag("Turret")
        activeTurrets.add(armorStand)

        return true
    }

    fun startTask() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            onInterval()
        }, 0, shootDelay)
        // particles
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            spawnParticles()
        }, 0, particleDelay)
    }

    private fun spawnParticles() {
        activeArrows.forEach { (arrow, _) ->
            arrow.world.spawnParticle(
                particleType,
                arrow.location,
                particleAmount,
                particleSpread,
                particleSpread,
                particleSpread,
                particleSpread
            )
        }
    }

    fun reloadTurrets() {
        activeTurrets.clear()
        Bukkit.getWorld("world")?.entities?.forEach { entity ->
            if (entity is ArmorStand && entity.scoreboardTags.contains("Turret")) {
                activeTurrets.add(entity)
            }
        }
        activeArrows.clear()
        Bukkit.getWorld("world")?.entities?.forEach { entity ->
            if (entity is Arrow && entity.scoreboardTags.contains("TurretArrow")) {
                activeArrows[entity] = arrowLifeTime
            }
        }
    }

    private fun onInterval() {
        // check if turret can see player
        activeTurrets.forEach { turret ->
            Bukkit.getOnlinePlayers().forEach { player ->
                if (turret.hasLineOfSight(player) && player.isGliding && turret.location.distance(player.location) <= turretReach
                    && turret.location.x != player.location.x
                ) {
                    // shoot arrow in player direction
                    val arrow = turret.world.spawn(turret.eyeLocation, Arrow::class.java)
                    arrow.addScoreboardTag("TurretArrow")

                    val predictedLocation = predictLocation(turret.location, player)
                    arrow.velocity = predictedLocation.toVector().subtract(turret.location.toVector()).normalize()
                        .multiply(speedMultiplier)
                    arrow.isInvulnerable = true
                    arrow.setGravity(false)
                    arrow.damage = arrowDamage
                    arrow.isSilent = true
                    activeArrows[arrow] = arrowLifeTime

                    player.playSound(
                        turret.location,
                        Sound.ENTITY_BLAZE_SHOOT,
                        turret.location.distance(player.location).div(10).toFloat(),
                        0.6f
                    )
                }
            }
        }

        activeArrows.forEach { (arrow, lifeTime) ->
            if (lifeTime == 0) {
                arrowsToRemove.add(arrow)
            } else {
                activeArrows[arrow] = lifeTime - 1
            }
        }
        arrowsToRemove.forEach { arrow ->
            arrow.remove()
            activeArrows.remove(arrow)
        }
    }

    private fun predictLocation(turretLocation: Location, player: Player): Location {
        val playerLocation = player.location

        val timeToReach = turretLocation.distance(playerLocation) / speedMultiplier

        return playerLocation.add(player.velocity.multiply(timeToReach))
    }

    @EventHandler
    fun onArrowHit(event: ProjectileHitEvent) {
        if (event.entity.scoreboardTags.contains("TurretArrow")) {
            activeArrows.remove(event.entity)
            event.entity.remove()
        }
    }
}