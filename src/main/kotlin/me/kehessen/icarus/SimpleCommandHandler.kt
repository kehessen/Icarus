package me.kehessen.icarus

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team

@Suppress("unused")
class SimpleCommandHandler(private val combatTime: CombatTime, val trtHan: TurretHandler) : CommandExecutor,
    TabCompleter {
    private var forbiddenCombatCommands = listOf("spawn", "tpa")

    private var sb: Scoreboard? = null
    private val pendingInvites = hashMapOf<Player, Team>()
    private val teamInvites = hashMapOf<Team, MutableSet<Player>>()

    @Suppress("DuplicatedCode")
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return false
        val player = Bukkit.getPlayer(sender.name)
        when (command.name) {
            "spawn" -> {
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
                } else sender.teleport(sender.world.spawnLocation)
                return true
            }

            "announce" -> {
                if (args.isEmpty()) {
                    sender.sendMessage("§cInvalid arguments")
                    return true
                }
                Bukkit.broadcastMessage("§4§l${args.joinToString(" ")}")
                return true
            }

            "join" -> {
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
                player!!.sendMessage("§aSent request to join ${team.name}")
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
                if (args.size != 1) {
                    sender.sendMessage("§cInvalid arguments")
                    return true
                }
                val target = Bukkit.getPlayer(args[0])
                val team = pendingInvites[target]
                if (team == null || sb!!.getEntryTeam(sender.name) != pendingInvites[target]) {
                    player!!.sendMessage("§cNo pending invites")
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
                if (args.size != 1) {
                    sender.sendMessage("§cInvalid arguments")
                    return true
                }
                val target = Bukkit.getPlayer(args[0])
                val team = pendingInvites[target]
                if (team == null || sb!!.getEntryTeam(sender.name) != pendingInvites[target]) {
                    player!!.sendMessage("§cNo pending invites")
                    return true
                }
                sb!!.getEntryTeam(player!!.name)!!.entries.forEach {
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
        Bukkit.getPluginCommand("spawn")?.setExecutor(this)
        Bukkit.getPluginCommand("announce")?.setExecutor(this)
        Bukkit.getPluginCommand("test")?.setExecutor(this)
        Bukkit.getPluginCommand("join")?.setExecutor(this)
        Bukkit.getPluginCommand("accept")?.setExecutor(this)
        Bukkit.getPluginCommand("decline")?.setExecutor(this)

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
            if (forbiddenCombatCommands.contains(command) && combatTime.getCombatTime(event.player.uniqueId) != null) {
                event.player.sendMessage("§cYou can't use this command while in combat")
                event.isCancelled = true
            }
        }
    }
}