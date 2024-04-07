package me.kehessen.icarus

import org.bukkit.Location
import org.bukkit.Particle

class Utils {
    companion object {
        fun drawLine(a: Location, b: Location, particle: Particle, amount: Int): Boolean {
            if (a.world == null || b.world == null || a.world != b.world) return false
            val vector = b.toVector().subtract(a.toVector())
            val length = vector.length()
            vector.normalize()
            val step = length / amount
            for (i in 0 until amount) {
                val location = a.clone().add(vector.clone().multiply(step * i))
                location.world!!.spawnParticle(particle, location, 1, 0.0, 0.0, 0.0, 0.0)
            }
            return true
        }
    }
}