package me.kehessen.icarus.event

import me.kehessen.icarus.combat.Turret
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event that gets called when a turret runs out of ammo
 * @param turret The turret that ran out of ammo
 */
class TurretOutOfAmmoEvent(val turret: Turret) : Event() {
    override fun getHandlers() = getHandlerList()

    companion object {
        @JvmStatic
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlers
    }

}