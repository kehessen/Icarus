package me.kehessen.icarus.misc

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scoreboard.Scoreboard

class Chat(val enableFormatting: Boolean) : Listener {

    private var sb: Scoreboard? = null

    fun start() {
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("Icarus")!!)
        sb = Bukkit.getScoreboardManager()!!.mainScoreboard
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        if (enableFormatting)
            event.joinMessage = "§2+ ${event.player.name}"
        if (sb!!.getEntryTeam(event.player.name) == null) {
            sb!!.getTeam("Default")?.addEntry(event.player.name)
        }
    }

    @EventHandler
    private fun onPlayerChat(event: AsyncPlayerChatEvent) {
        if (enableFormatting)
            event.format = "§7" + event.player.name + ":§f " + event.message
    }

    @EventHandler
    private fun onPlayerLeave(event: PlayerQuitEvent) {
        if (enableFormatting)
            event.quitMessage = "§7- " + event.player.name
    }
}