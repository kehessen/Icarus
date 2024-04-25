package me.kehessen.icarus

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.event.Listener
import org.bukkit.scheduler.BukkitTask
import java.time.LocalTime

class TimedAccess(config: FileConfiguration) : Listener {
    private val enabled = config.getBoolean("TimedAccess.enabled")
    private val stop = config.getBoolean("TimedAccess.stop-on-close")

    private val openHour = config.getInt("TimedAccess.open-hour")
    private val openMinute = config.getInt("TimedAccess.open-minute")

    private val closeHour = config.getInt("TimedAccess.close-hour")
    private val closeMinute = config.getInt("TimedAccess.close-minute")

    private val warningTime = config.getInt("TimedAccess.warning-time")


    private val open = openHour * 60 * 60 * 1000 + openMinute * 60 * 1000
    private val close = closeHour * 60 * 60 * 1000 + closeMinute * 60 * 1000
    private val warning = close - warningTime * 60 * 1000

    private var isClosed = false

    private var task: BukkitTask? = null

    fun start() {
        if (enabled) {
            isClosed = Bukkit.getServer().hasWhitelist() // untested
            Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("Icarus")!!)
            task = Bukkit.getScheduler().runTaskTimer(Bukkit.getPluginManager().getPlugin("Icarus")!!, Runnable {
                checkTime()
            }, 5, 20 * 60)
        }
    }

    private fun checkTime() {
        val now = LocalTime.now()
        val nowMillis = now.hour * 60 * 60 * 1000 + now.minute * 60 * 1000 + now.second * 1000
        if (nowMillis in (warning + 1)..<close) {
            Bukkit.broadcastMessage("§cServer will close in ${(close - nowMillis) / 1000 / 60 + 1} minutes")
        }
        if ((nowMillis < open || nowMillis > close) && !isClosed) {
            Bukkit.broadcastMessage("§cServer closing...")
            Bukkit.getServer().setWhitelist(true)
            Bukkit.getOnlinePlayers().forEach {
                if (!it.isWhitelisted)
                    it.kickPlayer("Server closed")
            }
            isClosed = true
            if (stop && nowMillis > open) Bukkit.getServer().shutdown()
        } else if (Bukkit.getServer().hasWhitelist() && nowMillis in open..close && isClosed) {
            Bukkit.broadcastMessage("§aServer opening...")
            Bukkit.getServer().setWhitelist(false)
            isClosed = false
        }
    }
}