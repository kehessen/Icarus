package me.kehessen.icarus.combat

import me.kehessen.icarus.util.CustomItem
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.*
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.SpectralArrow
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import kotlin.random.Random

// anything over yield 5 can destroy turrets
// wanted to do a custom advancement, but it would require a dependency which is annoying
@Suppress("unused")
class Bomb(config: FileConfiguration, private val base: Base) : CommandExecutor, TabCompleter, Listener {

    internal var smallBombItem = CustomItem(
        Material.TNT,
        "§r§c50kg Bomb",
        "§fRight click while flying to drop",
        "§7Can be used to destroy turrets",
        "§7Will slow you down when flying"
    )
    internal var mediumBombItem = CustomItem(
        Material.TNT,
        "§r§c100kg Bomb",
        "§fRight click while flying to drop",
        "§7Can be used to destroy turrets",
        "§7Will slow you down when flying"
    )
    internal var largeBombItem = CustomItem(
        Material.TNT,
        "§r§c§lHydrogen Bomb",
        "§fRight click while flying to drop",
        "§7Can be used to destroy turrets",
        "§7Will slow you down when flying"
    )
    internal var rocketLauncherItem = CustomItem(
        Material.CROSSBOW,
        "§r§c§lRocket Launcher",
        "§7Right click to shoot an unguided missile",
        "§7Can be used to destroy bombs"
    )
    internal var rocketLauncherAmmo = CustomItem(
        Material.FIREWORK_ROCKET,
        "§r§cRocket",
        "§fUsed for the Rocket Launcher",
    )

    private var smallBombEnabled = config.getBoolean("Bomb.enable-small")
    private var mediumBombEnabled = config.getBoolean("Bomb.enable-medium")
    private var largeBombEnabled = config.getBoolean("Bomb.enable-large")

    private var smallBombYield = config.getInt("Bomb.small-yield")
    private var mediumBombYield = config.getInt("Bomb.medium-yield")
    private var largeBombYield = config.getInt("Bomb.large-yield")

    private var fuseTicks = config.getInt("Bomb.fuse-ticks")

    private var launcherRange = config.getDouble("RocketLauncher.range")
    private var rocketYield = config.getDouble("RocketLauncher.yield").toFloat()
    private var rockets = hashMapOf<SpectralArrow, Location>()
    private var rocketCheckID: Int? = null

    private var smallSpeedLimit = config.getDouble("Bomb.small-speed-limit")
    private var mediumSpeedLimit = config.getDouble("Bomb.medium-speed-limit")
    private var largeSpeedLimit = config.getDouble("Bomb.large-speed-limit")

    private val dropAmmonium = config.getBoolean("Bomb.drop-ammonium-nitrate")
    private val dropPlutonium = config.getBoolean("Bomb.drop-plutonium")

    private val ammoniumChance = config.getInt("Bomb.ammonium-nitrate-chance")
    private val plutoniumChance = config.getInt("Bomb.plutonium-chance")

    private var playersWithSmallBomb = mutableSetOf<Player>()
    private var playersWithMediumBomb = mutableSetOf<Player>()
    private var playersWithLargeBomb = mutableSetOf<Player>()

    private var activeBombs = ArrayList<TNTPrimed>()

    private val activeTasks = hashMapOf<TNTPrimed, Int>()

    private var explosionCheckTask: Int? = null


    internal var ammoniumNitrate = CustomItem(Material.SUGAR, "§r§cAmmonium Nitrate", "§fUsed to craft Bombs")
    internal var plutoniumCore = CustomItem(
        Material.FIREWORK_STAR, "§r§c§lPlutonium Core", "§fUsed to craft Hydrogen Bombs"
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args[0]) {
            "spawn" -> {
                if (sender !is Player) return false
                if (args.size != 2 || args[1].toFloatOrNull() == null) {
                    sender.sendMessage("§cInvalid arguments")
                    return true
                }
                val player = Bukkit.getPlayer(sender.name)
                val tnt = player!!.world.spawn(player.location, org.bukkit.entity.TNTPrimed::class.java)
                tnt.fuseTicks = fuseTicks
                tnt.yield = args[1].toFloat()
                return true
            }

            "give" -> {
                if (args.size != 2 || sender !is Player) {
                    sender.sendMessage("§cInvalid arguments")
                    return true
                }
                when (args[1]) {
                    "small" -> {
                        sender.inventory.addItem(ItemStack(smallBombItem))
                        return true
                    }

                    "medium" -> {
                        sender.inventory.addItem(ItemStack(mediumBombItem))
                        return true
                    }

                    "large" -> {
                        sender.inventory.addItem(ItemStack(largeBombItem))
                        return true
                    }

                    "ammonium" -> {
                        sender.inventory.addItem(ItemStack(ammoniumNitrate))
                        return true
                    }

                    "plutonium" -> {
                        sender.inventory.addItem(ItemStack(plutoniumCore))
                        return true
                    }

                    "rocketLauncher" -> {
                        sender.inventory.addItem(ItemStack(rocketLauncherItem))
                        return true
                    }

                    "launcherAmmo" -> {
                        sender.inventory.addItem(ItemStack(rocketLauncherAmmo))
                        return true
                    }

                    else -> {
                        sender.sendMessage("§cInvalid arguments")
                        return true
                    }

                }
            }

            else -> return false
        }
    }

    fun start() {
        Bukkit.getPluginCommand("bomb")?.setExecutor(this)
        Bukkit.getPluginCommand("bomb")?.tabCompleter = this
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("Icarus")!!)

        if(!smallBombEnabled && !mediumBombEnabled && !largeBombEnabled) return

        val meta = rocketLauncherItem.itemMeta as org.bukkit.inventory.meta.CrossbowMeta
        meta.setChargedProjectiles(listOf(ItemStack(Material.FIREWORK_ROCKET)))
        rocketLauncherItem.itemMeta = meta

        addRecipes()
        reloadRockets()
    }

    fun getPlayersWithBombs(): List<Player> {
        return (playersWithSmallBomb + playersWithMediumBomb + playersWithLargeBomb).toList()
    }

    private fun addRecipes() {
        var recipe = ShapedRecipe(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "small_bomb"), smallBombItem
        )
        recipe.shape("TT", "TT")
        recipe.setIngredient('T', Material.TNT)
        Bukkit.addRecipe(recipe)

        recipe = ShapedRecipe(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "medium_bomb"), mediumBombItem
        )
        recipe.shape("TTT", "TAT", "TTT")
        recipe.setIngredient('T', RecipeChoice.ExactChoice(ItemStack(Material.TNT)))
        recipe.setIngredient('A', RecipeChoice.ExactChoice(ammoniumNitrate))
        Bukkit.addRecipe(recipe)

        recipe = ShapedRecipe(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "large_bomb"), largeBombItem
        )
        recipe.shape("TTT", "TCT", "TTT")
        recipe.setIngredient('T', RecipeChoice.ExactChoice(smallBombItem))
        recipe.setIngredient('C', RecipeChoice.ExactChoice(plutoniumCore))
        Bukkit.addRecipe(recipe)

        recipe = ShapedRecipe(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "rocket_launcher"), rocketLauncherItem
        )
        recipe.shape("OOO", "ONO", "ORO")
        recipe.setIngredient('N', RecipeChoice.ExactChoice(ItemStack(Material.NETHER_STAR)))
        recipe.setIngredient('O', RecipeChoice.ExactChoice(ItemStack(Material.OBSIDIAN)))
        recipe.setIngredient('R', RecipeChoice.ExactChoice(ItemStack(Material.REDSTONE_BLOCK)))
        Bukkit.addRecipe(recipe)

        recipe = ShapedRecipe(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "rocket_launcher_ammo"), rocketLauncherAmmo
        )
        recipe.shape(" I ", " S ", "GBG")
        recipe.setIngredient('I', Material.IRON_INGOT)
        recipe.setIngredient('S', Material.STICK)
        recipe.setIngredient('G', Material.GUNPOWDER)
        recipe.setIngredient('B', Material.BLAZE_POWDER)
        Bukkit.addRecipe(recipe)
    }

    private fun checkItems(player: Player, sendMessage: Boolean = false) {
        var sentMessage = false
        if (player.inventory.containsAtLeast(smallBombItem, 1)) {
            playersWithSmallBomb.add(player)
            if (sendMessage) player.sendMessage("§cYou are carrying a bomb. This will slow you down when flying.")
            sentMessage = true
        } else playersWithSmallBomb.remove(player)

        if (player.inventory.containsAtLeast(mediumBombItem, 1)) {
            playersWithMediumBomb.add(player)
            if (sendMessage && !sentMessage) player.sendMessage("§cYou are carrying a bomb. This will slow you down when flying.")
            sentMessage = true
        } else playersWithMediumBomb.remove(player)

        if (player.inventory.containsAtLeast(largeBombItem, 1)) {
            playersWithLargeBomb.add(player)
            if (sendMessage && !sentMessage) player.sendMessage("§cYou are carrying a bomb. This will slow you down when flying.")
        } else playersWithLargeBomb.remove(player)
    }

    private fun checkSpeed() {
        playersWithLargeBomb.forEach { player ->
            if (player.isGliding && player.velocity.length() > largeSpeedLimit) {
                player.velocity = player.velocity.normalize().multiply(largeSpeedLimit)
            }
        }
        playersWithMediumBomb.forEach { player ->
            if (player.isGliding && player.velocity.length() > mediumSpeedLimit) {
                player.velocity = player.velocity.normalize().multiply(mediumSpeedLimit)
            }
        }
        playersWithSmallBomb.forEach { player ->
            if (player.isGliding && player.velocity.length() > smallSpeedLimit) {
                player.velocity = player.velocity.normalize().multiply(smallSpeedLimit)
            }
        }
    }

    private fun checkSpeedOf(player: Player) {
        if (player.isGliding && playersWithLargeBomb.contains(player) && player.velocity.length() > largeSpeedLimit) {
            player.velocity = player.velocity.normalize().multiply(largeSpeedLimit)
        } else if (player.isGliding && playersWithMediumBomb.contains(player) && player.velocity.length() > mediumSpeedLimit) {
            player.velocity = player.velocity.normalize().multiply(mediumSpeedLimit)
        } else if (player.isGliding && playersWithSmallBomb.contains(player) && player.velocity.length() > smallSpeedLimit) {
            player.velocity = player.velocity.normalize().multiply(smallSpeedLimit)
        }
    }

    private fun explosionCheck() {
        if (explosionCheckTask != null) return
        explosionCheckTask =
            Bukkit.getScheduler().scheduleSyncRepeatingTask(Bukkit.getPluginManager().getPlugin("Icarus")!!, {
                if (activeBombs.isEmpty()) {
                    Bukkit.getScheduler().cancelTask(explosionCheckTask!!)
                    explosionCheckTask = null
                    return@scheduleSyncRepeatingTask
                }
                val iterator = activeBombs.iterator()
                while (iterator.hasNext()) {
                    val bomb = iterator.next()
                    val block = bomb.location.subtract(0.0, 1.0, 0.0).block.type
                    if (bomb.isDead || bomb.fuseTicks < 1) {
                        bomb.world.createExplosion(bomb.location, bomb.yield)
                        iterator.remove()
                        continue
                    }
                    if (block != Material.AIR && block != Material.CAVE_AIR && block != Material.VOID_AIR) {
                        bomb.world.spawnParticle(
                            org.bukkit.Particle.FLAME,
                            bomb.location,
                            (bomb.yield * 20).toInt(),
                            1.0,
                            1.0,
                            1.0,
                            0.5
                        )
                        bomb.fuseTicks = 0
                        bomb.world.createExplosion(bomb.location, bomb.yield)
                        bomb.remove()
                        iterator.remove()
                    }
                }
            }, 0, 1)
    }

    private fun reloadRockets() {
        Bukkit.getWorld("world")!!.entities.forEach { entity ->
            if (entity.scoreboardTags.contains("SAM_rocket")) {
                rockets[entity as SpectralArrow] = entity.location
            }
        }
    }

    private fun checkRockets() {
        if (rockets.isEmpty()) {
            stopRocketTask()
            return
        }
        val rocketsToRemove = mutableListOf<SpectralArrow>()
        rockets.forEach { (rocket, spawnLocation) ->
            if (rocket.isDead || rocket.velocity.length() < 0.2) {
                rocket.remove()
                rocketsToRemove.add(rocket)
            }
            if (rocket.location.distance(spawnLocation) > launcherRange) {
                rocket.world.createExplosion(rocket.location, 3f, false, true, rocket)
                rocket.remove()
            }
            rocket.getNearbyEntities(5.0, 5.0, 5.0).forEach { entity ->
                if (entity.scoreboardTags.contains("bomb")) {
                    (entity as TNTPrimed).fuseTicks = 0
                }
            }
        }
        rocketsToRemove.forEach { rockets.remove(it) }
    }

    private fun startRocketTask() {
        if (rocketCheckID != null) return
        rocketCheckID =
            Bukkit.getScheduler().scheduleSyncRepeatingTask(Bukkit.getPluginManager().getPlugin("Icarus")!!, {
                checkRockets()
            }, 0, 3)
    }

    private fun stopRocketTask() {
        if (rocketCheckID == null) return
        Bukkit.getScheduler().cancelTask(rocketCheckID!!)
        rocketCheckID = null
    }

    private fun spawnBomb(player: Player, yield: Float, fuseTime: Int) {
        val bomb = player.world.spawn(player.location, TNTPrimed::class.java)
        bomb.addScoreboardTag("bomb")
        bomb.fuseTicks = fuseTime
        bomb.yield = yield
        bomb.isGlowing = true
        bomb.velocity = player.location.direction.multiply(0.75)
        activeBombs.add(bomb)
        explosionCheck()
    }

    @EventHandler
    private fun onPlayerGlide(event: PlayerMoveEvent) {
        checkSpeedOf(event.player)
    }

    @EventHandler
    private fun onItemDrop(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.CREEPER || event.entity.killer !is Player || event.entity.killer!!.inventory.itemInMainHand.containsEnchantment(
                Enchantment.FORTUNE
            )
        ) return
        val player = event.entity.killer!!
        if (java.util.Random().nextInt(0, ammoniumChance) == 0 && dropAmmonium) {
            event.drops.add(ammoniumNitrate)
            if (!player.hasDiscoveredRecipe(
                    NamespacedKey(
                        Bukkit.getPluginManager().getPlugin("Icarus")!!, "medium_bomb"
                    )
                )
            ) {
                player.discoverRecipe(
                    NamespacedKey(
                        Bukkit.getPluginManager().getPlugin("Icarus")!!, "medium_bomb"
                    )
                )
            }
        }
        if (Random.nextInt(0, plutoniumChance) == 0 && dropPlutonium) {
            event.drops.add(plutoniumCore)
            if (!player.hasDiscoveredRecipe(
                    NamespacedKey(
                        Bukkit.getPluginManager().getPlugin("Icarus")!!, "large_bomb"
                    )
                )
            ) {
                player.discoverRecipe(
                    NamespacedKey(
                        Bukkit.getPluginManager().getPlugin("Icarus")!!, "large_bomb"
                    )
                )
                val component = TextComponent("${player.name}:§a Hmm... Interesting")
                component.hoverEvent = HoverEvent(
                    HoverEvent.Action.SHOW_TEXT, Text("§aDiscover a plutonium core. \nWhat could this be used for?")
                )
                Bukkit.spigot().broadcast(component)
            }
        }
    }

    @EventHandler
    private fun onBombDrop(event: PlayerInteractEvent) {
        if (!event.player.isGliding || event.action != Action.RIGHT_CLICK_AIR || event.item?.itemMeta == null) return
        when (event.item!!.itemMeta) {
            smallBombItem.itemMeta -> {
                if(!smallBombEnabled) {
                    event.player.sendMessage("§cSmall bombs are disabled")
                    return
                }
                spawnBomb(event.player, smallBombYield.toFloat(), fuseTicks)
                if (event.player.gameMode != GameMode.CREATIVE) event.item!!.amount--
                checkItems(event.player)
            }

            mediumBombItem.itemMeta -> {
                if(!mediumBombEnabled) {
                    event.player.sendMessage("§cMedium bombs are disabled")
                    return
                }
                spawnBomb(event.player, mediumBombYield.toFloat(), fuseTicks)
                if (event.player.gameMode != GameMode.CREATIVE) event.item!!.amount--
                checkItems(event.player)
            }

            largeBombItem.itemMeta -> {
                if (!largeBombEnabled) {
                    event.player.sendMessage("§cLarge bombs are disabled")
                    return
                }
                spawnBomb(event.player, largeBombYield.toFloat(), fuseTicks)
                if (event.player.gameMode != GameMode.CREATIVE) event.item!!.amount--
                checkItems(event.player)
            }
        }
    }

    @EventHandler
    private fun onBombExplode(event: EntityExplodeEvent) {
        if (event.entity.scoreboardTags.contains("bomb")) {
            if (base.isProtected(event.entity.location)) {
                event.isCancelled = true
                return
            }
        }
    }

    @EventHandler
    private fun onRocketLaunch(event: PlayerInteractEvent) {
        if (event.item == null || event.item!!.itemMeta == null) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.item!!.itemMeta!!.displayName != rocketLauncherItem.itemMeta!!.displayName) return
        if (event.player.inventory.containsAtLeast(rocketLauncherAmmo, 1)) {
            event.player.inventory.removeItem(rocketLauncherAmmo)
        } else {
            event.player.sendMessage("§cNo ammo left")
            event.isCancelled = true
            return
        }
        val rocket = event.player.launchProjectile(org.bukkit.entity.SpectralArrow::class.java)
        rocket.velocity = event.player.location.direction.multiply(4)
        rocket.addScoreboardTag("SAM_rocket")
        rocket.isGlowing = true
        rocket.setGravity(false)
        rockets[rocket] = rocket.location
        startRocketTask()
        event.isCancelled = true
    }

    @EventHandler
    private fun rocketImpactEvent(event: ProjectileHitEvent) {
        if (event.entity.scoreboardTags.contains("SAM_rocket")) {
            event.entity.world.createExplosion(event.entity.location, rocketYield)
            event.entity.remove()
        }
    }

    @EventHandler
    private fun onBlockPlace(event: BlockPlaceEvent) {
        if (event.itemInHand.itemMeta == null) return
        val itemName = event.itemInHand.itemMeta!!.displayName
        if (itemName != smallBombItem.itemMeta!!.displayName && itemName != mediumBombItem.itemMeta!!.displayName && itemName != largeBombItem.itemMeta!!.displayName) return
        event.isCancelled = true
    }

    @EventHandler
    private fun onCraft(event: CraftItemEvent) {
        if (event.recipe.result.itemMeta == null) return
        val itemName = event.recipe.result.itemMeta!!.displayName
        if (itemName == largeBombItem.itemMeta!!.displayName) {
            if (Random.nextInt(0, 20) == 19) {
                event.whoClicked.damage(500.0)
                Bukkit.getWorld("world")!!.createExplosion(event.whoClicked.location, 120f)
                Bukkit.getServer()
                    .broadcastMessage("${event.whoClicked.name} has been vaporized while trying to craft a Hydrogen Bomb")
            }
        }
    }

    @EventHandler
    private fun playerStartGliding(event: EntityToggleGlideEvent) {
        if (event.entity !is Player) return
        if (event.isGliding) {
            val player = event.entity as Player
            checkItems(player, true)
        }
    }

    @EventHandler
    private fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (event.itemDrop.itemStack.itemMeta == null) return
        val itemName = event.itemDrop.itemStack.itemMeta!!.displayName
        if (itemName != smallBombItem.itemMeta!!.displayName && itemName != mediumBombItem.itemMeta!!.displayName && itemName != largeBombItem.itemMeta!!.displayName) return
        checkItems(event.player)
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!event.player.hasDiscoveredRecipe(
                NamespacedKey(
                    Bukkit.getPluginManager().getPlugin("Icarus")!!, "rocket_launcher"
                )
            )
        ) {
            event.player.discoverRecipe(
                NamespacedKey(
                    Bukkit.getPluginManager().getPlugin("Icarus")!!, "rocket_launcher"
                )
            )
            event.player.discoverRecipe(
                NamespacedKey(
                    Bukkit.getPluginManager().getPlugin("Icarus")!!, "rocket_launcher_ammo"
                )
            )
        }
        if (!event.player.hasDiscoveredRecipe(
                NamespacedKey(
                    Bukkit.getPluginManager().getPlugin("Icarus")!!, "small_bomb"
                )
            )
        ) {
            event.player.discoverRecipe(
                NamespacedKey(
                    Bukkit.getPluginManager().getPlugin("Icarus")!!, "small_bomb"
                )
            )
        }
    }

    override fun onTabComplete(p0: CommandSender, p1: Command, p2: String, p3: Array<out String>): MutableList<String> {
        if (p3.size == 1) {
            return mutableListOf("spawn", "give")
        }
        return when {
            p3.size == 2 && p3[0] == "spawn" -> {
                mutableListOf("50", "100", "150")
            }

            p3.size == 2 && p3[0] == "give" -> {
                mutableListOf("small", "medium", "large", "ammonium", "plutonium", "rocketLauncher", "launcherAmmo")
            }

            else -> mutableListOf()
        }
    }
}