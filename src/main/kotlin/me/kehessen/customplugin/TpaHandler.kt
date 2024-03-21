package me.kehessen.customplugin


import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.sql.Time
import java.util.*

@Suppress("unused")
class TpaHandler(private val combatTime: CombatTime) : CommandExecutor {
    private var map = hashMapOf<UUID, UUID>()

    //uses the creator as "owner" of the tpa request
    private var creationTime = hashMapOf<UUID, Time>()
    var scheduler =
        Bukkit.getPluginManager().getPlugin("CustomPlugin")?.let {
            Bukkit.getScheduler().scheduleSyncDelayedTask(it, {
                map.forEach { (key, value) ->
                    if (creationTime[key]?.time!! + 20 * 60 * 2 < System.currentTimeMillis()) {
                        Bukkit.getPlayer(key)
                            ?.sendMessage("§cYour teleport request to ${Bukkit.getPlayer(value)?.name} has expired")
                        Bukkit.getPlayer(value)
                            ?.sendMessage("§cThe teleport request from ${Bukkit.getPlayer(key)?.name} has expired")
                        map.remove(key)
                    }
                }
            }, 20 * 1)
        }

    // combat check in CombatTime
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.size != 1) {
            sender.sendMessage("§cInvalid arguments")
            return false
        }
        if (sender !is Player) {
            sender.sendMessage("§Invalid sender")
            return false
        }
        if (combatTime.getCombatTime(sender.uniqueId) != 0) {
            sender.sendMessage("§cYou can't use this command while in combat")
            return true
        }

        // check if both sender and receiver are players and if the command parameters are valid and sends the request
        // command.name == "tpa" removed, should work without
        if (command.name == "tpa" && Bukkit.getPlayer(args[0]) is Player) {
            val component = TextComponent("§aClick here to accept")
            component.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept ${sender.name}")
            component.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text("§aAccept the teleport request"))
            map[sender.uniqueId] = Bukkit.getPlayer(args[0])?.uniqueId!!
            creationTime[sender.uniqueId] = Time(System.currentTimeMillis())
            Bukkit.getPlayer(args[0])?.sendMessage("§a${sender.name} wants to teleport to you")
            Bukkit.getPlayer(args[0])?.spigot()?.sendMessage(component)
            Bukkit.getPlayer(sender.name)?.sendMessage("§aTeleport request sent to ${Bukkit.getPlayer(args[0])?.name}")
        }
        // sender is now tpa receiver
        else if (command.name == "tpaccept" && Bukkit.getPlayer(args[0]) is Player) {
            Bukkit.getPlayer(args[0])?.teleport(Bukkit.getPlayer(sender.name)!!.location)
            Bukkit.getPlayer(args[0])?.sendMessage("§aTeleported to ${sender.name}")
            Bukkit.getPlayer(sender.name)?.sendMessage("§aTeleported ${Bukkit.getPlayer(args[0])?.name} to you")
        }
        return true
    }
}