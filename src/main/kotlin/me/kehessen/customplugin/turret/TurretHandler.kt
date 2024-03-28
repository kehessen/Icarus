package me.kehessen.customplugin.turret

import me.kehessen.customplugin.MenuHandler
import org.bukkit.*
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
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

@Suppress("unused", "DuplicatedCode")
class TurretHandler(private val plugin: JavaPlugin, config: FileConfiguration, private val menu: MenuHandler) :
    CommandExecutor,
    TabCompleter,
    Listener {

    // ---config---
    private var shotDelay: Long = 3 //config.getLong("Turret.shoot-delay")
    private val particleDelay: Long = 2 //config.getLong("Turret.particle-delay")
    private val particleAmount = 20 //config.getInt("Turret.particle-amount")
    private val particleType = Particle.FLAME
    private val particleSpread = 0.2 //config.getDouble("Turret.particle-spread")
    private val turretReach = 500 //config.getInt("Turret.reach")
    private val arrowDamage = 0.5 //config.getDouble("Turret.damage")

    // blocks per tick
    private val speedMultiplier: Float = 5f //config.getInt("Turret.arrow-speed-multiplier")

    // ticks
    private val distanceCheckDelay: Long = 20
    private val performanceCheckDelay: Long = 20 * 60


    // ---options---
    private var burningArrow: Boolean = true
    private var silenced = false


    // ---backend---
    // time it takes to reach turret reach + 20 ticks
    private val arrowLifeTime = (turretReach / speedMultiplier / shotDelay + 20 / shotDelay).toInt()
    private var activeTurrets = arrayListOf<ArmorStand>()
    private var activeArrows = hashMapOf<Arrow, Int>()
    private var onlinePlayers = Bukkit.getOnlinePlayers()
    private val targets = mutableSetOf<Player>()

    private var shootTaskID: Int? = null
    private var particleTaskID: Int? = null
    private var reachCheckTaskID: Int? = null
    private var performanceCheckTaskID: Int? = null

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
            onlinePlayers = Bukkit.getOnlinePlayers()
            sender.sendMessage("§aReloaded ${activeTurrets.size} turrets")
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
            stopReachCheckTask()
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

        if (args[0] == "targets") {
            sender.sendMessage("§aTargets: ${targets.joinToString { it.name }}")
            return true
        }

        return false
    }

    fun startReachCheckTask() {
        onlinePlayers = Bukkit.getOnlinePlayers()
        if (reachCheckTaskID != null) return
        reachCheckTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            reachCheck()
        }, 0, distanceCheckDelay)
        Bukkit.getLogger().info("Enabled turret reach checker")
    }

    private fun stopReachCheckTask() {
        Bukkit.getScheduler().cancelTask(reachCheckTaskID!!)
        reachCheckTaskID = null
        Bukkit.getLogger().info("Disabled turret reach checker since no players are online")
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
        // remove arrows later, so it looks better when exiting reach distance
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, {
            removeArrows()
        }, 40)
    }

    fun startPerformanceCheckTask() {
        performanceCheckTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            performanceChecks()
        }, 0, performanceCheckDelay)
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
        // add players in range to target list, start tasks if not already running
        activeTurrets.forEach first@{ turret ->
            onlinePlayers.forEach { player ->
                if (player.isGliding && turret.hasLineOfSight(player) && turret.location.distance(player.location) <= turretReach) {
                    targets.add(player)
                    if (shootTaskID == null) {
                        Bukkit.getLogger().info("Players in range, starting turret tasks")
                        startTasks()
                    }
                    return@first
                } else if (shootTaskID != null && targets.isEmpty()) {
                    Bukkit.getLogger().info("No players in range, stopping turret tasks")
                    stopTasks()
                }
                targets.remove(player)
            }
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

    // THIS SHOULD ALWAYS RUN, EVEN IF NO PLAYERS ARE ONLINE
    private fun performanceChecks() {
        if (onlinePlayers.isEmpty() && reachCheckTaskID != null) {
            Bukkit.getLogger().info("No players online, stopping tasks")
            stopReachCheckTask()
            if (shootTaskID != null) stopTasks()
        } else if (activeTurrets.isEmpty() && shootTaskID != null) {
            Bukkit.getLogger().info("No turrets found, stopping tasks")
            stopTasks()
            stopReachCheckTask()
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
        targets.forEach { player ->
            activeTurrets.forEach inner@{ turret ->
                if (turret.location.distance(player.location) > turretReach || !player.isOnline) return@inner
                // shoot arrow in player direction
                val arrow = turret.world.spawn(turret.eyeLocation, Arrow::class.java)
                arrow.addScoreboardTag("TurretArrow")

                val predictedLocation = predictLocation(turret.location, player)
                arrow.velocity = predictedLocation.toVector().subtract(turret.location.toVector()).normalize()
                    .multiply(speedMultiplier)
//                arrow.isInvulnerable = true
                arrow.setGravity(false)
                arrow.damage = arrowDamage
                arrow.isSilent = true
                arrow.isVisualFire = burningArrow
                activeArrows[arrow] = arrowLifeTime
//                    player.sendHurtAnimation(0f)

                if (!silenced) {
                    player.playSound(
                        turret.location,
                        Sound.ENTITY_BLAZE_SHOOT,
                        turret.location.distance(player.location).div(10).toFloat(),
                        0.6f
                    )
                    turret.world.playSound(
                        turret.location,
                        Sound.ENTITY_BLAZE_SHOOT,
                        1f,
                        0.6f
                    )
                }

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

//        // particle shooting if i ever want to use it
//        turret.world.spawnParticle(
//            Particle.FLAME, turret.eyeLocation, 0,
//            (player.eyeLocation.x - turret.eyeLocation.x) / 2,
//            (player.eyeLocation.y - turret.eyeLocation.y) / 2,
//            (player.eyeLocation.z - turret.eyeLocation.z) / 2,
//        )
        }
    }


    private fun removeArrows() {
        activeArrows.forEach { (arrow, _) ->
            arrow.remove()
        }
        activeArrows.clear()
    }

    private fun predictLocation(turretLocation: Location, player: Player): Location {
        val playerLocation = player.location
        playerLocation.y -= 1.5

        // adding some extra time since a lot of arrows fly behind the player
        val timeToReach = (turretLocation.distance(playerLocation) / speedMultiplier) + Random().nextFloat(0.5f, 0.8f)

        return playerLocation.add(player.velocity.multiply(timeToReach))
    }

    @EventHandler
    private fun onArrowHit(event: EntityDamageByEntityEvent) {
        if (event.damager.scoreboardTags.contains("TurretArrow") && event.entity is Player) {
            activeArrows.remove(event.entity)
            event.damager.remove()
        }
    }

    @EventHandler
    private fun onDestroy(event: EntityDeathEvent) {
        if (event.entity.scoreboardTags.contains("Turret")) {
            activeTurrets.remove(event.entity)
        }
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        if (reachCheckTaskID == null)
            Bukkit.getLogger().info("Starting reach check task")
        startReachCheckTask()
    }

    @EventHandler
    private fun onArmorStandHit(event: EntityDamageByEntityEvent) {
        if (event.entity.scoreboardTags.contains("Turret")) {
            event.isCancelled = true
        }
    }

    @EventHandler
    private fun onRightClick(event: PlayerInteractEntityEvent) {
        if (event.rightClicked.scoreboardTags.contains("Turret")) {
            val inv = menu.createInventory(3, "Turret")
            menu.createItem(Material.GREEN_WOOL, inv, 11, "Active", "Turret is active. Click to deactivate")
            menu.createItem(Material.RED_WOOL, inv, 13, "Ammo", "Turret is inactive. Click to activate")
        }
    }

    override fun onTabComplete(
        p0: CommandSender, p1: Command, p2: String, p3: Array<out String>
    ): MutableList<String> {
        if (p3.size == 1) {
            return mutableListOf("reload", "remove", "spawn", "silence", "burningArrow", "shotDelay", "targets")
        }
        return mutableListOf("")
    }
}