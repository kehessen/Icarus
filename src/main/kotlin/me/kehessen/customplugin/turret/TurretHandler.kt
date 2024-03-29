package me.kehessen.customplugin.turret

import me.kehessen.customplugin.InvHolder
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
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
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
    private var turretReach = 500 //config.getInt("Turret.reach")
    private var arrowDamage = 0.5 //config.getDouble("Turret.damage")
    private var ammo: Int = 100

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
    private var activeTurrets = mutableSetOf<ArmorStand>()
    private var inactiveTurrets = mutableSetOf<ArmorStand>()
    private var turrets = mutableSetOf<ArmorStand>()
    private var activeArrows = hashMapOf<Arrow, Int>()
    private var onlinePlayers = Bukkit.getOnlinePlayers()
    private val targets = mutableSetOf<Player>()

    // shot delay, key: turret, value: shot delay, delay is 1-5 ticks
    private var minTurretSpeed = 1L
    private var maxTurretSpeed = 5L
    private var turretSpeeds = hashMapOf<ArmorStand, Long>()

    // key: inventory holder, value: turret
    private var openInvs = hashMapOf<InventoryHolder, ArmorStand>()

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


    // multiple tasks for different shot delays, up to 5 -> better than creating a new class hehehehaw
    private var shootTaskIDs: MutableSet<Int> = mutableSetOf()
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
            turrets.forEach { turret ->
                turret.persistentDataContainer.set(
                    shotDelayKey,
                    PersistentDataType.LONG,
                    shotDelay
                )
                turretSpeeds[turret] = shotDelay
            }
            if (shootTaskIDs.isNotEmpty()) {
                stopTasks()
                startTasks()
            }
            sender.sendMessage("§aTurret shot delay set to $newDelay ticks")
            return true
        }

        // needs to be after commands with multiple arguments
        if (args.size != 1) {
            sender.sendMessage("§cInvalid arguments")
            return false
        }
        when (args[0]) {
            "burningArrow" -> {
                burningArrow = !burningArrow
                if (burningArrow) {
                    sender.sendMessage("§aTurrets now shoot burning arrows")
                } else {
                    sender.sendMessage("§aTurrets now shoot normal arrows")
                }
                return true
            }

            "reload" -> {
                reloadTurrets()
                onlinePlayers = Bukkit.getOnlinePlayers()
                sender.sendMessage("§aReloaded ${turrets.size} turrets")
                return true
            }

            "remove" -> {
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
                Bukkit.getLogger().info("Disabled turret reach checker since all turrets were removed")
                return true
            }

            "silence" -> {
                silenced = !silenced
                if (silenced) {
                    sender.sendMessage("§aTurrets silenced")
                } else {
                    sender.sendMessage("§aTurrets unsilenced")
                }
                return true
            }

            "spawn" -> {
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
                turrets.add(armorStand)
                activeTurrets.add(armorStand)
                return true
            }

            "targets" -> {
                sender.sendMessage("§aTargets: ${targets.joinToString { it.name }}")
                return true
            }
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

        val tasksToStart = mutableSetOf<Long>()
        turrets.forEach { turret ->
            if (turretSpeeds[turret] == null) {
                turretSpeeds[turret] = shotDelay
            }
            tasksToStart.add(turretSpeeds[turret]!!)
        }

        // start tasks for each shot delay currently used
        tasksToStart.forEach { delay ->
            val affectedTurrets: MutableSet<ArmorStand> =
                turrets.filter { turret -> turretSpeeds[turret] == delay }.toMutableSet()
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
        Bukkit.getScheduler().cancelTask(particleTaskID!!)

//        shootTaskID = null
        shootTaskIDs.clear()
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

    private fun clearTurrets() {
        turrets.forEach { turret ->
            turret.remove()
        }
        activeTurrets.clear()
        inactiveTurrets.clear()
        turrets.clear()
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
//                    }
                    if (shootTaskIDs.isEmpty()) {
                        Bukkit.getLogger().info("Players in range, starting turret tasks")
                        startTasks()
                    }
                    return@first
                } else if (shootTaskIDs.isNotEmpty() && targets.isEmpty()) {
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
//            if (shootTaskID != null) stopTasks()
            if (shootTaskIDs.isNotEmpty()) stopTasks()
        } else if (activeTurrets.isEmpty() && shootTaskIDs.isNotEmpty()) {
            Bukkit.getLogger().info("No turrets found, stopping tasks")
            stopTasks()
            stopReachCheckTask()
        }
    }

    fun reloadTurrets() {
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
            turretSpeeds[turret] = turret.persistentDataContainer.get(shotDelayKey, PersistentDataType.LONG)!!
        }
        updateSettings()
        activeArrows.clear()
        Bukkit.getWorld("world")?.entities?.forEach { entity ->
            if (entity is Arrow && entity.scoreboardTags.contains("TurretArrow")) {
                activeArrows[entity] = arrowLifeTime
            }
        }
    }

    private fun onInterval(turrets: Set<ArmorStand>) {
        targets.forEach { player ->
            turrets.forEach inner@{ turret ->
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

    // I MADE A WHOLE CLASS FOR THIS JUST TO REALIZE 5 HOURS LATER THAT I DON'T NEED IT FML (I might need it)
    // active needs special treatment since I need another set for inactive turrets unless I make a new class (I won't)
    private fun updateSettings() {
        turrets.forEach { turret ->
            keyNames.forEach inner@{ keyName ->
                val key = NamespacedKey(plugin, keyName)
                if (turret.persistentDataContainer.has(key)) {
                    when (keyName) {
                        "ammo" -> ammo = turret.persistentDataContainer.get(key, PersistentDataType.INTEGER)!!
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
                }
                when (keyName) {
                    "ammo" -> turret.persistentDataContainer.set(key, PersistentDataType.INTEGER, ammo)
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
            inactiveTurrets.remove(event.entity)
            turrets.remove(event.entity)
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
    private fun onRightClick(event: PlayerInteractAtEntityEvent) {
        Bukkit.getLogger().info(turrets.size.toString())
        if (event.rightClicked is ArmorStand && event.rightClicked.scoreboardTags.contains("Turret")) {
            Bukkit.getLogger().info("Opening turret menu for ${event.player.name}")
            val turret = event.rightClicked as ArmorStand
            val ammo = turret.persistentDataContainer.get(ammoKey, PersistentDataType.INTEGER)!!
            val holder = InvHolder()
            val inv = menu.createInventory(invRows, "§lTurret", holder)
            openInvs[holder] = turret


            menu.createItem(activeDisplayMaterial, inv, 10, "Active", "Turret is active. Click to deactivate")
            menu.createItem(
                ammoDisplayMaterial,
                inv,
                13,
                "Ammo",
                "Turret ammo: $ammo",
                "Add ammo by shift-clicking arrows"
            )
            menu.createItem(
                shotDelayMaterial,
                inv,
                16,
                "Shot Delay",
                "Turret shot delay: ${turretSpeeds[turret]} ticks",
                "Left click to increase",
                "Right click to decrease"
            )
            menu.createItem(pickUpMaterial, inv, 35, "Pick up", "Pick up turret")

            menu.fillWithoutName(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE, 22)

            event.player.openInventory(inv)
        }
    }

    @EventHandler
    fun onInvClick(event: InventoryClickEvent) {
        if (event.clickedInventory == null) return
        if (event.view.title != "§lTurret") return
        if (event.currentItem == null) return
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
                    meta.lore = mutableListOf("Turret is inactive. Click to activate")
                    i.itemMeta = meta
                    event.clickedInventory!!.setItem(10, i)
                    turret!!.persistentDataContainer.set(activeKey, PersistentDataType.BOOLEAN, false)
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
                    meta.lore = mutableListOf("Turret is active. Click to deactivate")
                    i.itemMeta = meta
                    event.clickedInventory!!.setItem(10, i)
                    turret!!.persistentDataContainer.set(activeKey, PersistentDataType.BOOLEAN, true)
                    event.isCancelled = true
                }
            }

            shotDelayMaterial -> {
                val i: ItemStack
                val meta: ItemMeta
                val clickedItem = event.clickedInventory?.getItem(16) ?: return
                val delay = turret!!.persistentDataContainer.get(shotDelayKey, PersistentDataType.LONG)!!
                when {
                    event.isLeftClick -> {
                        if (delay < maxTurretSpeed) {
                            i = ItemStack(shotDelayMaterial)
                            meta = i.itemMeta!!
                            meta.setDisplayName("Shot Delay")
                            meta.lore = mutableListOf(
                                "Turret shot delay: ${delay + 1} ticks",
                                "Left click to increase",
                                "Right click to decrease"
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
                            meta.setDisplayName("Shot Delay")
                            meta.lore = mutableListOf(
                                "Turret shot delay: ${delay - 1} ticks",
                                "Left click to increase",
                                "Right click to decrease"
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

            else -> {
                event.isCancelled = true
                return
            }
        }
        event.isCancelled = true
    }

    @EventHandler
    private fun onInvClose(event: InventoryCloseEvent) {
        if (event.inventory.holder in openInvs.keys) {
            openInvs.remove(event.inventory.holder)
            Bukkit.getLogger().info("Closed turret menu of ${event.player.name}")
        }
    }

    override fun onTabComplete(
        p0: CommandSender, p1: Command, p2: String, p3: Array<out String>
    ): MutableList<String> {
        if (p3.size == 1) {
            return mutableListOf(
                "burningArrow",
                "disableAll",
                "enableAll",
                "reload",
                "remove",
                "shotDelay",
                "silence",
                "spawn",
                "targets"
            )
        }
        return mutableListOf("")
    }
}