package me.kehessen.icarus

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerQuitEvent
import java.sql.Time
import java.util.*

@Suppress("unused")
class TpaHandler(private val combatTime: CombatTime, config: FileConfiguration) : CommandExecutor, TabCompleter {
    private var map = hashMapOf<UUID, UUID>()
    private val canTeleportInCombat = config.getBoolean("Combat.can-teleport-in-combat")

    // uses the creator as "owner" of the tpa request
    private var creationTime = hashMapOf<UUID, Time>()
    var scheduler = Bukkit.getPluginManager().getPlugin("Icarus")?.let {
        Bukkit.getScheduler().scheduleSyncDelayedTask(it, {
            map.forEach { (key, value) ->
                // 20*60?
                if (creationTime[key]?.time!! + 2 < System.currentTimeMillis()) {
                    Bukkit.getPlayer(key)
                        ?.sendMessage("§cYour teleport request to ${Bukkit.getPlayer(value)?.name} has expired")
                    Bukkit.getPlayer(value)
                        ?.sendMessage("§cThe teleport request from ${Bukkit.getPlayer(key)?.name} has expired")
                    map.remove(key)
                }
            }
        }, 20 * 1)
    }

    fun start() {
        Bukkit.getPluginCommand("tpa")?.setExecutor(this)
        Bukkit.getPluginCommand("tpaccept")?.setExecutor(this)
    }

    // combat check in CombatTime
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§Invalid sender")
            return false
        }

        if (command.name == "tpa" && args.size == 1 && args[0] == "cancel" && map.containsKey(sender.uniqueId)) {
            sender.sendMessage("§cCancelled the teleport request to ${Bukkit.getPlayer(map[sender.uniqueId]!!)!!.name}")
            Bukkit.getPlayer(map[sender.uniqueId]!!)!!
                .sendMessage("§c${sender.name} has cancelled the teleport request")
            map.remove(sender.uniqueId)
            return true
        }

        if (args.size != 1) {
            sender.sendMessage("§cInvalid arguments")
            return false
        }


        val receiver = Bukkit.getPlayer(args[0])

        if ((receiver == null || !receiver.isOnline)) {
            sender.sendMessage("§cPlayer not found")
            return false
        }
        if (combatTime.getCombatTime(sender.uniqueId) != 0 || canTeleportInCombat) {
            sender.sendMessage("§cYou can't use this command while in combat")
            return false
        }
        if (sender.name == receiver.name) {
            sender.sendMessage("§cYou can't teleport to yourself")
            return false
        }
        if (args[0] !in Bukkit.getOnlinePlayers().map { it.name }) {
            sender.sendMessage("§cPlayer not found")
            return false
        }

        // should work, but can't check yet
        // value is automatically updated when the same key is used again
        if (map.containsKey(sender.uniqueId)) {
            sender.sendMessage("§cCancelled the teleport request to ${Bukkit.getPlayer(map[sender.uniqueId]!!)!!.name}")
            Bukkit.getPlayer(map[sender.uniqueId]!!)!!
                .sendMessage("§c${sender.name} has cancelled the teleport request")
        }


        // check if both sender and receiver are players and if the command parameters are valid and sends the request
        if (command.name == "tpa") {
            if (args[0] == "cancel") {
                if (!map.containsKey(sender.uniqueId)) {
                    sender.sendMessage("§cYou don't have a pending request")
                    return false
                }
                receiver.sendMessage("§c${sender.name} has cancelled the teleport request")
                sender.sendMessage("§cCancelled the teleport request to ${receiver.name}")
                map.remove(sender.uniqueId)
                return true
            }
            val component = TextComponent("§a§l[Click here to accept]")
            component.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept ${sender.name}")
            component.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text("§aAccept the teleport request"))
            map[sender.uniqueId] = receiver.uniqueId
            creationTime[sender.uniqueId] = Time(System.currentTimeMillis())
            receiver.sendMessage("§a${sender.name} wants to teleport to you")
            receiver.spigot().sendMessage(component)
            Bukkit.getPlayer(sender.name)?.sendMessage("§aTeleport request sent to ${receiver.name}")
        }
        // sender is now tpa receiver
        else if (command.name == "tpaccept" && receiver in Bukkit.getOnlinePlayers()) {
            if (!map.containsKey(receiver.uniqueId)) {
                sender.sendMessage("§cYou don't have a pending request")
                return false
            }
            receiver.teleport(Bukkit.getPlayer(sender.name)!!.location)
            receiver.sendMessage("§aTeleported to ${sender.name}")
            Bukkit.getPlayer(sender.name)?.sendMessage("§aTeleported ${receiver.name} to you")
            map.remove(receiver.uniqueId)
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender, command: Command, label: String, args: Array<out String>
    ): MutableList<String>? {
        when (command.name) {
            "tpa" -> {
                if (map.containsKey(Bukkit.getPlayer(sender.name)!!.uniqueId)) return mutableListOf("cancel")
                val list = Bukkit.getOnlinePlayers().map { it.name }.toMutableList()
                list.remove(sender.name)
                // not entirely sure how it behaves if the sender is not a player, but shouldn't crash
                // also who else would send a tpa
                // CIA after hearing someone invented a cure for cancer
                if (map.containsKey(Bukkit.getPlayer(sender.name)!!.uniqueId)) list.add("cancel")
                return list
            }

            "tpaccept" -> return map.keys.map { Bukkit.getPlayer(it)?.name!! }.toMutableList()
        }
        return null
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (map.containsKey(event.player.uniqueId)) {
            Bukkit.getPlayer(map[event.player.uniqueId]!!)?.sendMessage("§c${event.player.name} has logged out")
            map.remove(event.player.uniqueId)
        }
        if (map.containsValue(event.player.uniqueId)) {
            map.remove(map.filterValues { it == event.player.uniqueId }.keys.first())
        }
    }
}