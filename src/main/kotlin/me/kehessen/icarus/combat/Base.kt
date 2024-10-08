package me.kehessen.icarus.combat

import me.kehessen.icarus.util.CustomItem
import org.bukkit.*
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import kotlin.math.cos
import kotlin.math.sin

class Base(config: FileConfiguration) : Listener {
    private val range = config.getInt("Base.range")
    private val bombProtection = config.getBoolean("Base.bomb-protection")
    private val maxBases = 1 // current teleport implementation doesn't support multiple bases, feel like multiple bases aren't necessary
    private val disableBlockBreaking = config.getBoolean("Base.disable-block-breaking")
    private val disableBlockPlacing = config.getBoolean("Base.disable-block-placing")
    private val disableInteract = config.getBoolean("Base.disable-block-interacting")
    private val preventCreeper = config.getBoolean("Base.prevent-creeper-spawn")
    private val showParticles = config.getBoolean("Base.show-particles")

    internal val baseItem =
        CustomItem(Material.ARMOR_STAND, "§aBase", "§7Place to create a base", "§7Max bases per team: $maxBases")

    private val bases = mutableSetOf<ArmorStand>()

    private var sb: Scoreboard? = null

    fun start() {
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("Icarus")!!)

        sb = Bukkit.getScoreboardManager()!!.mainScoreboard

        reloadBases()

        addRecipe()
        
        if(showParticles) {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(Bukkit.getPluginManager().getPlugin("Icarus")!!, {
                bases.forEach {
                    displayBaseParticles(it.location)
                }
            }, 0, 100)
        }
    }

    fun isProtected(location: Location): Boolean {
        if (!bombProtection) return false
        return bases.any { it.world == location.world && it.location.distance(location) <= range.toDouble() }
    }

    fun isBase(loc: Location): Boolean {
        return bases.any { it.world == loc.world && it.location.distance(loc) <= range.toDouble() }
    }

    fun baseTeam(loc: Location): Team? {
        bases.forEach {
            if (it.world == loc.world && it.location.distance(loc) <= range.toDouble()) {
                return sb!!.getEntryTeam(it.uniqueId.toString())!!
            }
        }
        return null
    }
    
    fun getPlayerBase(player: Player): ArmorStand? {
        Bukkit.getPluginManager().getPlugin("Icarus")!!.logger.info(bases.toString())
        bases.forEach {
            if (sb!!.getEntryTeam(it.uniqueId.toString()) == sb!!.getEntryTeam(player.name)) {
                return it
            }
        }
        return null
    }
    
    private fun displayBaseParticles(loc: Location) {
        for (i in 0 until 360 step 1) {
            val x = cos(Math.toRadians(i.toDouble())) * range
            val z = sin(Math.toRadians(i.toDouble())) * range
            val startPosition = loc.clone().add(x, 0.0, z)
            loc.world!!.spawnParticle(Particle.HAPPY_VILLAGER, startPosition, 1)
        }
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
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.item == null || event.item!!.itemMeta == null) return
        if (event.item!!.itemMeta!!.displayName != baseItem.itemMeta!!.displayName) return

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
        armorStand.customName = "Base of ${sb!!.getEntryTeam(event.player.name)!!.name}"
        armorStand.isCustomNameVisible = true
        armorStand.removeWhenFarAway = false

        event.item!!.amount--

        event.isCancelled = true

        for (i in 0 until 360 step 1) {
            val x = cos(Math.toRadians(i.toDouble())) * range
            val z = sin(Math.toRadians(i.toDouble())) * range
            val startPosition = armorStand.location.clone().add(x, 0.0, z)
            event.player.world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, startPosition, 1)
        }
    }

    @EventHandler
    private fun onPickup(event: PlayerInteractAtEntityEvent) {
        if (!event.rightClicked.scoreboardTags.contains("BaseMarker")) return
        if (event.rightClicked !is ArmorStand) return
        if (!event.player.isSneaking) return
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
    private fun onPlayerJoin(event: PlayerJoinEvent) {
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

    @EventHandler
    private fun onBlockInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.clickedBlock == null) return
        if (!disableInteract) return
        if (baseTeam(event.clickedBlock!!.location) == null) return
        if (isBase(event.clickedBlock!!.location) && baseTeam(event.clickedBlock!!.location) != sb!!.getEntryTeam(event.player.name)!!) {
            event.isCancelled = true
        }
    }

    @EventHandler
    private fun onEntitySpawn(event: EntitySpawnEvent) {
        if (event.entityType == EntityType.CREEPER && preventCreeper && isProtected(event.location)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    private fun onCreeperExplode(event: EntityExplodeEvent) {
        if (event.entity.type == EntityType.CREEPER && isProtected(event.location)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    private fun onChunkLoad(event: ChunkLoadEvent) {
        event.chunk.entities.forEach {
            if (it is ArmorStand && it.scoreboardTags.contains("BaseMarker")) {
                bases.add(it)
            }
        }
    }
}