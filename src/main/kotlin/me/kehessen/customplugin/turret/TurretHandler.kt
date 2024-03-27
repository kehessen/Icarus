package me.kehessen.customplugin.turret

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused", "DuplicatedCode")
class TurretHandler(private val plugin: JavaPlugin, config: FileConfiguration) : CommandExecutor, TabCompleter,
    Listener {

    private var shotDelay: Long = 3 //config.getLong("Turret.shoot-delay")
    private val particleDelay: Long = 2 //config.getLong("Turret.particle-delay")
    private val particleAmount = 20 //config.getInt("Turret.particle-amount")
    private val particleType = Particle.FLAME
    private val particleSpread = 0.2 //config.getDouble("Turret.particle-spread")

    private var activeTurrets = arrayListOf<ArmorStand>()

    private val turretReach = 500 //config.getInt("Turret.reach")
    private val arrowDamage = 0.5 //config.getDouble("Turret.damage")
    private var burningArrow: Boolean = true

    // blocks per tick
    private val speedMultiplier: Float = 5f //config.getInt("Turret.arrow-speed-multiplier")

    private var activeArrows = hashMapOf<Arrow, Int>()
    private val arrowLifeTime = 20 / shotDelay.toInt() * 3

    // ticks
    private val distanceCheckDelay: Long = 20

    private var silenced = false

    private val onlinePlayers = Bukkit.getOnlinePlayers()
    private val targets = mutableSetOf<Player>()
    private val arrowsToRemove = mutableListOf<Arrow>()

    private var shootTaskID: Int? = null
    private var particleTaskID: Int? = null
    private var reachCheckTaskID: Int? = null

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args[0] == "shotDelay") {
            val newDelay = args[1].toIntOrNull()
            if (args.size != 2 || newDelay == null) {
                sender.sendMessage("§cInvalid arguments")
                return false
            }
            if (newDelay < 1 || newDelay > 100) {
                sender.sendMessage("§cInvalid number")
                return false
            }
            shotDelay = newDelay.toLong()
            if (shootTaskID != null) {
                stopTasks()
                startTasks()
            }
            sender.sendMessage("§aTurret shot delay set to $newDelay ticks")
            return true
        }
        if (args.size != 1) {
            sender.sendMessage("§cInvalid arguments")
            return false
        }
        if (args[0] == "reload") {
            reloadTurrets()
            return true
        }

        if (args[0] == "burningArrow") {
            burningArrow = !burningArrow
            if (burningArrow) {
                sender.sendMessage("§aTurrets now shoot burning arrows")
            } else {
                sender.sendMessage("§aTurrets now shoot normal arrows")
            }
            return true
        }

        if (args[0] == "remove") {
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
            Bukkit.getScheduler().cancelTask(reachCheckTaskID!!)
            Bukkit.getLogger().info("Disabled turret reach checker since all turrets were removed")
            return true
        }

        if (args[0] == "spawn") {
            if (Bukkit.getPlayer(sender.name) !is Player) return false
            startReachCheckTask()
            val player = Bukkit.getPlayer(sender.name)
            val armorStand = player!!.world.spawn(player.location, ArmorStand::class.java)
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

        if (args[0] == "silence") {
            silenced = !silenced
            if (silenced) {
                sender.sendMessage("§aTurrets silenced")
            } else {
                sender.sendMessage("§aTurrets unsilenced")
            }
            return true
        }

        return false
    }

    fun startReachCheckTask() {
        if (reachCheckTaskID != null || activeTurrets.isEmpty()) return
        reachCheckTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            reachCheck()
        }, 0, distanceCheckDelay)
    }

    private fun startTasks() {
        shootTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            onInterval()
        }, 0, shotDelay)
        // particles
        particleTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            spawnParticles()
        }, 0, particleDelay)
    }

    private fun stopTasks() {
        Bukkit.getScheduler().cancelTask(shootTaskID!!)
        Bukkit.getScheduler().cancelTask(particleTaskID!!)
        shootTaskID = null
        particleTaskID = null
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

    private fun reachCheck() {
        if (activeTurrets.isEmpty()) {
            if (shootTaskID != null) {
                Bukkit.getLogger().info("No turrets found, stopping tasks")
                stopTasks()
                return
            }
            return
        }
        activeTurrets.forEach { turret ->
            onlinePlayers.forEach { player ->
                if (turret.location.distance(player.location) <= turretReach && turret.hasLineOfSight(player) && player.isGliding) {
                    if (shootTaskID == null) {
                        Bukkit.getLogger().info("Players in range, starting turret tasks")
                        startTasks()
                        return
                    } else {
                        return
                    }
                }
            }
        }
        if (shootTaskID == null) return
        Bukkit.getLogger().info("No players in range, stopping turret tasks")
        stopTasks()
        return
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
                    targets.add(player)
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
                    arrow.isVisualFire = burningArrow
                    activeArrows[arrow] = arrowLifeTime

                    if (silenced) return
                    player.playSound(
                        turret.location,
                        Sound.ENTITY_BLAZE_SHOOT,
                        turret.location.distance(player.location).div(10).toFloat(),
                        0.6f
                    )
                } else {
                    targets.remove(player)
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

    override fun onTabComplete(
        p0: CommandSender, p1: Command, p2: String, p3: Array<out String>
    ): MutableList<String> {
        if (p3.size == 1) {
            return mutableListOf("reload", "remove", "spawn", "silence", "burningArrow", "shotDelay")
        }
        return mutableListOf("")
    }
}