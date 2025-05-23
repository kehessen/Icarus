package me.kehessen.icarus.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event that gets called when a SAM missile explodes
 * @param target The player the missile was targeting
 * @param hit Whether the missile hit the target or not
 */
class SAMExplodeEvent(val target: Player, val hit: Boolean) : Event() {
    override fun getHandlers() = getHandlerList()

    companion object {
        @JvmStatic
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList() = handlers
    }
}