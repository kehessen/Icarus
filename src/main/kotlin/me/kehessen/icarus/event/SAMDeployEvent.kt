package me.kehessen.icarus.event

import org.bukkit.entity.Entity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event that gets called when a SAM missile is deployed
 * @param deployer The player that deployed the missile
 * @param target The target of the missile
 */
@Suppress("unused")
class SAMDeployEvent(val deployer: Entity, val target: Entity): Event() {
    override fun getHandlers() = getHandlerList()

    companion object {
        @JvmStatic
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList() = handlers
    }
}