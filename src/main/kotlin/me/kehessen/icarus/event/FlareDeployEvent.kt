package me.kehessen.icarus.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class FlareDeployEvent(val player: Player): Event() {
    override fun getHandlers() = getHandlerList()

    companion object {
        @JvmStatic
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList() = handlers
    }
    
    
}