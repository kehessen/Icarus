package me.kehessen.icarus.util

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import kotlin.math.acos

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
        fun getNearbyPlayers(location: Location, radius: Double): List<Player> {
            return location.world!!.players.filter { it.location.distance(location) <= radius }
        }
        // get the amount of degrees the player is off from the target
        fun getAngleDifference(player: Player, target: Location): Double {
            val playerLocation = player.location
            val playerDirection = playerLocation.direction
            val targetDirection = target.toVector().subtract(playerLocation.toVector()).normalize()
            val dotProduct = playerDirection.dot(targetDirection)
            val clampedDotProduct = dotProduct.coerceIn(-1.0, 1.0)
            val angle = Math.toDegrees(acos(clampedDotProduct))
            return angle
        }

        fun sendActionBarMessage(player: Player, message: String) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(message))
        }
    }
}