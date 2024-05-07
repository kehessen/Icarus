package me.kehessen.icarus

import me.kehessen.icarus.combat.*
import me.kehessen.icarus.misc.*
import me.kehessen.icarus.util.MenuHandler
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
    private val base = Base(config)

    private val chat = Chat()
    private val turretHandler = TurretHandler(this, config, menuHandler)
    private val simpleCommandHandler = SimpleCommandHandler(combatTime, turretHandler)
    private val tpaHandler = TpaHandler(combatTime, config)
    private val bomb = Bomb(config, base)
    private val playerMounting = PlayerMounting(config)
    private val smokeGrenade = SmokeGrenade(config)
    private val airstrike = Airstrike(config, base)
    private val timedAccess = TimedAccess(config)
    private val noBeam = DisableBeaconBeam()
    private val napalm = Napalm(config, base)

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

        if (Bukkit.getWorld("world") == null) {
            logger.severe("[Icarus] World 'world' not found, this plugin will not work as intended.")
            logger.severe("[Icarus] Disabling plugin")
            Bukkit.getPluginManager().disablePlugin(this)
        }

        combatTime.start()
        menuHandler.start()
        base.start()

        chat.start()
        bomb.start()
        turretHandler.start()
        simpleCommandHandler.start()
        tpaHandler.start()
        playerMounting.start()
        smokeGrenade.start()
        airstrike.start()
        timedAccess.start()
        noBeam.start()
        napalm.start()
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
                    "turret" -> item = turretHandler.turretItem.clone()
                    "customenderpearl" -> item = turretHandler.customEnderPearl.clone()
                    "smokegrenade" -> item = smokeGrenade.smokeGrenade.clone()
                    "smallbomb" -> item = bomb.smallBombItem.clone()
                    "mediumbomb" -> item = bomb.mediumBombItem.clone()
                    "largebomb" -> item = bomb.largeBombItem.clone()
                    "mountinggun" -> item = playerMounting.customWeapon.clone()
                    "mountingammo" -> item = playerMounting.customAmmo.clone()
                    "rocketlauncher" -> item = bomb.rocketLauncherItem.clone()
                    "rocketlauncherammo" -> item = bomb.rocketLauncherAmmo.clone()
                    "ammonium" -> item = bomb.ammoniumNitrate.clone()
                    "plutonium" -> item = bomb.plutoniumCore.clone()
                    "flares" -> item = turretHandler.flares.clone()
                    "airstrike" -> item = airstrike.item.clone()
                    "napalm" -> item = napalm.item.clone()
                    "base" -> item = base.baseItem.clone()

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
                    "airstrike",
                    "napalm",
                    "base"
                )
                else mutableListOf("")
            }

        }

        return mutableListOf("")
    }
}
