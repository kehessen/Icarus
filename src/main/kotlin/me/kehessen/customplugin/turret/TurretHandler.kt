package me.kehessen.customplugin.turret

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused", "DuplicatedCode")
class TurretHandler(private val plugin: JavaPlugin) : CommandExecutor {

    private val shootDelay = 3L
    private val particleDelay = 0L
    private val particleAmount = 15
    private val particleType = Particle.FLAME
    private val particleSpread = 0.1

    private var activeTurrets = arrayListOf<ArmorStand>()

    private val turretReach = 500
    private val damagePerSecond = 0.5
    private val speedMultiplier = 5

    private var activeArrows = hashMapOf<Arrow, Int>()
    private val arrowLifeTime = 20 / shootDelay.toInt() * 3

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
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
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
        }, 0, particleDelay)
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
                // + player.isGliding, just removed for testing || turret x can't be player x since it causes exceptions
                if (turret.hasLineOfSight(player) && turret.location.distance(player.location) <= turretReach
                    && turret.location.x != player.location.x
                ) {

                    // might use player location prediction

                    // shoot arrow in player direction
                    val arrow = turret.world.spawn(turret.eyeLocation, Arrow::class.java)
                    arrow.addScoreboardTag("TurretArrow")
                    arrow.velocity = player.location.toVector().subtract(turret.location.toVector()).normalize()
                        .multiply(speedMultiplier)
                    arrow.isInvulnerable = true
                    arrow.setGravity(false)
                    arrow.damage = 0.0
                    arrow.isSilent = true
                    activeArrows[arrow] = arrowLifeTime
                }
            }
        }


        val arrowsToRemove = mutableListOf<Arrow>()
        activeArrows.forEach { (arrow, lifeTime) ->
            arrow.world.spawnParticle(Particle.FLAME, arrow.location, 20, 0.1, 0.1, 0.1, 0.1)
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
}