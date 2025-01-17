package me.kehessen.icarus.combat

import me.kehessen.icarus.event.FlareDeployEvent
import me.kehessen.icarus.util.CustomItem
import me.kehessen.icarus.util.InvHolder
import me.kehessen.icarus.util.MenuHandler
import org.bukkit.*
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@Suppress("unused")
class TurretHandler(private val plugin: JavaPlugin, config: FileConfiguration, private val menu: MenuHandler) :
    CommandExecutor, TabCompleter, Listener {

    // ---config---
    private var enabled = config.getBoolean("Turret.enable")
    private var checkTeam: Boolean = config.getBoolean("Turret.check-team")
    private var shotDelay: Long = config.getLong("Turret.shot-delay")
    private val particleDelay: Long = config.getLong("Turret.particle-delay")
    private val particleAmount = config.getInt("Turret.particle-amount")
    private val particleType = Particle.FLAME
    private val particleSpread = config.getDouble("Turret.particle-spread")
    private var turretReach = config.getInt("Turret.reach")
    private var arrowDamage = config.getDouble("Turret.damage")
    private var initialAmmo: Int = 0

    // blocks per tick
    private val speedMultiplier: Float = config.getInt("Turret.arrow-speed-multiplier").toFloat()

    // ticks
    private val distanceCheckDelay: Long = config.getLong("Turret.distance-check-delay")
    private val performanceCheckDelay: Long = config.getLong("Turret.performance-check-delay")


    // ---options---
    private var burningArrow: Boolean = config.getBoolean("Turret.burning-arrow")
    private var glowingArrow: Boolean = config.getBoolean("Turret.glowing-arrow")
    private var silenced: Boolean = config.getBoolean("Turret.silenced")


    // ---backend---
    // time it takes to reach turret reach + 20 ticks
    private val arrowLifeTime = (turretReach / speedMultiplier + 20).toInt()
    private var turrets = mutableSetOf<ArmorStand>()
    private var activeTurrets = mutableSetOf<ArmorStand>()
    private var inactiveTurrets = mutableSetOf<ArmorStand>()
    private var shootingTurrets = mutableSetOf<ArmorStand>()
    private var activeArrows = hashMapOf<Arrow, Int>()
    private val targets = mutableSetOf<Player>()
    private val lockedOn = hashMapOf<ArmorStand, Player>()
    private val immunePlayers = mutableSetOf<Player>()
    // activeturrets: shootingturrets + non-shootingturrets
    // turrets: activeturrets + inactiveturrets


    // shot delay, key: turret, value: shot delay, delay is 1-5 ticks
    private var minTurretSpeed = 1L
    private var maxTurretSpeed = 5L
    private var turretSpeeds = hashMapOf<ArmorStand, Long>()

    private lateinit var sb: Scoreboard

    // key: inventory holder, value: turret
    private var openInvs = hashMapOf<InventoryHolder, ArmorStand>()
    private var playersInInv = hashMapOf<Player, ArmorStand>()

    private var keyNames = arrayOf("ammo", "active", "damage", "reach", "shotDelay")
    private val ammoKey = NamespacedKey(plugin, "ammo")
    private val activeKey = NamespacedKey(plugin, "active")
    private val damageKey = NamespacedKey(plugin, "damage")
    private val reachKey = NamespacedKey(plugin, "reach")
    private val shotDelayKey = NamespacedKey(plugin, "shotDelay")

    private val ammoDisplayMaterial: Material = Material.ARROW
    private val activeDisplayMaterial: Material = Material.GREEN_WOOL
    private val inactiveDisplayMaterial: Material = Material.RED_WOOL
    private val shotDelayMaterial: Material = Material.CLOCK
    private val pickUpMaterial: Material = Material.BARRIER
    private val invRows = 4
    private val ammoPutSlot = 22


    // custom sound from resource pack
    private val customSound = "minecraft:missilelock"

    // crafting recipe:
    // 1 custom ender pearl from killing an enderman in the overworld without fortune,
    // 1 nether star, 1 crossbow, 1 blaze rod
    // 3 obsidian as base
    internal val customEnderPearl =
        CustomItem(Material.ENDER_PEARL, "§r§lEnder Pearl", "§r§7Can be used to craft turrets")
    internal val turretItem = CustomItem(Material.ARMOR_STAND, "§r§lTurret", "§r§7Right click to place")
    internal val flares = CustomItem(
        Material.BLAZE_ROD, "§r§lFlares", "§r§7Right click to use", "§r§7Can be used to distract turrets"
    )


    // multiple tasks for different shot delays, up to 5 -> better than creating a new class 
    private var shootTaskIDs: MutableSet<Int> = mutableSetOf()
    private var particleTaskID: Int? = null
    private var reachCheckTaskID: Int? = null
    private var performanceCheckTaskID: Int? = null

    private lateinit var arrowTeam: Team

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        args[0].lowercase()

        // needs to be after commands with multiple arguments
        if (args.size != 1) {
            sender.sendMessage("§cInvalid arguments")
            return true
        }
        when (args[0]) {
            "removeall" -> {
                turrets.forEach { turret ->
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
                inactiveTurrets.clear()
                turrets.clear()
                stopReachCheckTask()
                Bukkit.getLogger().info("[Icarus] Disabled turret reach checker since all turrets were removed")
                return true
            }
        }
        sender.sendMessage("§cInvalid arguments")
        return true
    }

    private fun reachCheck() {
        // add players in range to target list, start tasks if not already running
        val shooter = hashMapOf<ArmorStand, Player>()
        val isShooting = mutableSetOf<ArmorStand>()
        activeTurrets.forEach outer@{ turret ->
            // skip all checks if turret already has a target
            if (lockedOn.containsKey(turret)) {
                val player = lockedOn[turret]!!
                if (player.isGliding && player.isOnline && turret.hasLineOfSight(player) && turret.location.distanceSquared(
                        player.location
                    ) < turretReach * turretReach
                ) {
                    isShooting.add(turret)
                    shooter[turret] = player
                    return@outer
                } else {
                    lockedOn.remove(turret)
                }
            }
            turret.getNearbyEntities(turretReach.toDouble(), turretReach.toDouble(), turretReach.toDouble())
                .forEach { player ->
                    if (player !is Player) return@forEach
                    if (checkTeam && sb.getEntryTeam(player.name) == sb.getEntryTeam(turret.uniqueId.toString()))
                        return@forEach
                    // using distanceSquared for performance
                    if (player.isGliding && turret.hasLineOfSight(player) && turret.location.distanceSquared(player.location) < turretReach * turretReach) {
                        shooter[turret] = player
                        isShooting.add(turret)
                        if (!targets.contains(player)) {
                            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, {
                                if (player.isGliding && turret.hasLineOfSight(player) && turret.location.distanceSquared(
                                        player.location
                                    ) < turretReach * turretReach
                                ) {
                                    shootingTurrets.add(turret)
                                    targets.add(player)
                                    lockedOn[turret] = player
                                    if (shootTaskIDs.isEmpty()) {
                                        Bukkit.getLogger().info("[Icarus] Players in range, starting turret tasks")
                                        startTasks()
                                    }
                                }
                            }, 19)
                            player.playSound(turret.location, customSound, 100f, 1f)
                            return@outer
                        }
                    } else {
                        isShooting.remove(turret)
                    }
                }
        }

        shootingTurrets.removeIf { turret -> !isShooting.contains(turret) }

        targets.removeIf { player -> !shooter.containsValue(player) }

        if (shootTaskIDs.isNotEmpty() && targets.isEmpty()) {
            stopTasks()
        }
        val disabled = mutableSetOf<ArmorStand>()

        // doing ammo calculation here because it's faster than writing to NBT every single shot
        // disable turrets when out of ammo
        shootingTurrets.forEach { turret ->
            val ammo = turret.persistentDataContainer.get(ammoKey, PersistentDataType.INTEGER)!!
            val speed = turretSpeeds[turret]!!
            turret.persistentDataContainer.set(ammoKey, PersistentDataType.INTEGER, ammo - (20 / speed).toInt())
            if (ammo < 1) {
                activeTurrets.remove(turret)
                inactiveTurrets.add(turret)
                disabled.add(turret)
                turret.persistentDataContainer.set(activeKey, PersistentDataType.BOOLEAN, false)
                turret.persistentDataContainer.set(ammoKey, PersistentDataType.INTEGER, 0)
                Bukkit.getLogger().info("[Icarus] Turret with ID ${turret.uniqueId} out of ammo, deactivated")
            }
        }
        if (disabled.isNotEmpty()) {
            disabled.forEach { turret ->
                shootingTurrets.remove(turret)
            }
            stopTasks()
            startTasks()
        }

        // disable hit delay for targeted players
        Bukkit.getOnlinePlayers().forEach { player ->
            if (!targets.contains(player)) {
                // add hit delay if not targeted
                if (player.maximumNoDamageTicks != 20) {
                    player.maximumNoDamageTicks = 20
                    Bukkit.getLogger().info("[Icarus] Added hit delay to ${player.name}, player is not turret target")
                }
                // remove hit delay if targeted
            } else if (player.maximumNoDamageTicks != 0) {
                player.maximumNoDamageTicks = 0
                Bukkit.getLogger().info("[Icarus] Removed hit delay from ${player.name}, player is turret target")
            }
        }
    }

    private fun onInterval(turrets: Set<ArmorStand>) {
        targets.forEach { player ->
            turrets.forEach { turret ->
                if (turret.location.distance(player.location) <= turretReach && player.isOnline && !immunePlayers.contains(
                        player
                    )
                ) {
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
                    arrowTeam.addEntry(arrow.uniqueId.toString())
                    arrow.isGlowing = true
                    activeArrows[arrow] = arrowLifeTime
                    arrow.shooter = turret

                    if (!silenced) {
                        player.playSound(
                            turret.location,
                            Sound.ENTITY_BLAZE_SHOOT,
                            turret.location.distance(player.location).div(10).toFloat(),
                            0.6f
                        )
                        turret.world.playSound(
                            turret.location, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f
                        )
                    }
                }

            }
        }

        val arrowsToRemove = mutableListOf<Arrow>()
        activeArrows.forEach { (arrow, lifeTime) ->
            if (lifeTime <= 0) {
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

    // this should always run, even if no players are online
    private fun performanceChecks() {
        if (Bukkit.getOnlinePlayers().isEmpty() && reachCheckTaskID != null) {
            stopReachCheckTask()
            if (shootTaskIDs.isNotEmpty()) stopTasks()
        } else if (activeTurrets.isEmpty() && shootTaskIDs.isNotEmpty()) {
            Bukkit.getLogger().info("[Icarus] No turrets found, stopping tasks")
            stopTasks()
            stopReachCheckTask()
        }
        activeArrows.clear()
        Bukkit.getWorld("world")?.entities?.forEach { entity ->
            if (entity is Arrow && entity.scoreboardTags.contains("TurretArrow")) {
                activeArrows[entity] = arrowLifeTime
            }
        }
    }

    private fun spawnTurret(player: Player, location: Location) {
        val armorStand = location.world!!.spawn(location, ArmorStand::class.java)
        armorStand.setGravity(false)
        armorStand.customName = "§cTurret"
        armorStand.isCustomNameVisible = true
        armorStand.isVisible = true
        armorStand.removeWhenFarAway = false

        armorStand.addScoreboardTag("Turret")

        sb.getEntryTeam(player.name)!!.addEntry(armorStand.uniqueId.toString())

        armorStand.persistentDataContainer.set(ammoKey, PersistentDataType.INTEGER, initialAmmo)
        armorStand.persistentDataContainer.set(activeKey, PersistentDataType.BOOLEAN, true)
        armorStand.persistentDataContainer.set(damageKey, PersistentDataType.DOUBLE, arrowDamage)
        armorStand.persistentDataContainer.set(reachKey, PersistentDataType.INTEGER, turretReach)
        armorStand.persistentDataContainer.set(shotDelayKey, PersistentDataType.LONG, shotDelay)
        turretSpeeds[armorStand] = shotDelay

        turrets.add(armorStand)
        activeTurrets.add(armorStand)
    }

    fun start() {
        Bukkit.getPluginCommand("turret")?.setExecutor(this)
        Bukkit.getPluginCommand("turret")?.tabCompleter = this
        if (!enabled) return
        Bukkit.getPluginManager().registerEvents(this, plugin)
        sb = Bukkit.getScoreboardManager()!!.mainScoreboard
        if (sb.getTeam("MissileRedGlow") == null) {
            arrowTeam = sb.registerNewTeam("MissileRedGlow")
            arrowTeam.color = ChatColor.RED
        } else arrowTeam = sb.getTeam("MissileRedGlow")!!
        reloadTurrets()
        startPerformanceCheckTask()
        startReachCheckTask()
        addRecipes()

        val activationRange =
            Bukkit.getServer().spigot().config.getInt("world-settings.default.entity-activation-range.misc")
        if (activationRange < turretReach) {
            Bukkit.getLogger()
                .warning("[Icarus] Entity activation range is set to $activationRange, this may cause issues with turrets shooting correctly")
            Bukkit.getLogger()
                .warning("[Icarus] Setting entity activation range to $turretReach...")
            Bukkit.getServer().spigot().config.set("world-settings.default.entity-activation-range.misc", turretReach)
        }
    }

    fun disableAllTurrets() {
        turrets.forEach { turret ->
            turret.persistentDataContainer.set(NamespacedKey(plugin, "active"), PersistentDataType.BOOLEAN, false)
            inactiveTurrets.add(turret)
        }
        activeTurrets.clear()
    }

    fun enableAllTurrets() {
        turrets.forEach { turret ->
            turret.persistentDataContainer.set(NamespacedKey(plugin, "active"), PersistentDataType.BOOLEAN, true)
            activeTurrets.add(turret)
        }
        inactiveTurrets.clear()
    }

    private fun addRecipes() {
        var recipe = ShapedRecipe(NamespacedKey(plugin, "turret"), turretItem)
        recipe.shape(" P ", "NCB", "OOO")
        recipe.setIngredient('P', RecipeChoice.ExactChoice(customEnderPearl))
        recipe.setIngredient('N', Material.NETHER_STAR)
        recipe.setIngredient('C', Material.CROSSBOW)
        recipe.setIngredient('B', Material.BLAZE_ROD)
        recipe.setIngredient('O', Material.OBSIDIAN)
        Bukkit.addRecipe(recipe)

        recipe = ShapedRecipe(NamespacedKey(plugin, "flares"), flares)
        recipe.shape(" B ", " R ", " I ")
        recipe.setIngredient('B', Material.BLAZE_ROD)
        recipe.setIngredient('R', Material.REDSTONE_BLOCK)
        recipe.setIngredient('I', Material.IRON_INGOT)
        Bukkit.addRecipe(recipe)
    }

    private fun startTasks() {
        val tasksToStart = mutableSetOf<Long>()
        activeTurrets.forEach { turret ->
            if (turretSpeeds[turret] == null) {
                val tspeed = turret.persistentDataContainer.get(shotDelayKey, PersistentDataType.LONG)
                if (tspeed != null)
                    turretSpeeds[turret] = tspeed
                else {
                    turretSpeeds[turret] = shotDelay
                    turret.persistentDataContainer.set(shotDelayKey, PersistentDataType.LONG, shotDelay)
                }
            }
            tasksToStart.add(turretSpeeds[turret]!!)
        }

        // start tasks for each shot delay currently used
        tasksToStart.forEach { delay ->
            val affectedTurrets: MutableSet<ArmorStand> =
                activeTurrets.filter { turret -> turretSpeeds[turret] == delay }.toMutableSet()
            shootTaskIDs.add(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
                onInterval(affectedTurrets)
            }, 0, delay))
        }

        // particles
        particleTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            spawnParticles()
        }, 0, particleDelay)
    }

    private fun stopTasks() {
        shootTaskIDs.forEach { taskID ->
            Bukkit.getScheduler().cancelTask(taskID)
        }
        if (particleTaskID != null) Bukkit.getScheduler().cancelTask(particleTaskID!!)

        shootTaskIDs.clear()
        particleTaskID = null
        // remove arrows later, so it looks better when exiting reach distance
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, {
            removeArrows()
        }, 40)
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

    private fun startReachCheckTask() {
        if (reachCheckTaskID != null) return
        reachCheckTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            reachCheck()
        }, 0, distanceCheckDelay)
        Bukkit.getLogger().info("[Icarus] Enabled turret reach checker")
    }

    private fun stopReachCheckTask() {
        Bukkit.getScheduler().cancelTask(reachCheckTaskID!!)
        reachCheckTaskID = null
    }

    private fun startPerformanceCheckTask() {
        performanceCheckTaskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            performanceChecks()
        }, 0, performanceCheckDelay)
    }

    private fun reloadTurrets() {
        turrets.clear()
        activeTurrets.clear()
        inactiveTurrets.clear()
        Bukkit.getWorld("world")?.entities?.forEach { entity ->
            if (entity is ArmorStand && entity.scoreboardTags.contains("Turret")) {
                turrets.add(entity)
            }
        }
        // get settings from turrets
        turretSpeeds.clear()
        turrets.forEach { turret ->
            // get shot delay, set to default if not found
            if (!turret.persistentDataContainer.has(shotDelayKey)) {
                turret.persistentDataContainer.set(shotDelayKey, PersistentDataType.LONG, shotDelay)
            }
            turretSpeeds[turret] = turret.persistentDataContainer.get(shotDelayKey, PersistentDataType.LONG)!!
        }
        turrets.forEach { turret ->
            if (turret.persistentDataContainer.get(activeKey, PersistentDataType.BOOLEAN) == true) {
                turret.customName = "§cTurret"
            } else {
                turret.customName = "§cTurret (inactive)"
            }
        }
        updateSettings()
    }

    private fun clearTurrets() {
        turrets.forEach { turret ->
            turret.remove()
        }
        activeTurrets.clear()
        inactiveTurrets.clear()
        turrets.clear()
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
        val timeToReach = (turretLocation.distance(playerLocation) / speedMultiplier) + Random().nextFloat(0.3f, 1.5f)

        return playerLocation.add(player.velocity.multiply(timeToReach))
    }

    private fun updateSettings() {
        turrets.forEach { turret ->
            keyNames.forEach inner@{ keyName ->
                val key = NamespacedKey(plugin, keyName)
                if (turret.persistentDataContainer.has(key)) {
                    when (keyName) {
                        "ammo" -> turret.persistentDataContainer.get(key, PersistentDataType.INTEGER)!!
                        "active" -> {
                            if (turret.persistentDataContainer.get(key, PersistentDataType.BOOLEAN) == false) {
                                activeTurrets.remove(turret)
                                inactiveTurrets.add(turret)
                            } else {
                                activeTurrets.add(turret)
                                inactiveTurrets.remove(turret)
                            }
                        }

                        "damage" -> arrowDamage = turret.persistentDataContainer.get(key, PersistentDataType.DOUBLE)!!
                        "reach" -> turretReach = turret.persistentDataContainer.get(key, PersistentDataType.INTEGER)!!
                        "shotDelay" -> shotDelay = turret.persistentDataContainer.get(key, PersistentDataType.LONG)!!
                    }
                    return@inner
                } else when (keyName) {
                    "ammo" -> turret.persistentDataContainer.set(key, PersistentDataType.INTEGER, initialAmmo)
                    "active" -> {
                        turret.persistentDataContainer.set(key, PersistentDataType.BOOLEAN, true)
                        activeTurrets.add(turret)
                    }

                    "damage" -> turret.persistentDataContainer.set(key, PersistentDataType.DOUBLE, arrowDamage)
                    "reach" -> turret.persistentDataContainer.set(key, PersistentDataType.INTEGER, turretReach)
                    "shotDelay" -> turret.persistentDataContainer.set(key, PersistentDataType.LONG, shotDelay)
                }
            }
        }
    }

    @EventHandler
    private fun onFlareDeploy(event: PlayerInteractEvent) {
        if (event.item == null) return
        if (event.item?.itemMeta?.displayName != flares.itemMeta!!.displayName) return
        if (event.action != Action.RIGHT_CLICK_AIR) {
            event.isCancelled = true
            return
        }

        val e = FlareDeployEvent(event.player)
        Bukkit.getPluginManager().callEvent(e)

        if (event.player.gameMode != GameMode.CREATIVE) {
            if (event.player.inventory.itemInMainHand.itemMeta!!.displayName == flares.itemMeta!!.displayName) {
                if (event.player.gameMode != GameMode.CREATIVE) event.player.inventory.itemInMainHand.amount -= 1
            } else if (event.player.inventory.itemInOffHand.itemMeta!!.displayName == flares.itemMeta!!.displayName) {
                if (event.player.gameMode != GameMode.CREATIVE) event.player.inventory.itemInOffHand.amount -= 1
            } else {
                Bukkit.getLogger().warning("[Icarus] Flare item not found in ${event.player}'s hand but was activated")
            }
        }

        event.isCancelled = true
    }

    @EventHandler
    private fun onFlareDeploy(event: FlareDeployEvent) {
        val world = event.player.world
        val loc = event.player.location
        var a = 0
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            if (a > 15) return@scheduleSyncRepeatingTask
            for (i in 0..360 step 10) {
                val x = sin(i.toDouble()).times(a.toDouble())
                val z = cos(i.toDouble()).times(a.toDouble())
                world.spawnParticle(Particle.FLAME, loc.add(x, 0.0, z), 3)
            }
            world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1f, 100f)
            a++
        }, 0, 1)

        immunePlayers.add(event.player)
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, {
            immunePlayers.remove(event.player)
        }, 50)
    }

    @EventHandler
    private fun onDestroy(event: EntityDeathEvent) {
        if (event.entity.scoreboardTags.contains("Turret")) {
            activeTurrets.remove(event.entity)
            inactiveTurrets.remove(event.entity)
            turrets.remove(event.entity)
        }
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!enabled) return
        startReachCheckTask()
        if (!event.player.hasDiscoveredRecipe(NamespacedKey(plugin, "flares"))) {
            event.player.discoverRecipe(NamespacedKey(plugin, "flares"))
        }
    }

    @EventHandler
    private fun onArmorStandHit(event: EntityDamageByEntityEvent) {
        if (event.entity !is ArmorStand) return
        if (!event.entity.scoreboardTags.contains("Turret")) return
        if (event.damager is TNTPrimed && event.entity.scoreboardTags.contains("Turret") && event.damage > 65) {
            return
        }
        event.isCancelled = true
    }

    @EventHandler
    private fun onArmorStandHit(event: EntityDamageEvent) {
        if (event.entity !is ArmorStand) return
        if (event.cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION && event.damage > 65) {
            return
        }
        if (!event.entity.scoreboardTags.contains("Turret")) return
        event.isCancelled = true
    }

    @EventHandler
    private fun onChunkLoad(event: ChunkLoadEvent) {
        if (!enabled) return
        event.chunk.entities.forEach { entity ->
            if (entity is ArmorStand && entity.scoreboardTags.contains("Turret")) {
                turrets.add(entity)
                if (entity.persistentDataContainer.get(activeKey, PersistentDataType.BOOLEAN) == true) {
                    activeTurrets.add(entity)
                } else {
                    inactiveTurrets.add(entity)
                }
            }
        }
    }

    @EventHandler
    private fun onRightClick(event: PlayerInteractAtEntityEvent) {
        if (!enabled) return
        if (!event.rightClicked.scoreboardTags.contains("Turret")) return
        if (event.rightClicked !is ArmorStand) return
        if (checkTeam && sb.getEntryTeam(event.player.name) != sb.getEntryTeam(event.rightClicked.uniqueId.toString())) {
            event.player.sendMessage("§cYou can't interact with this turret")
            return
        }

        val turret = event.rightClicked as ArmorStand
        val ammo = turret.persistentDataContainer.get(ammoKey, PersistentDataType.INTEGER)!!
        val shotDelay = turret.persistentDataContainer.get(shotDelayKey, PersistentDataType.LONG)!!
        val holder = InvHolder()
        val inv = menu.createInventory(invRows, "§lTurret", holder)
        openInvs[holder] = turret
        playersInInv[event.player] = turret

        if (turret.persistentDataContainer.get(activeKey, PersistentDataType.BOOLEAN) == false) {
            menu.createItem(
                inactiveDisplayMaterial, inv, 10, "Inactive", "§r§7Turret is inactive. Click to activate"
            )
        } else {
            menu.createItem(activeDisplayMaterial, inv, 10, "Active", "§r§7Turret is active. Click to deactivate")
        }

        menu.createItem(
            ammoDisplayMaterial, inv, 13, "Ammo", "§r§7Turret ammo: $ammo", "§r§7Add ammo by clicking on arrows"
        )
        menu.createItem(
            shotDelayMaterial,
            inv,
            16,
            "Shot Delay",
            "§r§7Turret shot delay: $shotDelay ticks",
            "§r§7Left click to increase",
            "§r§7Right click to decrease"
        )
        menu.createItem(pickUpMaterial, inv, 35, "Pick up", "§r§7Pick up turret")

        menu.fillWithoutName(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE, ammoPutSlot)

        event.player.openInventory(inv)
        event.isCancelled = true

    }

    @EventHandler
    fun onInvClick(event: InventoryClickEvent) {
        if (!enabled) return
        if (event.clickedInventory == null) return
        if (event.view.title != "§lTurret") return
        if (event.currentItem == null) return
        // if a player clicks his own inventory he is the holder -> turret will be null
        if (event.rawSlot >= invRows * 9) return

        val turret = openInvs[event.clickedInventory!!.holder]

        when (event.currentItem!!.type) {
            activeDisplayMaterial -> {
                val i: ItemStack
                val meta: ItemMeta
                val clickedItem = event.clickedInventory?.getItem(10) ?: return
                if (clickedItem.itemMeta!!.displayName == "Active") {
                    i = ItemStack(inactiveDisplayMaterial)
                    meta = i.itemMeta!!
                    meta.setDisplayName("Inactive")
                    meta.lore = mutableListOf("§r§7Turret is inactive. Click to activate")
                    i.itemMeta = meta
                    event.clickedInventory!!.setItem(10, i)
                    turret!!.persistentDataContainer.set(activeKey, PersistentDataType.BOOLEAN, false)
                    Bukkit.getLogger().info("[Icarus] Turret with ID ${turret.uniqueId} deactivated")
                    activeTurrets.remove(turret)
                    inactiveTurrets.add(turret)
                    turret.customName = "§cTurret (inactive)"
                    event.isCancelled = true
                }
            }

            inactiveDisplayMaterial -> {
                val i: ItemStack
                val meta: ItemMeta
                val clickedItem = event.clickedInventory?.getItem(10) ?: return
                if (clickedItem.itemMeta!!.displayName == "Inactive") {
                    i = ItemStack(activeDisplayMaterial)
                    meta = i.itemMeta!!
                    meta.setDisplayName("Active")
                    meta.lore = mutableListOf("§r§7Turret is active. Click to deactivate")
                    i.itemMeta = meta
                    event.clickedInventory!!.setItem(10, i)
                    turret!!.persistentDataContainer.set(activeKey, PersistentDataType.BOOLEAN, true)
                    Bukkit.getLogger().info("[Icarus] Turret with ID ${turret.uniqueId} activated")
                    activeTurrets.add(turret)
                    inactiveTurrets.remove(turret)
                    turret.customName = "§cTurret"
                    event.isCancelled = true
                }
            }

            shotDelayMaterial -> {
                val i: ItemStack
                val meta: ItemMeta
                val delay = turret!!.persistentDataContainer.get(shotDelayKey, PersistentDataType.LONG)!!
                when {
                    event.isLeftClick -> {
                        if (delay < maxTurretSpeed) {
                            i = ItemStack(shotDelayMaterial)
                            meta = i.itemMeta!!
                            meta.setDisplayName("§rShot Delay")
                            meta.lore = mutableListOf(
                                "§r§7Turret shot delay: ${delay + 1} ticks",
                                "§r§7Left click to increase",
                                "§r§7Right click to decrease"
                            )
                            i.itemMeta = meta
                            event.clickedInventory!!.setItem(16, i)
                            turret.persistentDataContainer.set(shotDelayKey, PersistentDataType.LONG, delay + 1)
                            turretSpeeds[turret] = delay + 1
                        }
                    }

                    event.isRightClick -> {
                        if (delay > minTurretSpeed) {
                            i = ItemStack(shotDelayMaterial)
                            meta = i.itemMeta!!
                            meta.setDisplayName("§rShot Delay")
                            meta.lore = mutableListOf(
                                "§r§7Turret shot delay: ${delay - 1} ticks",
                                "§r§7Left click to increase",
                                "§r§7Right click to decrease"
                            )
                            i.itemMeta = meta
                            event.clickedInventory!!.setItem(16, i)
                            turret.persistentDataContainer.set(shotDelayKey, PersistentDataType.LONG, delay - 1)
                            turretSpeeds[turret] = delay - 1
                        }
                    }
                }
                event.isCancelled = true
            }

            pickUpMaterial -> {
                val player = event.whoClicked as Player
                player.inventory.addItem(turretItem)
                turret!!.remove()
                player.closeInventory()
                activeTurrets.remove(turret)
                inactiveTurrets.remove(turret)
                turrets.remove(turret)
                Bukkit.getLogger().info("[Icarus] $player picked up turret with ID ${turret.uniqueId}")
                event.isCancelled = true
            }

            else -> {
                event.isCancelled = true
                return
            }
        }
        event.isCancelled = true
    }

    // adding ammo to turret, updating inv
    @EventHandler
    fun onInvMoveItem(event: InventoryClickEvent) {
        if (!enabled) return
        if (event.whoClicked !is Player) return
        if (event.currentItem == null) return
        if (playersInInv.isEmpty()) return
        if (event.rawSlot < invRows * 9) return
        if (event.whoClicked in playersInInv.keys) {
            if (event.currentItem!!.type != Material.ARROW) {
                event.isCancelled = true
                return
            }
            val turret = playersInInv[event.whoClicked]!!
            val ammo = turret.persistentDataContainer.get(ammoKey, PersistentDataType.INTEGER)!!
            val amount = event.currentItem!!.amount * 5
            turret.persistentDataContainer.set(ammoKey, PersistentDataType.INTEGER, ammo + amount)
            event.currentItem!!.amount = 0
            val inv = event.whoClicked.openInventory.topInventory
            inv.setItem(ammoPutSlot, ItemStack(Material.AIR))
            val i = ItemStack(ammoDisplayMaterial)
            val meta = i.itemMeta!!
            meta.setDisplayName("§rAmmo")
            meta.lore = mutableListOf("§r§7Turret ammo: ${ammo + amount}", "§r§7Add ammo by clicking on arrows")
            i.itemMeta = meta
            inv.setItem(13, i)
        }
    }

    @EventHandler
    private fun onInvClose(event: InventoryCloseEvent) {
        if (!enabled) return
        if (event.inventory.holder == null) return // || event.inventory.holder !is InvHolder this is probably causing an error
        if (event.inventory.holder in openInvs.keys) {
            openInvs.remove(event.inventory.holder)
            playersInInv.remove(event.player)
        }
    }

    @EventHandler
    private fun onArmorStandPlace(event: PlayerInteractEvent) {
        if (!enabled) return
        if (event.item == null) return
        if (event.item?.itemMeta?.lore == turretItem.itemMeta!!.lore && event.action == Action.RIGHT_CLICK_BLOCK) {
            turrets.forEach { turret ->
                if (event.clickedBlock!!.location.add(0.5, 1.0, 0.5).distance(turret.location) < 0.5) {
                    event.isCancelled = true
                    return
                }
            }
            spawnTurret(event.player, event.clickedBlock!!.location.add(0.5, 1.0, 0.5))
            if (event.player.gameMode != GameMode.CREATIVE) {
                if (event.player.inventory.itemInMainHand == event.item) event.player.inventory.itemInMainHand.amount -= 1
                else event.player.inventory.itemInOffHand.amount -= 1
            }
            // cancelling to prevent another armor stand from being placed if player clicks on the side of a block
            // (it stays a turret when broken again but can't do anything)
            event.isCancelled = true
        }
    }

    @EventHandler
    private fun onItemDrop(event: EntityDeathEvent) {
        if (!enabled) return
        if (event.entity.killer == null || event.entity.killer !is Player) return
        if (event.entity.type == EntityType.ENDERMAN && event.entity.world.environment == World.Environment.NORMAL && (!event.entity.killer!!.inventory.itemInMainHand.enchantments.contains(
                Enchantment.FORTUNE
            ))
        ) {
            // 50% drop chance
            if (Random().nextInt(1, 3) == 1) {
                event.drops.add(customEnderPearl)
                if (!event.entity.killer!!.hasDiscoveredRecipe(NamespacedKey(plugin, "turret"))) {
                    event.entity.killer!!.discoverRecipe(NamespacedKey(plugin, "turret"))
                }
            }
            event.drops.remove(ItemStack(Material.ENDER_PEARL))
        }

    }

    override fun onTabComplete(
        p0: CommandSender, p1: Command, p2: String, p3: Array<out String>
    ): MutableList<String> {
        if (p3.size == 1) {
            return mutableListOf(
                "removeAll",
            )
        }
        return mutableListOf()
    }
}