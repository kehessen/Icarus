package me.kehessen.icarus.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event that gets called when a SAM missile explodes
 * @param target The player that got hit by the missile
 * @param hit Whether the missile hit the target or not
 */
class SAMExplodeEvent(var target: Player, var hit: Boolean): Event() {
    override fun getHandlers() = getHandlerList()

    companion object {
        @JvmStatic
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList() = handlers
    }
}