package me.kehessen.icarus.misc

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

@Suppress("unused")
class CombatTime(private val plugin: JavaPlugin, config: FileConfiguration) : Listener {
    private val combatTime = config.getInt("combat.combat-time")
    private var combatTimeMap = hashMapOf<UUID, Int>()
    private var currentlyInCombat = hashMapOf<UUID, UUID>()

    fun start() {
        startTask()
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