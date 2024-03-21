package me.kehessen.customplugin


import me.kehessen.customplugin.turret.ParticleTurretHandler
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


    private val combatTime = CombatTime(this)
    private val commandHandler = CommandHandler(combatTime)
    private val tpaHandler = TpaHandler(combatTime)
    private val turretHandler = TurretHandler(this)
    private val particleTurretHandler = ParticleTurretHandler(this)

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)

        combatTime.startTask()

        turretHandler.reloadTurrets()
        turretHandler.startTask()

        particleTurretHandler.reloadTurrets()
        particleTurretHandler.startTask()

        server.pluginManager.registerEvents(combatTime, this)
        server.pluginManager.registerEvents(particleTurretHandler, this)

        getCommand("spawn")?.setExecutor(commandHandler)
        getCommand("spawn")?.tabCompleter = commandHandler

        getCommand("announce")?.setExecutor(commandHandler)

        getCommand("tpa")?.setExecutor(tpaHandler)
        getCommand("tpaccept")?.setExecutor(tpaHandler)

        getCommand("combattime")?.setExecutor(combatTime)

        getCommand("spawnturret")?.setExecutor(turretHandler)
        getCommand("removeallturrets")?.setExecutor(turretHandler)

        getCommand("spawnparticleturret")?.setExecutor(particleTurretHandler)
        getCommand("removeallparticleturrets")?.setExecutor(particleTurretHandler)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        event.joinMessage = "§2+ ${event.player.name}"
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        event.format = "§7" + event.player.name + ":§f " + event.message
    }

    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent) {
        event.quitMessage = "§7- " + event.player.name
    }
}
