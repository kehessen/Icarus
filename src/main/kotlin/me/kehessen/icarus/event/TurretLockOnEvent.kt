package me.kehessen.icarus.event

import me.kehessen.icarus.combat.Turret
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event that gets called when a turret locks on to a target
 * @param target The player that the turret is locking on to
 * @param turret The turret that is locking on to the target
 */
class TurretLockOnEvent(val target: Player, val turret: Turret) : Event() {
    override fun getHandlers() = getHandlerList()

    companion object {
        @JvmStatic
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlers
    }
}