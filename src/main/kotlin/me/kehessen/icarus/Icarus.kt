package me.kehessen.icarus

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Scoreboard

class Icarus : JavaPlugin(), Listener, CommandExecutor, TabCompleter {

    // used for other classes, so they need to be initialized first
    private val menuHandler = MenuHandler(this)
    private val combatTime = CombatTime(this, config)

    private val chat = Chat()
    private val turretHandler = TurretHandler(this, config, menuHandler)
    private val simpleCommandHandler = SimpleCommandHandler(combatTime, turretHandler)
    private val tpaHandler = TpaHandler(combatTime, config)
    private val bomb = Bomb(config)
    private val playerMounting = PlayerMounting(config)
    private val smokeGrenade = SmokeGrenade(config)
    private val airstrike = Airstrike(config)
    private val timedAccess = TimedAccess(config)

    private var sb: Scoreboard? = null

    override fun onLoad() {
        saveDefaultConfig()
    }

    override fun onEnable() {
        config.options().copyDefaults(true)
        server.pluginManager.registerEvents(this, this)
        Bukkit.getPluginCommand("customitem")?.setExecutor(this)

        sb = Bukkit.getScoreboardManager()!!.mainScoreboard

        if (sb!!.getTeam("Default") == null) {
            sb!!.registerNewTeam("Default")
        }

        combatTime.start()
        menuHandler.start()

        chat.start()
        turretHandler.start()
        simpleCommandHandler.start()
        tpaHandler.start()
        bomb.start()
        playerMounting.start()
        smokeGrenade.start()
        airstrike.start()
        timedAccess.start()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§cInvalid arguments")
            return true
        }
        if (sender !is Player) {
            sender.sendMessage("§cInvalid sender")
            return true
        }
        val player = Bukkit.getPlayer(sender.name)
        when (command.name) {
            "customitem" -> {
                val amount = if (args.size == 2) args[1].toInt() else 1
                val item: ItemStack?
                when (args[0]) {
                    "turret" -> item = turretHandler.customItem
                    "customenderpearl" -> item = turretHandler.customEnderPearl
                    "smokegrenade" -> item = smokeGrenade.smokeGrenade
                    "smallbomb" -> item = bomb.smallBombItem
                    "mediumbomb" -> item = bomb.mediumBombItem
                    "largebomb" -> item = bomb.largeBombItem
                    "mountinggun" -> item = playerMounting.customWeapon
                    "mountingammo" -> item = playerMounting.customAmmo
                    "rocketlauncher" -> item = bomb.rocketLauncherItem
                    "rocketlauncherammo" -> item = bomb.rocketLauncherAmmo
                    "ammonium" -> item = bomb.ammoniumNitrate
                    "plutonium" -> item = bomb.plutoniumCore
                    "flares" -> item = turretHandler.flares
                    "airstrike" -> item = airstrike.item

                    else -> {
                        sender.sendMessage("§cInvalid arguments")
                        return true
                    }
                }
                item.amount = amount
                player!!.inventory.addItem(item)
            }
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender, command: Command, alias: String, args: Array<out String>
    ): MutableList<String> {
        when (command.name) {
            "customitem" -> {
                return if (args.size == 1) mutableListOf(
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
                    "plutonium",
                    "flares",
                    "airstrike"
                )
                else mutableListOf("")
            }

        }

        return mutableListOf("")
    }
}
