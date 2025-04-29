package me.kehessen.icarus.misc

import me.kehessen.icarus.combat.Base
import me.kehessen.icarus.combat.TurretHandler
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team

@Suppress("unused")
class SimpleCommandHandler(
    config: FileConfiguration,
    private val combatTime: CombatTime,
    val trtHan: TurretHandler,
    private val baseHan: Base
) : CommandExecutor,
    TabCompleter {
    private var disabledCombatCommands = listOf("spawn", "tpa", "base")

    private var sb: Scoreboard? = null
    private val pendingInvites = hashMapOf<Player, Team>()
    private val teamInvites = hashMapOf<Team, MutableSet<Player>>()

    private val tpaEnabled: Boolean = config.getBoolean("Other.enable-tpa")
    private val spawnEnabled: Boolean = config.getBoolean("Other.enable-spawn")
    private val baseEnabled: Boolean = config.getBoolean("Other.enable-base-tp") && config.getBoolean("Base.enable")
    private val joinEnabled: Boolean = config.getBoolean("Other.enable-join")


    @Suppress("DuplicatedCode")
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return false
        val player = Bukkit.getPlayer(sender.name)!!
        when (command.name) {
            "spawn" -> {
                if (!spawnEnabled) {
                    sender.sendMessage("§cThis command is disabled")
                    return false
                }
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
                } else sender.teleport(Bukkit.getWorld("world")!!.spawnLocation)
                return true
            }

            //TODO 
            // x issued server command: /base
            // [22:34:05 INFO]: [Icarus] [CraftArmorStand, CraftArmorStand, CraftArmorStand, CraftArmorStand, CraftArmorStand]
            // [22:34:08 INFO]: x issued server command: /base
            // [22:34:08 INFO]: [Icarus] [CraftArmorStand, CraftArmorStand, CraftArmorStand, CraftArmorStand, CraftArmorStand, CraftArmorStand]
            // -> cause?

            "base" -> {
                if (!baseEnabled) {
                    sender.sendMessage("§cThis command is disabled")
                    return false
                }
                if (args.isNotEmpty()) {
                    sender.sendMessage("§cInvalid arguments")
                    return true
                }
                val base = baseHan.getPlayerBase(player)
                if (base == null) {
                    sender.sendMessage("§cYou don't have a base")
                    return true
                }
                player.teleport(base.location)
                player.sendMessage("§aTeleported to base")
                return true
            }

            "join" -> {
                if (!joinEnabled) {
                    sender.sendMessage("§cThis command is disabled")
                    return false
                }
                if (args.size != 1) {
                    sender.sendMessage("§cInvalid arguments")
                    return true
                }

                val team = sb!!.getTeam(args[0])
                if (team == null) {
                    sender.sendMessage("§cInvalid team")
                    return true
                }
                if (sb!!.getEntryTeam(sender.name) == team) {
                    sender.sendMessage("§cYou are already in this team")
                    return true
                }
                if (pendingInvites[sender] == team) {
                    sender.sendMessage("§cYou already have a pending invite")
                    return true
                }
                pendingInvites[sender] = team
                if (teamInvites[team] == null) teamInvites[team] = mutableSetOf()
                teamInvites[team]!!.add(sender)
                player.sendMessage("§aSent request to join ${team.name}")
                team.entries.forEach {
                    if (Bukkit.getPlayer(it) == null) return@forEach

                    Bukkit.getPlayer(it)!!.sendMessage("§a${player.name} has requested to join your team")
                    val accept = TextComponent("§a§l[Accept]")
                    val decline = TextComponent("§c§l[Decline]")
                    accept.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/accept ${player.name}")
                    decline.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/decline ${player.name}")
                    Bukkit.getPlayer(it)!!.spigot().sendMessage(accept, decline)
                }
                return true
            }

            "accept" -> {
                if (!joinEnabled) {
                    sender.sendMessage("§cThis command is disabled")
                    return false
                }
                if (args.size != 1) {
                    sender.sendMessage("§cInvalid arguments")
                    return true
                }
                val target = Bukkit.getPlayer(args[0])
                val team = pendingInvites[target]
                if (team == null || sb!!.getEntryTeam(sender.name) != pendingInvites[target]) {
                    player.sendMessage("§cNo pending invites")
                    return true
                }

                sb!!.getEntryTeam(target!!.name)!!.entries.forEach {
                    if (Bukkit.getPlayer(it) != null && Bukkit.getPlayer(it) != target) Bukkit.getPlayer(it)!!
                        .sendMessage("§c${target.name} has left the team")
                }
                sb!!.getEntryTeam(target.name)!!.removeEntry(target.name)

                team.addEntry(target.name)
                pendingInvites.remove(target)
                team.entries.forEach {
                    if (Bukkit.getPlayer(it) != null) Bukkit.getPlayer(it)!!
                        .sendMessage("§a${target.name} has joined the team")
                }
                return true
            }

            "decline" -> {
                if (!joinEnabled) {
                    sender.sendMessage("§cThis command is disabled")
                    return false
                }
                if (args.size != 1) {
                    sender.sendMessage("§cInvalid arguments")
                    return true
                }
                val target = Bukkit.getPlayer(args[0])
                val team = pendingInvites[target]
                if (team == null || sb!!.getEntryTeam(sender.name) != pendingInvites[target]) {
                    player.sendMessage("§cNo pending invites")
                    return true
                }
                sb!!.getEntryTeam(player.name)!!.entries.forEach {
                    if (Bukkit.getPlayer(it) == null) return@forEach
                    Bukkit.getPlayer(it)!!.sendMessage("§c${player.name} has declined the invite")
                }
                target!!.sendMessage("§c${player.name} has declined the invite")
                pendingInvites.remove(target)
                return true
            }
        }
        return false
    }

    fun start() {
        if (spawnEnabled) Bukkit.getPluginCommand("spawn")?.setExecutor(this)
        if (joinEnabled) {
            Bukkit.getPluginCommand("join")?.setExecutor(this)
            Bukkit.getPluginCommand("accept")?.setExecutor(this)
            Bukkit.getPluginCommand("decline")?.setExecutor(this)
        }
        if (baseEnabled) Bukkit.getPluginCommand("base")?.setExecutor(this)

        sb = Bukkit.getScoreboardManager()?.mainScoreboard
    }

    override fun onTabComplete(
        sender: CommandSender, command: Command, alias: String, args: Array<out String>
    ): MutableList<String>? {
        when (command.name) {
            "spawn" -> if (sender.hasPermission("op")) return args.size.let {
                when (it) {
                    1 -> mutableListOf("set")
                    else -> mutableListOf("")
                }
            }

            "join" -> {
                if (args.size == 1) {
                    val team = sb!!.teams.map { it.name }.toMutableList()
                    team.remove(sb!!.getEntryTeam(sender.name)?.name)
                    team.remove(sb!!.getTeam("TurretArrows")?.name)
                    team.remove(sb!!.getTeam("MissileRedGlow")?.name)
                    return team
                } else return mutableListOf("")
            }

            "accept" -> {
                return if (args.size == 1) teamInvites.values.flatten().map { it.name }
                    .toMutableList() else mutableListOf("")
            }

            "decline" -> {
                return if (args.size == 1) teamInvites.values.flatten().map { it.name }
                    .toMutableList() else mutableListOf("")
            }
        }
        return null
    }


    @EventHandler
    fun blockCMDs(event: PlayerCommandPreprocessEvent) {
        if (event.message.startsWith("/")) {
            val command = event.message.split(" ")[0].substring(1).lowercase()
            if (disabledCombatCommands.contains(command) && combatTime.getCombatTime(event.player.uniqueId) != 0) {
                event.player.sendMessage("§cYou can't use this command while in combat")
                event.isCancelled = true
            }
        }
    }
}