package me.kehessen.customplugin

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerCommandPreprocessEvent

@Suppress("unused")
class SimpleCommandHandler(private val combatTime: CombatTime, val trtHan: TurretHandler) : CommandExecutor,
    TabCompleter {
    private var forbiddenCombatCommands = listOf("spawn", "tpa")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return false
        when (command.name) {
            "spawn" -> {
                if (combatTime.getCombatTime(sender.uniqueId) != 0) {
                    sender.sendMessage("§cYou can't use this command while in combat")
                    return true
                }
                if (args.size > 1) {
                    sender.sendMessage("§cInvalid arguments")
                    return true
                }
                if (args.size == 1 && args[0].lowercase() == "set") {
                    Bukkit.getWorld("world")?.let {
                        Bukkit.getWorld("world")?.setSpawnLocation(sender.location)
                    }
                    val sp = Bukkit.getWorld("world")?.spawnLocation
                    sender.sendMessage("§aSpawn set to ${sp?.x} ${sp?.y} ${sp?.z}")
                } else
                    sender.teleport(sender.world.spawnLocation)
                return true
            }

            "announce" -> {
                if (args.isEmpty()) {
                    sender.sendMessage("§cInvalid arguments")
                    return true
                }
                Bukkit.broadcastMessage("§4§l${args.joinToString(" ")}")
                return true
            }
        }
        return false
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        when (command.name) {
            "spawn" -> if (sender.hasPermission("op")) return args.size.let {
                when (it) {
                    1 -> mutableListOf("set")
                    else -> mutableListOf("")
                }
            }
        }
        return null
    }

    fun start() {
        Bukkit.getPluginCommand("spawn")?.setExecutor(this)
        Bukkit.getPluginCommand("announce")?.setExecutor(this)
        Bukkit.getPluginCommand("test")?.setExecutor(this)
    }

    @EventHandler
    fun blockCMDs(event: PlayerCommandPreprocessEvent) {
        if (event.message.startsWith("/")) {
            val command = event.message.split(" ")[0].substring(1).lowercase()
            if (forbiddenCombatCommands.contains(command) && combatTime.getCombatTime(event.player.uniqueId) != null) {
                event.player.sendMessage("§cYou can't use this command while in combat")
                event.isCancelled = true
            }
        }
    }
}