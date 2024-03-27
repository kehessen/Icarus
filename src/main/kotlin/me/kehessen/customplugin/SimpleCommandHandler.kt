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
class SimpleCommandHandler(private val combatTime: CombatTime) : CommandExecutor, TabCompleter {
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
                    return false
                }
                if (args.size == 1 && args[0].lowercase() == "set") {
                    Bukkit.getWorld("world")?.let {
                        Bukkit.getWorld("world")?.setSpawnLocation(sender.location)
                    }
                    val sp = Bukkit.getWorld("world")?.spawnLocation
                    sender.sendMessage("§aSpawn set to ${sp?.x} ${sp?.y} ${sp?.z}")
                } else
                    Bukkit.getWorld("world")?.let { sender.teleport(it.spawnLocation) }
            }

            "announce" -> {
                if (args.isEmpty()) {
                    sender.sendMessage("§cInvalid arguments")
                    return false
                }
                Bukkit.broadcastMessage("§4§l${args.joinToString(" ")}")
            }

            "test" -> {
                sender.sendMessage("§aCurrently not used")
            }

        }
        return true
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