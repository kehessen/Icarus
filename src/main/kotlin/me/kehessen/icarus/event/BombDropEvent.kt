package me.kehessen.icarus.event

import me.kehessen.icarus.combat.Bomb
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class BombDropEvent(val player: Player, val type: Bomb.Type) : Event() {
    override fun getHandlers() = getHandlerList()

    companion object {
        @JvmStatic
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlers
    }


}