package me.kehessen.icarus.combat

import me.kehessen.icarus.event.TurretOutOfAmmoEvent
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scoreboard.Team
import java.util.*

class Turret(val armorStand: ArmorStand) : Listener {
    private val plugin = Bukkit.getPluginManager().getPlugin("Icarus")!!
    private val config = plugin.config

    private val enabled = config.getBoolean("Turret.enable")
    private val particleDelay: Long = config.getLong("Turret.particle-delay")
    private val particleAmount = config.getInt("Turret.particle-amount")
    private val particleSpread = config.getDouble("Turret.particle-spread")
    private val arrowDamage = config.getDouble("Turret.damage")
    private var defaultShotDelay: Long = config.getLong("Turret.shot-delay")
    private val silenced: Boolean = config.getBoolean("Turret.silenced")
    private val burningArrow: Boolean = config.getBoolean("Turret.burning-arrow")
    private val glowingArrow: Boolean = config.getBoolean("Turret.glowing-arrow")
    internal val reach = config.getInt("Turret.reach")
    private val speedMultiplier: Float = config.getInt("Turret.arrow-speed-multiplier").toFloat()

    // time it takes to reach turret reach + 20 ticks
    private val arrowLifeTime = (reach / speedMultiplier + 20).toInt()

    private val ammoKey = NamespacedKey(plugin, "ammo")
    private val activeKey = NamespacedKey(plugin, "active")
    private val damageKey = NamespacedKey(plugin, "damage")
    private val reachKey = NamespacedKey(plugin, "reach")
    private val shotDelayKey = NamespacedKey(plugin, "shot-delay")

    var ammo = 20
    var active = true
        set(value) {
            field = value
            armorStand.persistentDataContainer.set(activeKey, PersistentDataType.BOOLEAN, value)
            if (value) {
                startArrowTask()
                startParticleTask()
                armorStand.customName = "§cTurret"
            } else {
                stopArrowTask()
                stopParticleTask()
                armorStand.customName = "§cTurret (inactive)"
                target = null
            }
        }

    var speed = defaultShotDelay
    val pos
        get() = armorStand.location
    var name: String?
        get() = armorStand.customName
        set(value) {
            armorStand.customName = value
        }
    val uuid: UUID
        get() {
            return armorStand.uniqueId
        }

    var target: Player? = null

    private val ammoSaveThreshold = 20
    private var shotsFired = 0

    private var arrowTeam: Team = Bukkit.getScoreboardManager()!!.mainScoreboard.getTeam("MissileRedGlow")!!

    data class TimedArrow(val arrow: Arrow, var ticksRemaining: Int)

    private val activeArrows = mutableListOf<TimedArrow>()

    private var shootingTask: Int? = null
    private var particleTask: Int? = null


    init {
        if (armorStand.persistentDataContainer.get(ammoKey, PersistentDataType.INTEGER) != null) {
            this.ammo = armorStand.persistentDataContainer.get(ammoKey, PersistentDataType.INTEGER)!!
        } else {
            armorStand.persistentDataContainer.set(ammoKey, PersistentDataType.INTEGER, ammo)
        }

        if (armorStand.persistentDataContainer.get(activeKey, PersistentDataType.BOOLEAN) != null) {
            this.active = armorStand.persistentDataContainer.get(activeKey, PersistentDataType.BOOLEAN)!!
        } else {
            armorStand.persistentDataContainer.set(activeKey, PersistentDataType.BOOLEAN, active)
        }

        if (armorStand.persistentDataContainer.get(shotDelayKey, PersistentDataType.LONG) != null) {
            this.speed = armorStand.persistentDataContainer.get(shotDelayKey, PersistentDataType.LONG)!!
        } else {
            armorStand.persistentDataContainer.set(shotDelayKey, PersistentDataType.LONG, defaultShotDelay)

        }

        armorStand.persistentDataContainer.set(damageKey, PersistentDataType.DOUBLE, arrowDamage)
        armorStand.persistentDataContainer.set(reachKey, PersistentDataType.INTEGER, reach)
    }

    internal fun shoot() {
        if (!enabled) {
            Bukkit.getLogger().warning("[Icarus] Turret shooting called but turret is disabled")
            return
        }
        if (!active) {
            Bukkit.getLogger().warning("[Icarus] Turret shooting called but turret is inactive")
            return
        }
        if (ammo <= 0) {
            Bukkit.getLogger().warning("[Icarus] Turret shooting called but turret is out of ammo")
            return
        }
        if (target == null) {
            Bukkit.getLogger().warning("[Icarus] Turret target is null")
        }

        spawnArrow(target!!)

        if (shootingTask == null) {
            startArrowTask()
            startParticleTask()
        }


        ammo--
        shotsFired++
        updateAmmo()

        playSound(target)
    }

    private fun spawnArrow(player: Player) {
        val arrow = armorStand.world.spawn(armorStand.eyeLocation, Arrow::class.java)
        arrow.addScoreboardTag(TURRET_ARROW_TAG)

        val predictedLocation = predictLocation(armorStand.location, player)
        arrow.velocity = predictedLocation.toVector().subtract(armorStand.location.toVector()).normalize()
            .multiply(speedMultiplier)
        arrow.isInvulnerable = true
        arrow.setGravity(false)
        arrow.damage = arrowDamage
        arrow.isSilent = true
        arrow.isVisualFire = burningArrow
        arrowTeam.addEntry(arrow.uniqueId.toString())
        arrow.isGlowing = glowingArrow
        activeArrows.add(TimedArrow(arrow, arrowLifeTime))
        arrow.shooter = armorStand
    }

    private fun startArrowTask() {
        if (shootingTask != null) return
        shootingTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            updateArrows()
            if (activeArrows.isEmpty())
                stopArrowTask()
        }, 0, 1)
    }

    private fun stopArrowTask() {
        shootingTask?.let { Bukkit.getScheduler().cancelTask(it) }
    }

    private fun updateArrows() {
        activeArrows.removeIf { it.ticksRemaining <= 0 }
        activeArrows.forEach { it.ticksRemaining-- }
    }

    private fun updateAmmo() {
        if (shotsFired >= ammoSaveThreshold) {
            armorStand.persistentDataContainer.set(ammoKey, PersistentDataType.INTEGER, ammo)
            shotsFired = 0
        }
        if (ammo <= 0) {
            active = false
            ammo = 0
            armorStand.persistentDataContainer.set(activeKey, PersistentDataType.BOOLEAN, active)
            armorStand.persistentDataContainer.set(ammoKey, PersistentDataType.INTEGER, 0)
            Bukkit.getPluginManager().callEvent(TurretOutOfAmmoEvent(this))
        }
    }

    private fun playSound(player: Player?) {
        if (!silenced) {
            player?.playSound(
                armorStand.location,
                Sound.ENTITY_BLAZE_SHOOT,
                armorStand.location.distance(player.location).div(10).toFloat(),
                0.6f
            )
            armorStand.world.playSound(
                armorStand.location, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f
            )
        }
    }

    private fun startParticleTask() {
        if (particleTask != null) return
        particleTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            if (activeArrows.isEmpty())
                stopParticleTask()

            spawnArrowParticles()
        }, 0, particleDelay)
    }

    private fun stopParticleTask() {
        particleTask?.let { Bukkit.getScheduler().cancelTask(it) }
    }

    private fun spawnArrowParticles() {
        activeArrows.forEach { (arrow, _) ->
            arrow.world.spawnParticle(
                Particle.FLAME,
                arrow.location,
                particleAmount,
                particleSpread,
                particleSpread,
                particleSpread,
                particleSpread
            )
        }
    }

    fun nearbyPlayers(radius: Double): List<Player> {
        return armorStand.world.getNearbyEntities(
            armorStand.location, radius, radius, radius
        ).filterIsInstance<Player>()
    }

    fun hasLineOfSight(player: Player): Boolean {
        return armorStand.hasLineOfSight(player)
    }

    fun inReach(player: Player?, reach: Double): Boolean {
        if (player == null) return false
        return armorStand.location.distanceSquared(player.location) <= reach * reach
    }

    private fun predictLocation(turretLocation: Location, player: Player): Location {
        val playerLocation = player.location.clone()
        playerLocation.y -= 1.5

        // adding some extra time since a lot of arrows fly behind the player
        val timeToReach = (turretLocation.distance(playerLocation) / speedMultiplier) + Random().nextFloat(0.3f, 1.5f)

        return playerLocation.add(player.velocity.multiply(timeToReach))
    }
}