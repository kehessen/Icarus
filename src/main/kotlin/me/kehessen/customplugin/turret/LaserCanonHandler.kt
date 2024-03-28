package me.kehessen.customplugin.turret

import org.bukkit.Particle
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Minecart
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused")
class LaserCanonHandler(val plugin: JavaPlugin, config: FileConfiguration) : CommandExecutor, TabCompleter {

    // ---config---
    private var shotDelay: Long = 50
    private var particleAmount: Int = 10
    private val particleType = Particle.FLAME
    private var maxRange: Int = 50
    private var damage: Int = 20

    // ---options---
    private var silence: Boolean = false

    // ---backend---
    private val activeCanons = arrayListOf<Minecart>()

    override fun onCommand(p0: CommandSender, p1: Command, p2: String, p3: Array<out String>): Boolean {
        TODO("Not yet implemented")
    }

    override fun onTabComplete(
        p0: CommandSender, p1: Command, p2: String, p3: Array<out String>
    ): MutableList<String> {
        if (p3.size == 1) {
            return mutableListOf("reload", "remove", "spawn", "silence", "shotDelay")
        }
        return mutableListOf("")
    }
}