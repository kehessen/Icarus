package me.kehessen.customplugin

import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import kotlin.random.Random

// anything over yield 5 can destroy turrets
// wanted to do a custom advancement, but it would require a dependency which is annoying
@Suppress("Duplicates", "unused")
class Bomb : CommandExecutor, TabCompleter, Listener {

    private var smallBombItem = CustomItem(
        Material.TNT,
        "§r§c50kg Bomb",
        "§fRight click while flying to drop",
        "§7Can be used to destroy turrets",
        "§7Will slow you down when flying"
    )
    private var mediumBombItem = CustomItem(
        Material.TNT,
        "§r§c100kg Bomb",
        "§fRight click while flying to drop",
        "§7Can be used to destroy turrets",
        "§7Will slow you down when flying"
    )
    private var largeBombItem = CustomItem(
        Material.TNT,
        "§r§c§lHydrogen Bomb",
        "§fRight click while flying to drop",
        "§7Can be used to destroy turrets, or anything else for that matter",
        "§7Will slow you down when flying"
    )

    private var smallBombYield = 6
    private var mediumBombYield = 20
    private var largeBombYield = 75

    private var smallSpeedLimit = 0.8
    private var mediumSpeedLimit = 0.6
    private var largeSpeedLimit = 0.4

    private var playersWithSmallBomb = mutableSetOf<Player>()
    private var playersWithMediumBomb = mutableSetOf<Player>()
    private var playersWithLargeBomb = mutableSetOf<Player>()

    private val activeTasks = hashMapOf<TNTPrimed, Int>()

    // both items can only be obtained form creepers, ammonium nitrate has a 10% drop chance, plutonium core has a 1% drop chance
    // items won't drop if the player has looting
    private var ammoniumNitrate = CustomItem(Material.SUGAR, "§r§cAmmonium Nitrate", "§fUsed to craft Bombs")
    private var plutoniumCore = CustomItem(
        Material.FIREWORK_STAR,
        "§r§c§lPlutonium Core",
        "§fUsed to craft Hydrogen Bombs",
        "§7§oHas a 5% chance to explode when used for crafting"
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args[0]) {
            "spawn" -> {
                if (args.size != 2 || sender !is Player || args[1].toFloatOrNull() == null) {
                    sender.sendMessage("§cInvalid arguments")
                    return true
                }
                if (args[1].toFloat() > 150) {
                    sender.sendMessage("§cYou're gonna crash the server")
                    return true
                }
                if (args[1].toFloat() > 100) {
                    sender.sendMessage("§cI'm gonna do it but dont blame me if the server crashes")
                }
                val player = Bukkit.getPlayer(sender.name)
                val tnt = player!!.world.spawn(player.location, org.bukkit.entity.TNTPrimed::class.java)
                tnt.fuseTicks = 100
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
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("CustomPlugin")!!)

        addRecipes()
    }

    private fun addRecipes() {
        var recipe = ShapedRecipe(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CustomPlugin")!!, "small_bomb"), smallBombItem
        )
        recipe.shape("TT", "TT")
        recipe.setIngredient('T', Material.TNT)
        Bukkit.addRecipe(recipe)

        recipe = ShapedRecipe(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CustomPlugin")!!, "medium_bomb"), mediumBombItem
        )
        recipe.shape("TTT", "TAT", "TTT")
        recipe.setIngredient('T', RecipeChoice.ExactChoice(ItemStack(Material.TNT)))
        recipe.setIngredient('A', RecipeChoice.ExactChoice(ammoniumNitrate))
        Bukkit.addRecipe(recipe)

        recipe = ShapedRecipe(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CustomPlugin")!!, "large_bomb"), largeBombItem
        )
        recipe.shape("TTT", "TCT", "TTT")
        recipe.setIngredient('T', RecipeChoice.ExactChoice(mediumBombItem))
        recipe.setIngredient('C', RecipeChoice.ExactChoice(plutoniumCore))
        Bukkit.addRecipe(recipe)
    }

    private fun checkItems(players: List<Player>, sendMessage: Boolean = false) {
        players.forEach { player ->
            if (player.inventory.containsAtLeast(smallBombItem, 1)) {
                playersWithSmallBomb.add(player)
                if (sendMessage) player.sendMessage("§cYou are carrying a bomb. This will slow you down when flying.")
            } else playersWithSmallBomb.remove(player)

            if (player.inventory.containsAtLeast(mediumBombItem, 1)) {
                playersWithMediumBomb.add(player)
                if (sendMessage) player.sendMessage("§cYou are carrying a bomb. This will slow you down when flying.")
            } else playersWithMediumBomb.remove(player)

            if (player.inventory.containsAtLeast(largeBombItem, 1)) {
                playersWithLargeBomb.add(player)
                if (sendMessage) player.sendMessage("§cYou are carrying a bomb. This will slow you down when flying.")
            } else playersWithLargeBomb.remove(player)

        }
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

    private fun explosionCheck(bomb: TNTPrimed) {
        val task =
            Bukkit.getScheduler().scheduleSyncRepeatingTask(Bukkit.getPluginManager().getPlugin("CustomPlugin")!!, {

                val block = bomb.location.subtract(0.0, 1.0, 0.0).block.type
                if (block != Material.AIR && block != Material.WATER && block != Material.LAVA) {
                    Bukkit.getScheduler().cancelTask(activeTasks[bomb]!!)
                    bomb.fuseTicks = 0
                }
                if (bomb.isDead) {
                    Bukkit.getScheduler().cancelTask(activeTasks[bomb]!!)
                }

            }, 0, 2)
        activeTasks[bomb] = task
    }

    public fun getPlayersWithBombs(): List<Player> {
        return listOf(playersWithSmallBomb, playersWithMediumBomb, playersWithLargeBomb).flatten()
    }

    @EventHandler
    private fun onPlayerGlide(event: PlayerMoveEvent) {
        checkSpeed()
    }

    @EventHandler
    private fun onItemDrop(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.CREEPER || event.entity.killer !is Player || event.entity.killer!!.inventory.itemInMainHand.containsEnchantment(
                Enchantment.LOOT_BONUS_MOBS
            )
        ) return
        val player = event.entity.killer!!
        if (Random.nextInt(0, 10) == 9) {
            event.drops.add(ammoniumNitrate)
            if (!player.hasDiscoveredRecipe(
                    NamespacedKey(
                        Bukkit.getPluginManager().getPlugin("CustomPlugin")!!, "medium_bomb"
                    )
                )
            ) {
                player.discoverRecipe(
                    NamespacedKey(
                        Bukkit.getPluginManager().getPlugin("CustomPlugin")!!, "medium_bomb"
                    )
                )
                player.sendMessage("§aThe 100kg bomb recipe has been added to your recipe book. Reconnect to see it.")
            }
        }
        if (Random.nextInt(0, 100) == 99) {
            event.drops.add(plutoniumCore)
            if (!player.hasDiscoveredRecipe(
                    NamespacedKey(
                        Bukkit.getPluginManager().getPlugin("CustomPlugin")!!, "large_bomb"
                    )
                )
            ) {
                player.discoverRecipe(
                    NamespacedKey(
                        Bukkit.getPluginManager().getPlugin("CustomPlugin")!!, "large_bomb"
                    )
                )
                player.sendMessage("§aThe Hydrogen bomb recipe has been added to your recipe book. Reconnect to see it.")
                val component = TextComponent("${player.name}:§a Hmm... Interesting")
                component.hoverEvent = HoverEvent(
                    HoverEvent.Action.SHOW_TEXT, Text("§aDiscover a plutonium core. \nWhat could this be used for?")
                )
                Bukkit.spigot().broadcast(component)
            }
        }
    }

    @EventHandler
    private fun onRightClick(event: PlayerInteractEvent) {
        if (!event.player.isGliding || event.action != Action.RIGHT_CLICK_AIR) return
        when (event.player.inventory.itemInMainHand.itemMeta!!.displayName) {
            smallBombItem.itemMeta!!.displayName -> {
                val bmb = event.player.world.spawn(event.player.location, TNTPrimed::class.java)
                bmb.fuseTicks = 100
                bmb.yield = smallBombYield.toFloat()
                explosionCheck(bmb)
                event.player.inventory.itemInMainHand.amount -= 1
                checkItems(listOf(event.player))
            }

            mediumBombItem.itemMeta!!.displayName -> {
                val bmb = event.player.world.spawn(event.player.location, TNTPrimed::class.java)
                bmb.fuseTicks = 100
                bmb.yield = mediumBombYield.toFloat()
                explosionCheck(bmb)
                event.player.inventory.itemInMainHand.amount -= 1
                checkItems(listOf(event.player))
            }

            largeBombItem.itemMeta!!.displayName -> {
                val bmb = event.player.world.spawn(event.player.location, TNTPrimed::class.java)
                bmb.fuseTicks = 100
                bmb.yield = largeBombYield.toFloat()
                explosionCheck(bmb)
                event.player.inventory.itemInMainHand.amount -= 1
                checkItems(listOf(event.player))
            }
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
        if (itemName != largeBombItem.itemMeta!!.displayName) return
        if (Random.nextInt(0, 20) == 19) {
            event.whoClicked.damage(500.0)
            Bukkit.getWorld("world")!!.createExplosion(event.whoClicked.location, 120f)
            Bukkit.getServer()
                .broadcastMessage("${event.whoClicked.name} has been vaporized while trying to craft a Hydrogen Bomb")
        }
    }

    @EventHandler
    private fun playerStartGliding(event: EntityToggleGlideEvent) {
        if (event.entity !is Player) return
        if (event.isGliding) {
            val player = event.entity as Player
            checkItems(listOf(player), true)
        }
    }

    @EventHandler
    private fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (event.itemDrop.itemStack.itemMeta == null) return
        val itemName = event.itemDrop.itemStack.itemMeta!!.displayName
        if (itemName != smallBombItem.itemMeta!!.displayName && itemName != mediumBombItem.itemMeta!!.displayName && itemName != largeBombItem.itemMeta!!.displayName) return
        checkItems(listOf(event.player))
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
                mutableListOf("small", "medium", "large", "ammonium", "plutonium")
            }

            else -> mutableListOf()
        }
    }
}