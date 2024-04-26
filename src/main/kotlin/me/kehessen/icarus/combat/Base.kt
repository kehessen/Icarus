package me.kehessen.icarus.combat

import me.kehessen.icarus.util.CustomItem
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.ArmorStand
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import kotlin.math.cos
import kotlin.math.sin

class Base(config: FileConfiguration) : Listener {
    private val range = config.getInt("Base.range")
    private val bombProtection = config.getBoolean("Base.bomb-protection")
    private val maxBases = config.getInt("Base.max-bases")
    private val disableBlockBreaking = config.getBoolean("Base.disable-block-breaking")
    private val disableBlockPlacing = config.getBoolean("Base.disable-block-placing")

    internal val baseItem =
        CustomItem(Material.ARMOR_STAND, "§aBase", "§7Place to create a base", "§7Max bases per team: $maxBases")

    private val bases = mutableSetOf<ArmorStand>()

    private var sb: Scoreboard? = null

    fun start() {
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("Icarus")!!)

        sb = Bukkit.getScoreboardManager()!!.mainScoreboard

        reloadBases()

        addRecipe()
    }

    fun isProtected(location: Location): Boolean {
        if (!bombProtection) return false
        return bases.any { it.location.distance(location) <= range.toDouble() }
    }

    fun isBase(loc: Location): Boolean {
        return bases.any { it.location.distance(loc) <= range.toDouble() }
    }

    fun baseTeam(loc: Location): Team? {
        bases.forEach {
            if (it.location.distance(loc) <= range.toDouble()) {
                return sb!!.getEntryTeam(it.uniqueId.toString())!!
            }
        }
        return null
    }

    private fun addRecipe() {
        val recipe = ShapedRecipe(NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "base"), baseItem)
        recipe.shape("III", "ISI", "III")
        recipe.setIngredient('I', Material.IRON_BLOCK)
        recipe.setIngredient('S', Material.CHEST)
        Bukkit.addRecipe(recipe)
    }

    private fun reloadBases() {
        bases.clear()
        Bukkit.getWorld("world")!!.entities.forEach {
            if (it is ArmorStand && it.scoreboardTags.contains("BaseMarker")) {
                bases.add(it)
            }
        }
    }

    @EventHandler
    private fun onPlace(event: PlayerInteractEvent) {
        if (event.item == null || event.item!!.itemMeta == null) return
        if (event.item!!.itemMeta!!.lore != baseItem.itemMeta!!.lore) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val team = sb!!.getEntryTeam(event.player.name)!!
        bases.forEach {
            if (sb!!.getEntryTeam(it.uniqueId.toString()) == team) {
                event.player.sendMessage("§cYou already have a base.")
                event.isCancelled = true
                return
            }
            if (it.location.distance(event.clickedBlock!!.location) <= (range * 2).toDouble()) {
                event.player.sendMessage("§cBase is too close to another base.")
                event.isCancelled = true
                return
            }
        }

        val armorStand =
            event.clickedBlock!!.world.spawn(event.clickedBlock!!.location.add(0.5, 1.0, 0.5), ArmorStand::class.java)
        sb!!.getEntryTeam(event.player.name)!!.addEntry(armorStand.uniqueId.toString())
        bases.add(armorStand)
        armorStand.scoreboardTags.add("BaseMarker")
        armorStand.isInvulnerable = true
        armorStand.setGravity(false)
        armorStand.customName = "Base"
        armorStand.isCustomNameVisible = true

        event.item!!.amount--

        event.isCancelled = true

        for (i in 0 until 360 step 1) {
            val x = cos(Math.toRadians(i.toDouble())) * range
            val z = sin(Math.toRadians(i.toDouble())) * range
            val startPosition = armorStand.location.clone().add(x, 0.0, z)
            event.player.world.spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, startPosition, 1)
        }
    }

    @EventHandler
    private fun onPickup(event: PlayerInteractAtEntityEvent) {
        if (!event.rightClicked.scoreboardTags.contains("BaseMarker")) return
        if (event.rightClicked !is ArmorStand) return
        if (sb!!.getEntryTeam(event.player.name) != sb!!.getEntryTeam(event.rightClicked.uniqueId.toString())) {
            event.player.sendMessage("§cYou can't interact with this base Marker.")
            return
        }
        // returns HashMap with item if inventory is full
        val item = event.player.inventory.addItem(baseItem)
        if (item.isEmpty()) {
            event.rightClicked.remove()
            bases.remove(event.rightClicked)
        }
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent){
        if (!event.player.hasDiscoveredRecipe(NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "base"))) {
            event.player.discoverRecipe(NamespacedKey(Bukkit.getPluginManager().getPlugin("Icarus")!!, "base"))
        }
    }

    @EventHandler
    private fun onBlockBreak(event: BlockBreakEvent) {
        if (!disableBlockBreaking) return
        if (isBase(event.block.location) && baseTeam(event.block.location) != sb!!.getEntryTeam(event.player.name)!!) {
            event.isCancelled = true
        }
    }

    @EventHandler
    private fun onBlockPlace(event: BlockPlaceEvent) {
        if (!disableBlockPlacing) return
        if (isBase(event.block.location) && baseTeam(event.block.location) != sb!!.getEntryTeam(event.player.name)!!) {
            event.isCancelled = true
        }
    }
}