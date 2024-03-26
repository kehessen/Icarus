package me.kehessen.customplugin.turret

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.java.JavaPlugin


// might delete later since it's not really useful
@Suppress("unused", "DuplicatedCode")
class ParticleTurretHandler(private val plugin: JavaPlugin) : CommandExecutor, Listener {

    private val shotDelay = 1L

    private var activeTurrets = arrayListOf<ArmorStand>()

    private val turretReach = 500

    private val speedMultiplier: Float = 2f

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // /removeallturrets
        if (command.name == "removeallparticleturrets") {
            activeTurrets.forEach { turret ->
                sender.sendMessage("§a Removed turret with ID ${turret.entityId}")
                turret.remove()
            }
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

        armorStand.addScoreboardTag("Particleturret")
        activeTurrets.add(armorStand)

        return true
    }


    fun startTask() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            onInterval()
        }, 0, shotDelay)
    }

    fun reloadTurrets() {
        activeTurrets.clear()
        Bukkit.getWorld("world")?.entities?.forEach { entity ->
            if (entity is ArmorStand && entity.scoreboardTags.contains("Particleturret")) {
                activeTurrets.add(entity)
            }
        }
    }

    @EventHandler
    fun playerMove(event: PlayerMoveEvent) {
        if (event.player.isGliding)
            event.player.spawnParticle(Particle.WAX_OFF, event.player.location, 2, 0.15, 0.15, 0.15, 0.0)//Falling lava
    }

    private fun onInterval() {
        // check if turret can see player
        activeTurrets.forEach { turret ->
            Bukkit.getOnlinePlayers().forEach { player ->
                // + player.isGliding, just removed for testing || turret x can't be player x since it causes exceptions
                if (turret.location.distance(player.location) <= turretReach && turret.hasLineOfSight(player)
                    && turret.location.x != player.location.x
                ) {
                    Bukkit.getWorld("world")!!.spawnParticle(
                        Particle.FLAME, turret.eyeLocation, 0,
                        (player.eyeLocation.x - turret.eyeLocation.x) / 2,
                        (player.eyeLocation.y - turret.eyeLocation.y) / 2,
                        (player.eyeLocation.z - turret.eyeLocation.z) / 2,
                    )
                }
            }
        }
    }
}