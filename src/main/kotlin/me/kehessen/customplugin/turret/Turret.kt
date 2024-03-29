package me.kehessen.customplugin.turret


import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

// shooting tasks and reach checks are done by the turret, particle handling and performance checks are done by the handler
// turrets are identified in data.json via their uuid
// menu options: pick up, refill, shotDelay, disable
@Suppress("RedundantSetter", "RedundantGetter", "DuplicatedCode")
class Turret(private val plugin: JavaPlugin) : Listener {
    public var isActive = true
    private var shotDelay: Long = 3
        get() = field
        set(value) {
            field = value
        }
    private var reach: Int = 500
        get() = field
        set(value) {
            field = value
        }
    private var damage: Double = 1.0
        get() = field
        set(value) {
            field = value
        }
    private var ammo: Int = 100
        get() = field
        set(value) {
            field = value
        }
    private val distanceCheckDelay: Long = 20

    // blocks per tick
    private val speedMultiplier: Float = 5f //config.getInt("Turret.arrow-speed-multiplier").toFloat()
        get() = field


    // ---options---
    public var burningArrow: Boolean = true
    public var silenced = false

    // ---backend---
    // time it takes to reach turret reach + 20 ticks
    private val arrowLifeTime = (reach / speedMultiplier / shotDelay + 20 / shotDelay).toInt()
    private var activeArrows = mutableMapOf<Arrow, Int>()
    private var onlinePlayers = Bukkit.getOnlinePlayers()
    private var targets = mutableListOf<Player>()
    private var aS: ArmorStand? = null
    private var shootTaskID: Int? = null
    private var reachCheckTaskID: Int? = null

    private var keyNames = arrayOf("ammo", "active", "damage", "reach", "shotDelay")


    fun spawn(location: Location, isVisible: Boolean = true): Turret {
        aS = location.world!!.spawn(location, ArmorStand::class.java)

        aS!!.setGravity(false)
        aS!!.isInvulnerable = true
        aS!!.customName = "§cTurret"
        aS!!.isCustomNameVisible = true
        aS!!.isVisible = isVisible
        aS!!.removeWhenFarAway = false

        aS!!.addScoreboardTag("Turret")


        updateSettings()

        return this
    }

    // THIS IS THE WHOLE FUCKING REASON I IMPLEMENTED THE TURRET CLASS AND I JUST REALIZED I CAN JUST USE THE ARMOR STANDS IN FUCKING TURRETHANDLER I HATE MY LIFE
    public fun updateSettings() {
        keyNames.forEach { keyName ->
            val key = NamespacedKey(plugin, keyName)
            if (aS!!.persistentDataContainer.has(key)) {
                when (keyName) {
                    "ammo" -> ammo = aS!!.persistentDataContainer.get(key, PersistentDataType.INTEGER)!!
                    "active" -> isActive = aS!!.persistentDataContainer.get(key, PersistentDataType.BOOLEAN)!!
                    "damage" -> damage = aS!!.persistentDataContainer.get(key, PersistentDataType.DOUBLE)!!
                    "reach" -> reach = aS!!.persistentDataContainer.get(key, PersistentDataType.INTEGER)!!
                    "shotDelay" -> shotDelay = aS!!.persistentDataContainer.get(key, PersistentDataType.LONG)!!
                }
                return@forEach
            }
            when (keyName) {
                "ammo" -> aS!!.persistentDataContainer.set(key, PersistentDataType.INTEGER, ammo)
                "active" -> aS!!.persistentDataContainer.set(key, PersistentDataType.BOOLEAN, isActive)
                "damage" -> aS!!.persistentDataContainer.set(key, PersistentDataType.DOUBLE, damage)
                "reach" -> aS!!.persistentDataContainer.set(key, PersistentDataType.INTEGER, reach)
                "shotDelay" -> aS!!.persistentDataContainer.set(key, PersistentDataType.LONG, shotDelay)
            }
        }
    }

    private fun reachCheck() {
        // add players in range to target list, start tasks if not already running

        onlinePlayers.forEach { player ->
            if (player.isGliding && aS!!.hasLineOfSight(player) && aS!!.location.distance(player.location) <= reach) {
                targets.add(player)
                if (shootTaskID == null) {
                    Bukkit.getLogger().info("Players in range, starting turret tasks")
                    startTasks()
                }
                return
            } else if (shootTaskID != null && targets.isEmpty()) {
                Bukkit.getLogger().info("No players in range, stopping turret tasks")
                stopTasks()
            }
            targets.remove(player)
        }

        // need to do this to prevent turret from shooting at disconnected player locations
        targets.forEach { target -> if (!target.isOnline) targets.remove(target) }
        // disable hit delay for targeted players
        onlinePlayers.forEach { player ->
            if (!targets.contains(player)) {
                // add hit delay if not targeted
                if (player.maximumNoDamageTicks != 20) {
                    player.maximumNoDamageTicks = 20
                    Bukkit.getLogger().info("Added hit delay to ${player.name}, player is not turret target")
                }
                // remove hit delay if targeted
            } else if (player.maximumNoDamageTicks != 0) {
                player.maximumNoDamageTicks = 0
                Bukkit.getLogger().info("Removed hit delay from ${player.name}, player is turret target")
            }
        }
    }

    private fun onInterval() {
        targets.forEach { player ->
            if (aS!!.location.distance(player.location) > reach || !player.isOnline) return@forEach
            // shoot arrow in player direction
            val arrow = aS!!.world.spawn(aS!!.eyeLocation, Arrow::class.java)
            arrow.addScoreboardTag("TurretArrow")

            val predictedLocation = predictLocation(aS!!.location, player)
            arrow.velocity = predictedLocation.toVector().subtract(aS!!.location.toVector()).normalize()
                .multiply(speedMultiplier)
//                arrow.isInvulnerable = true
            arrow.setGravity(false)
            arrow.damage = damage
            arrow.isSilent = true
            arrow.isVisualFire = burningArrow
            activeArrows[arrow] = arrowLifeTime
//                    player.sendHurtAnimation(0f)

            if (!silenced) {
                player.playSound(
                    aS!!.location,
                    Sound.ENTITY_BLAZE_SHOOT,
                    aS!!.location.distance(player.location).div(10).toFloat(),
                    0.6f
                )
                aS!!.world.playSound(
                    aS!!.location,
                    Sound.ENTITY_BLAZE_SHOOT,
                    1f,
                    0.6f
                )
            }

        }

        val arrowsToRemove = mutableListOf<Arrow>()
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
        playerLocation.y -= 1.5

        // adding some extra time since a lot of arrows fly behind the player
        val timeToReach = (turretLocation.distance(playerLocation) / speedMultiplier) + Random().nextFloat(0.5f, 0.8f)

        return playerLocation.add(player.velocity.multiply(timeToReach))
    }

    private fun startTasks() {
        shootTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            onInterval()
        }, 0, shotDelay)
        // particles
        reachCheckTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            reachCheck()
        }, 0, distanceCheckDelay)
    }

    private fun stopTasks() {
        Bukkit.getScheduler().cancelTask(shootTaskID!!)
        Bukkit.getScheduler().cancelTask(reachCheckTaskID!!)
        shootTaskID = null
        reachCheckTaskID = null
    }

}