package me.kehessen.customplugin

import org.bukkit.*
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.meta.CrossbowMeta

class PlayerMounting(config: FileConfiguration) : Listener, CommandExecutor, TabCompleter {
    // key: mounted player, value: flying player
    private val mountedPlayers = hashMapOf<Player, Player>()
    internal val customWeapon: ItemStack =
        CustomItem(Material.CROSSBOW, "§r§l§c12.7mm M2 Browning", "§7Right click to shoot .50 cal bullets")
    internal val customAmmo: ItemStack =
        CustomItem(Material.ARROW, "§r§l§c.50 BMG", "§7Used for the M2 Browning", "§7Armor piercing")

    private var canonReach: Double = config.getDouble("PlayerMounting.canon-reach")
    private var damage: Double = config.getDouble("PlayerMounting.canon-damage")
    private var onlyAllowMountingForFlight: Boolean = config.getBoolean("PlayerMounting.only-flight")
    private var playHurtAnimation: Boolean = config.getBoolean("PlayerMounting.hurt-animation")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return false
        if (args.size == 1) {
            if (args[0] == "hurtAnimation" && sender.isOp) {
                playHurtAnimation = !playHurtAnimation
                sender.sendMessage("§aHurt animation toggled to $playHurtAnimation")
                return true
            }
            val target = Bukkit.getPlayer(args[0])
            if (target == null) {
                sender.sendMessage("§cPlayer not found")
                return true
            }
            return mount(sender, target)
        }
        return false
    }

    @Suppress("SameReturnValue")
    private fun mount(player: Player, target: Player): Boolean {
        if (mountedPlayers.containsKey(player)) {
            player.sendMessage("§cYou are already mounted")
            return true
        }
        if (mountedPlayers.containsValue(target)) {
            player.sendMessage("§cPlayer already has a shooter")
            return true
        }
        if (!player.inventory.storageContents.contains(null)) {
            player.sendMessage("§cCannot mount with a full inventory")
            return true
        }
        if (player.location.distance(target.location) > 10) {
            player.sendMessage("§cYou are too far away from ${target.name}")
            return true
        }
        if (onlyAllowMountingForFlight && target.inventory.armorContents[2]?.type != Material.ELYTRA) {
            player.sendMessage("§c${target.name} is not wearing an elytra")
            return true
        }
        val scoreboard = Bukkit.getScoreboardManager()?.mainScoreboard
        if (scoreboard!!.getEntryTeam(player.name) != scoreboard.getEntryTeam(target.name)) {
            player.sendMessage("§cYou cannot mount an enemy player")
            return true
        }
        mountedPlayers[player] = target
        target.addPassenger(player)
        player.inventory.addItem(customWeapon)
        player.sendMessage("§aYou are now mounted on ${target.name}")
        target.sendMessage("§a${player.name} is now your gunman")
        if (onlyAllowMountingForFlight)
            Bukkit.getScheduler().scheduleSyncDelayedTask(Bukkit.getPluginManager().getPlugin("CustomPlugin")!!, {
                if (target.passengers.contains(player) && !target.isGliding) {
                    player.inventory.removeItem(customWeapon)
                    mountedPlayers.remove(player)
                    target.removePassenger(player)
                    player.sendMessage("§cYou have been dismounted as ${target.name} hasn't taken off in time")
                    target.sendMessage("§c${player.name} has been dismounted as you haven't taken off in time")
                }
            }, 20 * 5)
        return true
    }

    fun start() {
        val meta = customWeapon.itemMeta as CrossbowMeta
        meta.setChargedProjectiles(listOf(ItemStack(Material.ARROW)))
        customWeapon.itemMeta = meta

        Bukkit.getPluginCommand("mount")?.setExecutor(this)
        Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("CustomPlugin")!!)
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.isInsideVehicle && player.vehicle is Player) {
                mountedPlayers[player] = player.vehicle as Player
            }
        }

        addCustomRecipe()
    }

    private fun addCustomRecipe() {
        val recipe = ShapedRecipe(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CustomPlugin")!!, "50_cal_bullet"),
            customAmmo
        )
        recipe.shape("   ", "NAN", " G ")
        recipe.setIngredient('N', Material.IRON_NUGGET)
        recipe.setIngredient('G', Material.GUNPOWDER)
        recipe.setIngredient('A', Material.ARROW)
        Bukkit.addRecipe(recipe)
    }

    @EventHandler
    private fun onShot(event: PlayerInteractEvent) {
        if (event.item == null || event.item!!.itemMeta == null) return
        if (event.action != Action.RIGHT_CLICK_AIR) return
        if (!event.player.isInsideVehicle) {
            event.player.inventory.removeItem(customWeapon)
            return
        }
        if (event.item!!.itemMeta?.displayName == customWeapon.itemMeta!!.displayName) {
            if (!event.player.inventory.containsAtLeast(customAmmo, 1)) {
                event.player.sendMessage("§cNo ammo left")
                event.isCancelled = true
                return
            }
            val ray = event.player.rayTraceBlocks(100.0)
            val hitEntity = event.player.world.rayTraceEntities(
                event.player.eyeLocation,
                event.player.location.direction,
                100.0,
                0.0
            ) { entity ->
                entity != event.player && entity is LivingEntity
            }
            var distance = ray?.hitPosition?.distance(event.player.location.toVector())
            if (distance == null) {
                distance = canonReach
            }
            val particleAmount = (canonReach - (canonReach - distance)).toInt()
            val location = event.player.eyeLocation.subtract(0.0, 0.5, 0.0)
            val to = location.clone().add(location.direction.clone().multiply(distance)).add(0.0, 0.5, 0.0)
            Utils.drawLine(location, to, Particle.FLAME, particleAmount)
            if (hitEntity != null) {
                if (hitEntity.hitEntity is LivingEntity) {
                    (hitEntity.hitEntity as LivingEntity).damage(damage, event.player)
                    (hitEntity.hitEntity as LivingEntity).noDamageTicks = 5
                }
            }
            event.player.inventory.removeItem(customAmmo)

            if (playHurtAnimation)
                event.player.playHurtAnimation(0f)
            event.player.world.playSound(event.player.location, Sound.ENTITY_ENDER_PEARL_THROW, 100f, 0.1f)

            event.isCancelled = true
        }
    }

    @EventHandler
    private fun onDismount(event: EntityDismountEvent) {
        if (event.entity is Player && event.dismounted is Player) {
            val player = event.entity as Player
            val plane = event.dismounted as Player
            if (mountedPlayers.containsKey(player) && mountedPlayers[player] == plane) {
                mountedPlayers.remove(player)
                player.inventory.removeItem(customWeapon)
                plane.sendMessage("§c${player.name} is no longer your gunman")
            }
        }
    }

    @EventHandler
    private fun onLand(event: EntityToggleGlideEvent) {
        if (event.entity !is Player) return
        val player = event.entity as Player
        if (!event.isGliding) {
            if (mountedPlayers.containsValue(player)) {
                player.inventory.removeItem(customWeapon)
                mountedPlayers.filter { it.value == player }.keys.forEach { it.sendMessage("§cYou were dismounted as you have landed") }
                player.removePassenger(mountedPlayers.filter { it.value == player }.keys.first())
                mountedPlayers.filter { it.value == player }.keys.forEach { mountedPlayers.remove(it) }
            }
        }
    }

    @EventHandler
    private fun onQuit(event: PlayerQuitEvent) {
        // if the plane disconnects, it counts as a dismount
        if (mountedPlayers.containsKey(event.player)) {
            event.player.inventory.removeItem(customWeapon)
            mountedPlayers[event.player]!!.removePassenger(event.player)
            mountedPlayers.remove(event.player)
        }
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!event.player.hasDiscoveredRecipe(
                NamespacedKey(
                    Bukkit.getPluginManager().getPlugin("CustomPlugin")!!,
                    "50_cal_bullet"
                )
            )
        ) {
            event.player.discoverRecipe(
                NamespacedKey(
                    Bukkit.getPluginManager().getPlugin("CustomPlugin")!!,
                    "50_cal_bullet"
                )
            )
        }
    }

    override fun onTabComplete(
        p0: CommandSender,
        p1: Command,
        p2: String,
        p3: Array<out String>
    ): MutableList<String> {
        if (p3.size == 1) {
            val list = Bukkit.getOnlinePlayers().map { it.name }.toMutableList()
            if (p0.isOp)
                list.add("hurtAnimation")
            list.remove(p0.name)
            return list
        }
        return mutableListOf("")
    }
}