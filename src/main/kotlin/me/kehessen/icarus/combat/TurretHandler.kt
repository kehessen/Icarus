package me.kehessen.icarus.combat

import me.kehessen.icarus.event.FlareDeployEvent
import me.kehessen.icarus.event.TurretOutOfAmmoEvent
import me.kehessen.icarus.util.CustomItem
import me.kehessen.icarus.util.InvHolder
import me.kehessen.icarus.util.MenuHandler
import org.bukkit.*
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


private const val INVENTORY_ROWS = 4
private const val AMMO_PUT_SLOT = 22
private const val TURRET_TITLE = "§lTurret"
private const val TURRET_TAG = "Turret"
internal const val TURRET_ARROW_TAG = "TurretArrow"
private const val INITIAL_AMMO = 20
private val AMMO_DISPLAY_MATERIAL = Material.ARROW
private val ACTIVE_DISPLAY_MATERIAL = Material.GREEN_WOOL
private val INACTIVE_DISPLAY_MATERIAL = Material.RED_WOOL
private val SHOT_DELAY_MATERIAL = Material.CLOCK
private val PICK_UP_MATERIAL = Material.BARRIER

@Suppress("unused")
class TurretHandler(private val plugin: JavaPlugin, config: FileConfiguration, private val menu: MenuHandler) :
    Listener {

    // ---config---
    private val enabled = config.getBoolean("Turret.enable")
    private val checkTeam: Boolean = config.getBoolean("Turret.check-team")
    private var shotDelay: Long = config.getLong("Turret.shot-delay")
    private val particleDelay: Long = config.getLong("Turret.particle-delay")
    private val particleAmount = config.getInt("Turret.particle-amount")
    private val particleSpread = config.getDouble("Turret.particle-spread")
    private var turretReach = config.getInt("Turret.reach")
    private var arrowDamage = config.getDouble("Turret.damage")

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
    private var turrets = mutableSetOf<Turret>()
    private var activeTurrets = mutableSetOf<Turret>()
    private var inactiveTurrets = mutableSetOf<Turret>()
    private var shootingTurrets = mutableSetOf<Turret>()
    private val targets = mutableSetOf<Player>()
    private val lockedOn = hashMapOf<Turret, Player>()
    private val immunePlayers = mutableSetOf<Player>()


    // shot delay, key: turret, value: shot delay, delay is 1-5 ticks
    private var minTurretSpeed = config.getLong("Turret.min-shot-delay")
    private var maxTurretSpeed = config.getLong("Turret.max-shot-delay")
    private var turretSpeeds = hashMapOf<Turret, Long>()

    private lateinit var sb: Scoreboard

    // key: inventory holder, value: turret
    private var openInvs = hashMapOf<InventoryHolder, Turret>()
    private var playersInInv = hashMapOf<Player, Turret>()

    private var keyNames = arrayOf("ammo", "active", "damage", "reach", "shotDelay")
    private val ammoKey = NamespacedKey(plugin, "ammo")
    private val activeKey = NamespacedKey(plugin, "active")
    private val damageKey = NamespacedKey(plugin, "damage")
    private val reachKey = NamespacedKey(plugin, "reach")
    private val shotDelayKey = NamespacedKey(plugin, "shotDelay")


    // custom sound from resource pack
    private val customSound = "minecraft:missilelock"


    internal val customEnderPearl =
        CustomItem(Material.ENDER_PEARL, "§r§lEnder Pearl", "§r§7Can be used to craft turrets")
    internal val turretItem = CustomItem(Material.ARMOR_STAND, "§r§lTurret", "§r§7Right click to place")
    internal val flares = CustomItem(
        Material.BLAZE_ROD, "§r§lFlares", "§r§7Right click to use", "§r§7Can be used to distract turrets"
    )


    // multiple tasks for different shot delays, up to 5 -> better than creating a new class 
    private var shootTaskIDs: MutableSet<Int> = mutableSetOf()
    private var reachCheckTaskID: Int? = null
    private var performanceCheckTaskID: Int? = null

    private lateinit var arrowTeam: Team


    private fun reachCheck() {
        if (Bukkit.getOnlinePlayers().none { it.isGliding }) {
            lockedOn.clear()
            shootingTurrets.clear()
            targets.clear()
            activeTurrets.forEach { it.target = null }
            if (shootTaskIDs.isNotEmpty()) {
                stopShootTasks()
            }

            return
        }

        // add players in range to target list, start tasks if not already running
        val shooter = hashMapOf<Turret, Player>()
        val isShooting = mutableSetOf<Turret>()
        activeTurrets.forEach outer@{ turret ->
            // skip all checks if turret already has a target
            if (checkLockOnStatus(turret, shooter, isShooting)) return@outer
            if (!turret.active) {
                deactivateTurret(turret)

            }

            val reach = turretReach.toDouble()
            turret.nearbyPlayers(reach).forEach { player ->
                if (checkTeam && sb.getEntryTeam(player.name) == sb.getEntryTeam(turret.uuid.toString()))
                    return@forEach

                // if player is supposed to be targeted, add him to the list of targets
                if (player.isGliding && turret.hasLineOfSight(player) && turret.inReach(player, reach)) {
                    shooter[turret] = player
                    isShooting.add(turret)
                    turret.target = player
                    // if player is still in range after 20 ticks, lock on
                    startLockOnProcess(turret, player)
                    return@outer
                }
            }
        }

        shootingTurrets.removeIf { turret -> !isShooting.contains(turret) }
        targets.removeIf { player -> !shooter.containsValue(player) }

        if (shootTaskIDs.isNotEmpty() && targets.isEmpty()) {
            stopShootTasks()
        }

        // disable hit delay for targeted players
        updatePlayersHitDelay()
    }

    private fun startLockOnProcess(turret: Turret, player: Player) {
        // if he is newly discovered by a turret, schedule a task to check lock after lockOn time and add him to target list
        // start playing lockOn sound (if he has the required resource pack)
        if (!targets.contains(player)) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, {
                if (player.isGliding && turret.hasLineOfSight(player) &&
                    turret.inReach(player, turretReach.toDouble())
                ) {
                    shootingTurrets.add(turret)
                    targets.add(player)
                    lockedOn[turret] = player
                    if (shootTaskIDs.isEmpty()) {
                        startTasks()
                    }
                }
            }, 19)
            player.playSound(turret.pos, customSound, 100f, 1f)
        }
    }

    // checks if the turret is rightfully locked on to a player and if so, modifies the given isShooting 
    // and shooter lists and returns true
    // if not, removes the turret from the lockedOn list and returns false
    private fun checkLockOnStatus(
        turret: Turret,
        shooter: HashMap<Turret, Player>,
        isShooting: MutableSet<Turret>
    ): Boolean {
        if (lockedOn.containsKey(turret)) {
            val player = lockedOn[turret]!!
            if (player.isGliding && player.isOnline &&
                turret.hasLineOfSight(player) && turret.inReach(player, turretReach.toDouble())
            ) {
                isShooting.add(turret)
                shooter[turret] = player
                return true
            } else {
                lockedOn.remove(turret)
            }
        }
        return false
    }

    private fun updatePlayersHitDelay() {
        Bukkit.getOnlinePlayers().forEach { player ->
            if (!targets.contains(player)) {
                // add hit delay if not targeted
                if (player.maximumNoDamageTicks != 20) {
                    player.maximumNoDamageTicks = 20
                }
                // remove hit delay if targeted
            } else if (player.maximumNoDamageTicks != 0) {
                player.maximumNoDamageTicks = 0
            }
        }
    }

    private fun onInterval(turrets: Set<Turret>) {
        turrets.forEach { turret ->
            if (turret.target == null) return@forEach
            if (turret.inReach(turret.target, turret.reach.toDouble()) &&
                turret.target!!.isOnline && !isImmune(turret.target!!)
            ) {
                turret.shoot()
            }
        }
    }

    // this should always run, even if no players are online
    private fun performanceChecks() {
        if (Bukkit.getOnlinePlayers().isEmpty() && reachCheckTaskID != null) {
            stopReachCheckTask()
            if (shootTaskIDs.isNotEmpty()) stopShootTasks()
        } else if (activeTurrets.isEmpty() && shootTaskIDs.isNotEmpty()) {
            stopShootTasks()
            stopReachCheckTask()
        }

        Bukkit.getWorlds().forEach { world ->
            world.entities.forEach { entity ->
                if (entity is Arrow && entity.scoreboardTags.contains(TURRET_ARROW_TAG)) {
                    if (entity.ticksLived > arrowLifeTime + 40) {
                        entity.remove()
                    }
                }
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
        armorStand.addScoreboardTag(TURRET_TAG)
        sb.getEntryTeam(player.name)!!.addEntry(armorStand.uniqueId.toString())

        armorStand.persistentDataContainer.set(ammoKey, PersistentDataType.INTEGER, INITIAL_AMMO)
        armorStand.persistentDataContainer.set(activeKey, PersistentDataType.BOOLEAN, true)
        armorStand.persistentDataContainer.set(damageKey, PersistentDataType.DOUBLE, arrowDamage)
        armorStand.persistentDataContainer.set(reachKey, PersistentDataType.INTEGER, turretReach)
        armorStand.persistentDataContainer.set(shotDelayKey, PersistentDataType.LONG, shotDelay)

        val turret = Turret(armorStand)
        turretSpeeds[turret] = shotDelay

        turrets.add(turret)
        activeTurrets.add(turret)
    }

    private fun deactivateTurret(turret: Turret) {
        turret.active = false
        activeTurrets.remove(turret)
        inactiveTurrets.add(turret)
    }

    fun start() {
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
            if (Bukkit.getServer()
                    .spigot().config.getInt("world-settings.default.entity-activation-range.misc") != turretReach
            ) {
                Bukkit.getLogger()
                    .severe("[Icarus] Failed to set entity activation range. Manually change it in spigot.yml to $turretReach to prevent issues")
            }
        }
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
                val tspeed = turret.speed
                turretSpeeds[turret] = tspeed
            }
            tasksToStart.add(turretSpeeds[turret]!!)
        }

        // start tasks for each shot delay currently used
        tasksToStart.forEach { delay ->
            val affectedTurrets: MutableSet<Turret> =
                activeTurrets.filter { turret -> turretSpeeds[turret] == delay }.toMutableSet()
            shootTaskIDs.add(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
                onInterval(affectedTurrets)
            }, 0, delay))
        }
    }

    private fun stopShootTasks() {
        shootTaskIDs.forEach { Bukkit.getScheduler().cancelTask(it) }

        shootTaskIDs.clear()
        // remove arrows later, so it looks better when exiting reach distance
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, { removeArrows() }, 40)
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
        Bukkit.getWorlds().forEach { world ->
            world.entities.forEach { entity ->
                if (entity is ArmorStand && entity.scoreboardTags.contains(TURRET_TAG)) {
                    turrets.add(Turret(entity))
                }
            }
        }

        turrets.forEach { turret ->
            if (turret.active) {
                turret.name = "§cTurret"
                activeTurrets.add(turret)
            } else {
                deactivateTurret(turret)
            }
        }

        // get settings from turrets
        turretSpeeds.clear()
        turrets.forEach { turret ->
            turretSpeeds[turret] = turret.speed
        }
    }

    private fun removeArrows() {
        Bukkit.getWorlds().forEach { world ->
            world.entities.forEach { entity ->
                if (entity is Arrow && entity.scoreboardTags.contains(TURRET_ARROW_TAG)) {
                    entity.remove()
                }
            }
        }
    }

    private fun getTurret(armorStand: ArmorStand): Turret? {
        return turrets.find { it.armorStand == armorStand }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun givePlayerImmunity(player: Player, ticks: Long) {
        immunePlayers.add(player)
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, {
            immunePlayers.remove(player)
        }, ticks)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    internal fun isImmune(player: Player): Boolean {
        return immunePlayers.contains(player)
    }

    //TODO: check events, especially ammo, target handling, etc.

    @EventHandler
    private fun onTurretOutOfAmmo(event: TurretOutOfAmmoEvent) {
        deactivateTurret(event.turret)
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
                event.player.inventory.itemInMainHand.amount -= 1
            } else if (event.player.inventory.itemInOffHand.itemMeta!!.displayName == flares.itemMeta!!.displayName) {
                event.player.inventory.itemInOffHand.amount -= 1
            }
        }

        event.isCancelled = true
    }

    @EventHandler
    private fun onFlareDeploy(event: FlareDeployEvent) {
        val world = event.player.world
        val loc = event.player.location

        for (partRadius in 0..15) {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
                for (i in 0..360 step 10) {
                    val x = sin(i.toDouble()).times(partRadius.toDouble())
                    val z = cos(i.toDouble()).times(partRadius.toDouble())
                    world.spawnParticle(Particle.FLAME, loc.add(x, 0.0, z), 3)
                }
                world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1f, 100f)
            }, 0, 1 + partRadius.toLong())
        }

        givePlayerImmunity(event.player, 50)
    }

    @EventHandler
    private fun onDestroy(event: EntityDeathEvent) {
        if (event.entity.scoreboardTags.contains(TURRET_TAG)) {
            activeTurrets.removeIf { it.armorStand == event.entity }
            inactiveTurrets.removeIf { it.armorStand == event.entity }
            turrets.removeIf { it.armorStand == event.entity }
        }
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!enabled) return
        if (activeTurrets.isNotEmpty() && reachCheckTaskID == null)
            startReachCheckTask()
        if (!event.player.hasDiscoveredRecipe(NamespacedKey(plugin, "flares")))
            event.player.discoverRecipe(NamespacedKey(plugin, "flares"))
    }

    @EventHandler
    private fun onArmorStandHit(event: EntityDamageByEntityEvent) {
        if (event.entity !is ArmorStand) return
        if (!event.entity.scoreboardTags.contains(TURRET_TAG)) return
        if (event.damager is TNTPrimed && event.damage > 65) {
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
        if (!event.entity.scoreboardTags.contains(TURRET_TAG)) return
        event.isCancelled = true
    }

    @EventHandler
    private fun onChunkLoad(event: ChunkLoadEvent) {
        if (!enabled) return
        event.chunk.entities.forEach { entity ->
            if (entity is ArmorStand && entity.scoreboardTags.contains(TURRET_TAG)) {
                val turret = Turret(entity)
                turrets.add(turret)
                if (entity.persistentDataContainer.get(activeKey, PersistentDataType.BOOLEAN) == true) {
                    activeTurrets.add(turret)
                } else {
                    inactiveTurrets.add(turret)
                }
            }
        }
    }

    @EventHandler
    private fun onRightClick(event: PlayerInteractAtEntityEvent) {
        if (!enabled) return
        if (!event.rightClicked.scoreboardTags.contains(TURRET_TAG)) return
        if (event.rightClicked !is ArmorStand) return
        if (checkTeam && sb.getEntryTeam(event.player.name) != sb.getEntryTeam(event.rightClicked.uniqueId.toString())) {
            event.player.sendMessage("§cYou can't interact with this turret")
            return
        }

        val armorStand = event.rightClicked as ArmorStand
        val turret = getTurret(armorStand) ?: return
        val holder = InvHolder()
        val inv = menu.createInventory(INVENTORY_ROWS, TURRET_TITLE, holder)
        openInvs[holder] = turret
        playersInInv[event.player] = turret

        if (!turret.active) {
            menu.createItem(
                INACTIVE_DISPLAY_MATERIAL, inv, 10, "Inactive", "§r§7Turret is inactive. Click to activate"
            )
        } else {
            menu.createItem(ACTIVE_DISPLAY_MATERIAL, inv, 10, "Active", "§r§7Turret is active. Click to deactivate")
        }

        menu.createItem(
            AMMO_DISPLAY_MATERIAL,
            inv,
            13,
            "Ammo",
            "§r§7Turret ammo: ${turret.ammo}",
            "§r§7Add ammo by clicking on arrows"
        )
        menu.createItem(
            SHOT_DELAY_MATERIAL,
            inv,
            16,
            "Shot Delay",
            "§r§7Turret shot delay: ${turret.speed} ticks",
            "§r§7Left click to increase",
            "§r§7Right click to decrease"
        )
        menu.createItem(PICK_UP_MATERIAL, inv, 35, "Pick up", "§r§7Pick up turret")

        menu.fillWithoutName(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE, AMMO_PUT_SLOT)

        event.player.openInventory(inv)
        event.isCancelled = true
    }

    @EventHandler
    fun onInvClick(event: InventoryClickEvent) {
        if (!enabled) return
        if (event.clickedInventory == null) return
        if (event.view.title != TURRET_TITLE) return
        if (event.currentItem == null) return
        // if a player clicks his own inventory he is the holder -> turret will be null
        if (event.rawSlot >= 4 * 9) return

        val turret = openInvs[event.clickedInventory!!.holder] ?: return

        when (event.currentItem!!.type) {
            ACTIVE_DISPLAY_MATERIAL -> {
                val i: ItemStack
                val meta: ItemMeta
                val clickedItem = event.clickedInventory?.getItem(10) ?: return
                if (clickedItem.itemMeta!!.displayName == "Active") {
                    i = ItemStack(INACTIVE_DISPLAY_MATERIAL)
                    meta = i.itemMeta!!
                    meta.setDisplayName("Inactive")
                    meta.lore = mutableListOf("§r§7Turret is inactive. Click to activate")
                    i.itemMeta = meta
                    event.clickedInventory!!.setItem(10, i)
                    turret.active = false
                    Bukkit.getLogger().info("[Icarus] Turret with ID ${turret.uuid} deactivated")
                    deactivateTurret(turret)
                    turret.name = "§cTurret (inactive)"
                    event.isCancelled = true
                }
            }

            INACTIVE_DISPLAY_MATERIAL -> {
                val i: ItemStack
                val meta: ItemMeta
                val clickedItem = event.clickedInventory?.getItem(10) ?: return
                if (clickedItem.itemMeta!!.displayName == "Inactive") {
                    i = ItemStack(ACTIVE_DISPLAY_MATERIAL)
                    meta = i.itemMeta!!
                    meta.setDisplayName("Active")
                    meta.lore = mutableListOf("§r§7Turret is active. Click to deactivate")
                    i.itemMeta = meta
                    event.clickedInventory!!.setItem(10, i)
                    turret.active = true
                    Bukkit.getLogger().info("[Icarus] Turret with ID ${turret.uuid} activated")
                    activeTurrets.add(turret)
                    inactiveTurrets.remove(turret)
                    turret.name = "§cTurret"
                    event.isCancelled = true
                }
            }

            SHOT_DELAY_MATERIAL -> {
                val i: ItemStack
                val meta: ItemMeta
                val delay = turret.speed
                when {
                    event.isLeftClick -> {
                        if (delay < maxTurretSpeed) {
                            i = ItemStack(SHOT_DELAY_MATERIAL)
                            meta = i.itemMeta!!
                            meta.setDisplayName("§rShot Delay")
                            meta.lore = mutableListOf(
                                "§r§7Turret shot delay: ${delay + 1} ticks",
                                "§r§7Left click to increase",
                                "§r§7Right click to decrease"
                            )
                            i.itemMeta = meta
                            event.clickedInventory!!.setItem(16, i)
                            turret.speed++
                            turretSpeeds[turret] = turret.speed
                        }
                    }

                    event.isRightClick -> {
                        if (delay > minTurretSpeed) {
                            i = ItemStack(SHOT_DELAY_MATERIAL)
                            meta = i.itemMeta!!
                            meta.setDisplayName("§rShot Delay")
                            meta.lore = mutableListOf(
                                "§r§7Turret shot delay: ${delay - 1} ticks",
                                "§r§7Left click to increase",
                                "§r§7Right click to decrease"
                            )
                            i.itemMeta = meta
                            event.clickedInventory!!.setItem(16, i)
                            turret.speed--
                            turretSpeeds[turret] = delay - 1
                        }
                    }
                }
                event.isCancelled = true
            }

            PICK_UP_MATERIAL -> {
                val player = event.whoClicked as Player
                player.inventory.addItem(turretItem)
                turret.armorStand.remove()
                player.closeInventory()
                activeTurrets.remove(turret)
                inactiveTurrets.remove(turret)
                turrets.remove(turret)
                Bukkit.getLogger().info("[Icarus] $player picked up turret with ID ${turret.uuid}")
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
        if (event.rawSlot < 4 * 9) return
        if (event.whoClicked in playersInInv.keys) {
            if (event.currentItem!!.type != Material.ARROW) {
                event.isCancelled = true
                return
            }
            val turret = playersInInv[event.whoClicked]!!
            val amount = event.currentItem!!.amount * 5
            turret.ammo += amount
            event.currentItem!!.amount = 0
            val inv = event.whoClicked.openInventory.topInventory
            inv.setItem(AMMO_PUT_SLOT, ItemStack(Material.AIR))
            val i = ItemStack(AMMO_DISPLAY_MATERIAL)
            val meta = i.itemMeta!!
            meta.setDisplayName("§rAmmo")
            meta.lore = mutableListOf("§r§7Turret ammo: ${turret.ammo + amount}", "§r§7Add ammo by clicking on arrows")
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
                if (event.clickedBlock!!.location.add(0.5, 1.0, 0.5).distance(turret.pos) < 0.5) {
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
}