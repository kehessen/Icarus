package me.kehessen.customplugin

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

// anything over yield 5 can destroy turrets
class TNT : CommandExecutor, TabCompleter {
    private var smallTNTItem = ItemStack(Material.TNT, 1)
    private var mediumTNTItem = ItemStack(Material.TNT, 1)
    private var largeTNTItem = ItemStack(Material.TNT, 1)

    private var smallTNTYield = 6
    private var mediumTNTYield = 20
    private var largeTNTYield = 50

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args[0] == "spawn") {
            if (args.size != 2 || sender !is Player || args[1].toFloatOrNull() == null) {
                sender.sendMessage("§cInvalid arguments")
                return true
            }
            if (args[1].toFloat() > 150) {
                sender.sendMessage("§cYou're gonna crash the server")
                return true
            }
            if (args[1].toFloat() > 100) {
                sender.sendMessage("§cI'm gonna do it but dont blame me if the server crashes")
            }
            val player = Bukkit.getPlayer(sender.name)
            val tnt = player!!.world.spawn(player.location, org.bukkit.entity.TNTPrimed::class.java)
            tnt.fuseTicks = 100
            tnt.yield = args[1].toFloat()
            return true
        }
        return false
    }

    fun start() {
        Bukkit.getPluginCommand("tnt")?.setExecutor(this)
        Bukkit.getPluginCommand("tnt")?.tabCompleter = this

        var meta = smallTNTItem.itemMeta
        meta!!.setDisplayName("50kg TNT")
        meta.lore = mutableListOf("§7Can be used to destroy turrets", "§7Will slow you down when flying")
        smallTNTItem.itemMeta = meta
        meta = mediumTNTItem.itemMeta
        meta!!.setDisplayName("100kg TNT")
        meta.lore = mutableListOf("§7Can be used to destroy turrets", "§7Will slow you down when flying")
        mediumTNTItem.itemMeta = meta
        meta = largeTNTItem.itemMeta
        meta!!.setDisplayName("250kg TNT")
        meta.lore = mutableListOf("§7Can be used to destroy turrets", "§7Will slow you down when flying")
        largeTNTItem.itemMeta = meta
    }

    override fun onTabComplete(p0: CommandSender, p1: Command, p2: String, p3: Array<out String>): MutableList<String> {
        if (p3.size == 1) {
            return mutableListOf("spawn")
        }
        return mutableListOf()
    }
}