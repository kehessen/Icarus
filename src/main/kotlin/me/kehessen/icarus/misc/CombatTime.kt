package me.kehessen.icarus.misc

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

@Suppress("unused")
class CombatTime(private val plugin: JavaPlugin, config: FileConfiguration) : Listener, CommandExecutor {
    private val combatTime = config.getInt("combat.combat-time")
    private var combatTimeMap = hashMapOf<UUID, Int>()
    private var currentlyInCombat = hashMapOf<UUID, UUID>()
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.size != 1) {
            sender.sendMessage("§cInvalid arguments :(")
            return false
        }
        val playerUUID = Bukkit.getPlayer(args[0])?.uniqueId
        if (playerUUID == null) {
            sender.sendMessage("§cPlayer not found")
            return false
        }
        if (combatTimeMap[playerUUID] == null) {
            sender.sendMessage("§c${args[0]} is not in combat")
            return true
        }
        sender.sendMessage("§c${args[0]} is in combat for ${getCombatTime(playerUUID)} seconds")
        return true
    }

    fun start() {
        startTask()
        Bukkit.getPluginCommand("combattime")?.setExecutor(this)
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    private fun startTask() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            onInterval()
        }, 0, 20 * 1)
    }


    @EventHandler
    fun onPlayerHit(event: EntityDamageByEntityEvent) {
        if (event.damager is Player && event.entity is Player) {
            combatTimeMap[event.damager.uniqueId] = combatTime
            combatTimeMap[event.entity.uniqueId] = combatTime
            currentlyInCombat[event.damager.uniqueId] = event.entity.uniqueId
            currentlyInCombat[event.entity.uniqueId] = event.damager.uniqueId
        }
    }

    private fun onInterval() {
        val iterator = combatTimeMap.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value == 0) {
                iterator.remove()
                currentlyInCombat.remove(entry.key)
            } else if (Bukkit.getPlayer(entry.key)?.isOnline == true) {
                combatTimeMap[entry.key] = entry.value - 1
            }
        }
    }

    fun getCombatTime(playerUUID: UUID): Int {
        return if (combatTimeMap[playerUUID] == null) {
            0
        } else combatTimeMap[playerUUID]!!
    }

    fun getOpponentUUID(playerUUID: UUID): UUID? {
        return currentlyInCombat[playerUUID]
    }

}