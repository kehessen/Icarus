package me.kehessen.customplugin.turret

import org.bukkit.event.Listener

class Turret : Listener {
    public var isActive = true
    private var shotDelay: Long = 3
        get() = field
        set(value) {
            field = value
        }
    private var reach: Int = 500
        get() = field
        set(value) {
            field = value
        }
    private var damage: Float = 1f
        get() = field
        set(value) {
            field = value
        }

    // blocks per tick
    private val speedMultiplier: Float = 5f //config.getInt("Turret.arrow-speed-multiplier").toFloat()
        get() = field


    // ---options---
    private var burningArrow: Boolean = true
    private var silenced = false

    // ---backend---
    // time it takes to reach turret reach + 20 ticks
    private val arrowLifeTime = (reach / speedMultiplier / shotDelay + 20 / shotDelay).toInt()

}