package me.kehessen.icarus.combat

import me.kehessen.icarus.event.SAMDeployEvent
import me.kehessen.icarus.event.SAMExplodeEvent
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.Listener

class SAM(val target: Player, shooter: Player, var lifetime: Int, val yield: Float, val velocity: Float) : Listener {

    // TurretHandler gets initialized before MANPAD, so Team has been created
    private var sb = Bukkit.getScoreboardManager()!!.mainScoreboard
    private var missileTeam = sb.getTeam("MissileRedGlow")!!
    private var hit = false
    private var exploded = false
    // spawn missile 2 blocks in front of player
    var missile = shooter.world.spawnEntity(shooter.location.add(shooter.location.direction.normalize().multiply(3)), EntityType.FIREBALL)

    private var task: Int? = null

    init {
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("Icarus")!!)
        val event = SAMDeployEvent(shooter, target)
        Bukkit.getPluginManager().callEvent(event)
        
        missileTeam.addEntry(missile.uniqueId.toString())
        missile.isGlowing = true
        missile.isVisualFire = false

        task = Bukkit.getScheduler().scheduleSyncRepeatingTask(Bukkit.getPluginManager().getPlugin("Icarus")!!, {
            onInterval()
        }, 0, 1)
    }

    private fun onInterval() {
        if (missile.isDead) {
            explode()
            return
        }
        if (exploded)
            return
        missile.velocity = target.location.subtract(missile.location).toVector().normalize().multiply(velocity)
        if (missile.location.distance(target.location) < 3) {
            hit = true
            explode()
            return
        }
        if (lifetime <= 0) {
            explode()
            return
        }
        lifetime--
    }

    private fun explode() {
        if (exploded) return
        exploded = true
        missile.world.createExplosion(missile.location, yield)
        missile.world.spawnParticle(Particle.FLAME, missile.location, 800, 0.8, 0.8, 0.8, 0.5)
        missile.remove()
        Bukkit.getScheduler().cancelTask(task!!)
        val event = SAMExplodeEvent(target, hit)
        Bukkit.getPluginManager().callEvent(event)
    }
}