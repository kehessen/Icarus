package me.kehessen.icarus.combat

import me.kehessen.icarus.event.SAMDeployEvent
import me.kehessen.icarus.util.CustomItem
import me.kehessen.icarus.util.Utils
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team

class MANPAD(config: FileConfiguration, private val bomb: Bomb) : Listener {
    val item = CustomItem(
        Material.DISPENSER,
        "§r§c§lMANPAD",
        "§r§7Man-portable air-defense system against players\n§r§7Shoot to launch a missile"
    )
    val ammoItem = CustomItem(
        Material.FIREWORK_ROCKET, "§r§c§lMANPAD Missile",
        "§r§7Missile for MANPAD"
    )

    private val enabled: Boolean = config.getBoolean("MANPAD.enable")
    private val lockOnRange: Double = config.getDouble("MANPAD.range")
    private val missileSpeed: Float = config.getInt("MANPAD.speed").toFloat()
    private val yield: Float = config.getDouble("MANPAD.yield").toFloat()
    private val lifetime: Int = config.getInt("MANPAD.lifetime")
    private val lockOnTime: Int = config.getInt("MANPAD.lock-on-time")
    private val lockOnCooldown: Int = config.getInt("MANPAD.cooldown")
    private val lockOnThreshold: Double = config.getDouble("MANPAD.lock-angle-threshold")

    private val aimingPlayers = mutableSetOf<Player>()
    private val playersLockingOn = mutableMapOf<Player, Player>()
    private val remainingLockOnTimes = mutableMapOf<Player, Int>()
    private val readyToFire = mutableSetOf<Player>()

    private var onIntervalTask: Int? = null
    private val lockedOnTasks = mutableMapOf<Player, Int>()

    private val missileSound = "minecraft:manpadmissilelock"

    private lateinit var itemKey: NamespacedKey
    private lateinit var ammoKey: NamespacedKey

    private val slownessEffect = PotionEffect(
        PotionEffectType.SLOWNESS,
        20, 4, false, false, false
    )
    private val glowingEffect = PotionEffect(
        PotionEffectType.GLOWING,
        5, 0, false, false, false
    )

    private lateinit var missileTeam: Team
    private lateinit var sb: Scoreboard

    fun start() {
        if (!enabled) return
        itemKey = NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "manpad")
        ammoKey = NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "manpad_missile")
        sb = Bukkit.getScoreboardManager()!!.mainScoreboard
        // TurretHandler gets instantiated before MANPAD, so Team has definitely been created
        missileTeam = sb.getTeam("MissileRedGlow")!!

        Bukkit.getPluginManager()
            .registerEvents(this, Bukkit.getPluginManager().getPlugin("Icarus")!!)


        addRecipes()
        onIntervalTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            Bukkit.getPluginManager().getPlugin("Icarus")!!,
            { onInterval() },
            0,
            10
        )
    }

    private fun onInterval() {
        val iterator = aimingPlayers.iterator()
        while (iterator.hasNext()) {
            val player = iterator.next()
            player.addPotionEffect(slownessEffect)
            if (player.isDead) {
                aimingPlayers.remove(player)
                cancelLockOn(player)
                continue
            }

            if (player.inventory.itemInMainHand != item) {
                aimingPlayers.remove(player)
                cancelLockOn(player)
                continue
            }

            if (playersLockingOn[player] != null) {
                val t = playersLockingOn[player]!!
                t.addPotionEffect(glowingEffect)
                t.playSound(t.location, missileSound, 1f, 1f)
                continue
            }

            val target = getClosestLockOn(player)
            if (target != null) {
                playersLockingOn[player] = target
                target.addPotionEffect(glowingEffect)
                startLockOnCheckTask(player)
            }
        }
        readyToFire.forEach { it.playSound(it.location, missileSound, 1f, 1f) }
    }

    private fun startLockOnCheckTask(player: Player) {
        lockedOnTasks[player] =
            Bukkit.getScheduler().scheduleSyncRepeatingTask(Bukkit.getPluginManager().getPlugin("Icarus")!!, {
                val target = playersLockingOn[player] ?: return@scheduleSyncRepeatingTask
                // task will be cancelled one tick after cancelLockOn is called
                val hasLockOn = hasLockOn(player, target)
                if (player.isDead || !hasLockOn) {
                    cancelLockOn(player)
                    return@scheduleSyncRepeatingTask
                }
//                target.addPotionEffect(glowingEffect)

                if (remainingLockOnTimes[player] == null) {
                    remainingLockOnTimes[player] = lockOnTime
                }

                if (remainingLockOnTimes[player]!! <= 0 && !readyToFire.contains(player)) {
                    readyToFire.add(player)
                    playersLockingOn[player]!!.addPotionEffect(
                        PotionEffect(
                            PotionEffectType.GLOWING,
                            20 * 60,
                            67,
                            false,
                            false,
                            false
                        )
                    )
                }
                if (!player.hasCooldown(item.type))
                    remainingLockOnTimes[player] = remainingLockOnTimes[player]!! - 1
            }, 0, 1)
    }

    private fun cancelLockOn(player: Player) {
        if (lockedOnTasks[player] != null)
            Bukkit.getScheduler().cancelTask(lockedOnTasks[player]!!)
        playersLockingOn[player]?.removePotionEffect(PotionEffectType.GLOWING)
        lockedOnTasks.remove(player)
        playersLockingOn.remove(player)
        remainingLockOnTimes.remove(player)
        readyToFire.remove(player)
    }

    private fun getClosestLockOn(player: Player): Player? {
        val nearbyPlayers = Utils.getNearbyPlayers(player.location, lockOnRange)
        return nearbyPlayers
            .filter { Utils.getAngleDifference(player, it.location) < lockOnThreshold }
            .minByOrNull { player.location.distance(it.location) }
    }

    private fun hasLockOn(player: Player, target: Player): Boolean {
        val angle = Utils.getAngleDifference(player, target.location)
        return angle < lockOnThreshold && target.isGliding
    }

    private fun addRecipes() {
        var recipe = ShapedRecipe(itemKey, item)
        recipe.shape("IDR", "OEO", "OPO")
        recipe.setIngredient('I', Material.IRON_BLOCK)
        recipe.setIngredient('D', Material.DISPENSER)
        recipe.setIngredient('R', Material.REDSTONE_BLOCK)
        recipe.setIngredient('O', Material.OBSIDIAN)
        recipe.setIngredient('E', Material.ENDER_EYE)
        recipe.setIngredient('P', RecipeChoice.ExactChoice(bomb.plutoniumCore))
        Bukkit.addRecipe(recipe)

        recipe = ShapedRecipe(ammoKey, ammoItem)
        recipe.shape("ITI", "IGI", "RBR")
        recipe.setIngredient('I', Material.IRON_BLOCK)
        recipe.setIngredient('T', RecipeChoice.ExactChoice(bomb.mediumBombItem))
        recipe.setIngredient('G', Material.GHAST_TEAR)
        recipe.setIngredient('R', Material.FIREWORK_ROCKET)
        recipe.setIngredient('B', Material.BLAZE_ROD)
        Bukkit.addRecipe(recipe)
    }

    fun stop() {
        playersLockingOn.values.forEach { it.removePotionEffect(PotionEffectType.GLOWING) }
        aimingPlayers.forEach { it.removePotionEffect(PotionEffectType.SLOWNESS) }
    }

    @EventHandler
    private fun onAmmoniumDiscovery(event: EntityPickupItemEvent) {
        if (event.entity !is Player) return
        if (event.item.itemStack.itemMeta != bomb.ammoniumNitrate.itemMeta) return

        val player = event.entity as Player
        if (!player.hasDiscoveredRecipe(itemKey))
            player.discoverRecipe(itemKey)
        if (!player.hasDiscoveredRecipe(ammoKey))
            player.discoverRecipe(ammoKey)
    }

    @EventHandler
    private fun onAim(event: PlayerInteractEvent) {
        if (event.item != item) return
        if (event.action != Action.RIGHT_CLICK_AIR) return

        if (event.player in aimingPlayers) {
            aimingPlayers.remove(event.player)
            event.player.removePotionEffect(PotionEffectType.SLOWNESS)
            cancelLockOn(event.player)
        } else {
            aimingPlayers.add(event.player)
            event.player.addPotionEffect(slownessEffect)
        }

        event.isCancelled = true
    }

    @EventHandler
    private fun onShot(event: PlayerInteractEvent) {
        if (event.item != item) return
        if (event.action != Action.LEFT_CLICK_AIR) return
        if (!readyToFire.contains(event.player)) {
            event.player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacy("§c§l not locked onto a target")
            )
            return
        }

        if (event.player.inventory.containsAtLeast(ammoItem, 1)) {
            event.player.inventory.removeItem(ammoItem)
        } else {
            Utils.sendActionBarMessage(event.player, "§c§l out of ammo")
            return
        }

        SAM(playersLockingOn[event.player]!!, event.player, lifetime, yield, missileSpeed)
        val e = SAMDeployEvent(event.player, playersLockingOn[event.player]!!)
        Bukkit.getPluginManager().callEvent(e)
        if (event.player.gameMode != GameMode.CREATIVE)
            event.player.setCooldown(item.type, lockOnCooldown)
        event.player.removePotionEffect(PotionEffectType.SLOWNESS)
        cancelLockOn(event.player)
    }

    @EventHandler
    private fun onItemSwitch(event: PlayerItemHeldEvent) {
        if (event.player.inventory.getItem(event.newSlot) == item) {
            Utils.sendActionBarMessage(event.player, "left click to shoot --- right click to aim")
            return
        }
        if (event.player.inventory.getItem(event.previousSlot) == item) {
            cancelLockOn(event.player)
            aimingPlayers.remove(event.player)
            if (event.player.getPotionEffect(PotionEffectType.SLOWNESS)?.amplifier == 4)
                event.player.removePotionEffect(PotionEffectType.SLOWNESS)
        }
    }
}