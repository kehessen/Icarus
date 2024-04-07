package me.kehessen.customplugin

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Scoreboard

@Suppress("unused")
class CustomPlugin : JavaPlugin(), Listener, CommandExecutor, TabCompleter {

    // used for other classes
    private val menuHandler = MenuHandler(this)
    private val combatTime = CombatTime(this, config)


    private val turretHandler = TurretHandler(this, config, menuHandler)
    private val simpleCommandHandler = SimpleCommandHandler(combatTime, turretHandler)
    private val tpaHandler = TpaHandler(combatTime, config)
    private val bomb = Bomb()
    private val playerMounting = PlayerMounting(config)
    private val smokeGrenade = SmokeGrenade()

    private var sb: Scoreboard? = null


    override fun onEnable() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        reloadConfig()
        server.pluginManager.registerEvents(this, this)
        Bukkit.getPluginCommand("customitem")?.setExecutor(this)

        sb = Bukkit.getScoreboardManager()!!.mainScoreboard

        if (sb!!.getTeam("Default") == null) {
            sb!!.registerNewTeam("Default")
        }

        combatTime.start()
        menuHandler.start()
        turretHandler.start()
        simpleCommandHandler.start()
        tpaHandler.start()
        bomb.start()
        playerMounting.start()
        smokeGrenade.start()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name == "customitem") {
            if (args.isEmpty()) {
                sender.sendMessage("§cInvalid arguments")
                return true
            }
            if (sender !is Player) {
                sender.sendMessage("§cInvalid sender")
                return true
            }
            val player = Bukkit.getPlayer(sender.name)
            when (args[0]) {
                "turret" -> player!!.inventory.addItem(turretHandler.customItem)
                "customenderpearl" -> player!!.inventory.addItem(turretHandler.customEnderPearl)
                "smokegrenade" -> player!!.inventory.addItem(smokeGrenade.smokeGrenade)
                "smallbomb" -> player!!.inventory.addItem(bomb.smallBombItem)
                "mediumbomb" -> player!!.inventory.addItem(bomb.mediumBombItem)
                "largebomb" -> player!!.inventory.addItem(bomb.largeBombItem)
                "mountinggun" -> player!!.inventory.addItem(playerMounting.customWeapon)
                "mountingammo" -> player!!.inventory.addItem(playerMounting.customAmmo)
                "rocketlauncher" -> player!!.inventory.addItem(bomb.rocketLauncherItem)
                "rocketlauncherammo" -> player!!.inventory.addItem(bomb.rocketLauncherAmmo)
                "ammonium" -> player!!.inventory.addItem(bomb.ammoniumNitrate)
                "plutonium" -> player!!.inventory.addItem(bomb.plutoniumCore)

                else -> sender.sendMessage("§cInvalid arguments")
            }
        }
        return true
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        event.joinMessage = "§2+ ${event.player.name}"
        if (event.player.scoreboard.getEntryTeam(event.player.name) == null) {
            event.player.scoreboard.getTeam("Default")?.addEntry(event.player.name)
        }
    }

    @EventHandler
    private fun onPlayerChat(event: AsyncPlayerChatEvent) {
        event.format = "§7" + event.player.name + ":§f " + event.message
    }

    @EventHandler
    private fun onPlayerLeave(event: PlayerQuitEvent) {
        event.quitMessage = "§7- " + event.player.name
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        return when (args.size) {
            1 -> mutableListOf(
                "turret",
                "customenderpearl",
                "smokegrenade",
                "smallbomb",
                "mediumbomb",
                "largebomb",
                "mountinggun",
                "mountingammo",
                "rocketlauncher",
                "rocketlauncherammo",
                "ammonium",
                "plutonium"
            )

            else -> mutableListOf("")
        }
    }
}
