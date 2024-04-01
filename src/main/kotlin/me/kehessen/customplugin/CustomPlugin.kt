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

    // maybe make a plane

    // used for other classes
    private val menuHandler = MenuHandler(this)
    private val combatTime = CombatTime(this, config)


    private val turretHandler = TurretHandler(this, config, menuHandler)
    private val simpleCommandHandler = SimpleCommandHandler(combatTime, turretHandler)
    private val tpaHandler = TpaHandler(combatTime, config)


    override fun onEnable() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        reloadConfig()
        server.pluginManager.registerEvents(this, this)

        combatTime.start()
        menuHandler.start()
        turretHandler.start()
        simpleCommandHandler.start()
        tpaHandler.start()
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
////            event.player.sendMessage(event.player.ping.toString())
//        }
//    }
}
