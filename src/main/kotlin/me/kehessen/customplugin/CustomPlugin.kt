package me.kehessen.customplugin

import me.kehessen.customplugin.turret.TurretHandler
import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused")
class CustomPlugin : JavaPlugin(), Listener, CommandExecutor, TabCompleter {


    private val combatTime = CombatTime(this, config)
    private val simpleCommandHandler = SimpleCommandHandler(combatTime)
    private val tpaHandler = TpaHandler(combatTime, config)
    private val turretHandler = TurretHandler(this, config)

    override fun onEnable() {
        saveDefaultConfig()
        server.pluginManager.registerEvents(this, this)
        combatTime.startTask()

        config.options().copyDefaults(true)
        reloadConfig()

        turretHandler.reloadTurrets()
        turretHandler.startPerformanceCheckTask()
        turretHandler.startReachCheckTask()

        server.pluginManager.registerEvents(combatTime, this)
        server.pluginManager.registerEvents(turretHandler, this)

        getCommand("spawn")?.setExecutor(simpleCommandHandler)
        getCommand("announce")?.setExecutor(simpleCommandHandler)
        getCommand("test")?.setExecutor(simpleCommandHandler)

        getCommand("tpa")?.setExecutor(tpaHandler)
        getCommand("tpaccept")?.setExecutor(tpaHandler)

        getCommand("combattime")?.setExecutor(combatTime)

        getCommand("turret")?.setExecutor(turretHandler)
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        event.joinMessage = "§2+ ${event.player.name}"
    }

    @EventHandler
    private fun onPlayerChat(event: AsyncPlayerChatEvent) {
        event.format = "§7" + event.player.name + ":§f " + event.message
    }

    @EventHandler
    private fun onPlayerLeave(event: PlayerQuitEvent) {
        event.quitMessage = "§7- " + event.player.name
    }

//    @EventHandler
//    fun onPlayerGlide(event: PlayerMoveEvent) {
//        val speedLimit = 0.5
//        if (event.player.isGliding && event.player.velocity.length() > speedLimit) {
//            event.player.velocity = event.player.velocity.normalize().multiply(speedLimit)
//        }
//    }
}
