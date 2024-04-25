package me.kehessen.icarus.misc

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class DisableBeaconBeam : Listener, CommandExecutor {

    private val disabledPlayers = mutableListOf<Player>()
    private var taskID: Int? = null


    fun start() {
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("Icarus")!!)
        Bukkit.getPluginCommand("togglebeaconbeam")?.setExecutor(this)
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.scoreboardTags.contains("disablebeaconbeam")) {
                disabledPlayers.add(player)
            }
        }
    }

    private fun disableBeams(player: Player) {
        val viewDistance = player.clientViewDistance.toDouble()
        val material = Material.AIR
        player.world.getNearbyEntities(player.location, viewDistance, viewDistance, viewDistance)
            .filterIsInstance<ArmorStand>()
            .filter { it.scoreboardTags.contains("beacon") }
            .forEach {
                player.sendBlockChange(it.location, material.createBlockData())
            }
    }

    private fun enableBeams(player: Player) {
        val viewDistance = player.clientViewDistance.toDouble()
        player.world.getNearbyEntities(player.location, viewDistance, viewDistance, viewDistance)
            .filterIsInstance<ArmorStand>()
            .filter { it.scoreboardTags.contains("beacon") }
            .forEach {
                player.sendBlockChange(it.location, it.world.getBlockAt(it.location).blockData)
            }
    }

    private fun onInterval() {
        disabledPlayers.forEach { disableBeams(it) }
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        if (event.player.scoreboardTags.contains("disablebeaconbeam")) {
            disabledPlayers.add(event.player)
        }
        if (disabledPlayers.isNotEmpty() && taskID == null) {
            taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(Bukkit.getPluginManager().getPlugin("Icarus")!!, {
                onInterval()
            }, 0, 20 * 5)
        }
    }

    @EventHandler
    private fun onPlayerLeave(event: PlayerQuitEvent) {
        disabledPlayers.remove(event.player)
        if (disabledPlayers.isEmpty() && taskID != null) {
            Bukkit.getScheduler().cancelTask(taskID!!)
            taskID = null
        }
    }

    @EventHandler
    private fun onBeaconPlace(event: BlockPlaceEvent) {
        if (event.blockPlaced.type != Material.BEACON) return
        val armorStand: ArmorStand =
            event.blockPlaced.world.spawn(event.blockPlaced.location.add(0.5, -1.0, 0.5), ArmorStand::class.java)
        armorStand.isInvisible = true
        armorStand.isSmall = true
        armorStand.setGravity(false)
        armorStand.scoreboardTags.add("beacon")
        armorStand.removeWhenFarAway = false
    }

    @EventHandler
    private fun onBeaconBreak(event: BlockBreakEvent) {
        val armorStand: ArmorStand = event.block.world.getNearbyEntities(event.block.location, 1.0, 1.0, 1.0)
            .filterIsInstance<ArmorStand>()
            .firstOrNull { it.scoreboardTags.contains("beacon") } ?: return
        if (!armorStand.isValid) {
            Bukkit.getLogger().warning("Armorstand associated with beacon at ${event.block.location} is not valid")
            return
        }
        armorStand.remove()
    }

    override fun onCommand(p0: CommandSender, p1: Command, p2: String, p3: Array<out String>): Boolean {
        if (p0 !is Player) return false
        if (disabledPlayers.contains(p0)) {
            disabledPlayers.remove(p0)
            p0.removeScoreboardTag("disablebeaconbeam")
            p0.sendMessage("§aBeacon beam enabled")
            enableBeams(p0)
        } else {
            disabledPlayers.add(p0)
            p0.addScoreboardTag("disablebeaconbeam")
            p0.sendMessage("§cBeacon beam disabled")
            disableBeams(p0)
        }
        return true
    }
}