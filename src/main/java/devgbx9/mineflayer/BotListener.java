package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public class BotListener implements Listener {

    private final BotManager botManager;
    private final Plugin plugin;

    public BotListener(BotManager botManager, Plugin plugin) {
        this.botManager = botManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        BotNPC bot = botManager.getBotByPlayer(event.getPlayer());
        if (bot != null) {
            String name = bot.getName();
            if (bot.isRespawnEnabled()) {
                // Delay by 1 tick to prevent dead-body glitching
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (bot.isAlive() && bot.getBukkitPlayer() != null) {
                        bot.getBukkitPlayer().spigot().respawn();
                        bot.getBukkitPlayer().teleport(bot.getSpawnLocation());
                    }
                });
                Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' died and is respawning.");
            } else {
                botManager.removeBot(name);
                Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' died.");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        BotNPC bot = botManager.getBotByPlayer(event.getPlayer());
        if (bot != null) {
            String name = bot.getName();
            if (botManager.exists(name)) {
                botManager.removeBot(name);
            }
        }
    }

    /**
     * Prevent the server from kicking the bot for automatic reasons
     * (timeout, flying, moved too quickly, etc.)
     * But allow administrative /kick and /ban commands to work.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerKick(PlayerKickEvent event) {
        BotNPC bot = botManager.getBotByPlayer(event.getPlayer());
        if (bot != null) {
            boolean isAdminKick = false;
            try {
                // Check Paper API getCause() via reflection
                Object causeObj = event.getClass().getMethod("getCause").invoke(event);
                if (causeObj != null) {
                    String causeName = causeObj.toString();
                    if (causeName.equals("KICK_COMMAND") || causeName.equals("KICKED") 
                        || causeName.equals("BANNED") || causeName.equals("IP_BANNED")) {
                        isAdminKick = true;
                    }
                }
            } catch (Exception e) {
                // Fallback for Spigot or older Paper versions using kick reasons
                String reason = event.getReason();
                if (reason != null) {
                    String rl = reason.toLowerCase();
                    if (rl.contains("kick") || rl.contains("ban") || rl.contains("operator") 
                        || rl.contains("admin") || rl.contains("bye") || rl.contains("removed")) {
                        isAdminKick = true;
                    }
                }
            }

            if (isAdminKick) {
                // Allow the kick to go through, which removes the bot permanently via PlayerQuitEvent
                botManager.removeBot(bot.getName());
                Bukkit.getLogger().info("[Mineflayer] Bot '" + bot.getName() + "' kicked by admin/command. Removing.");
            } else {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Apply knockback to bots when they take damage from another entity.
     * Uses a 1-tick delay to ensure the velocity is applied AFTER
     * ServerPlayer.tick() processes, so it won't get overwritten.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        BotNPC bot = botManager.getBotByPlayer(victim);
        if (bot == null) return;
        if (bot.isStandStill()) return; // No knockback in standstill mode

        // Calculate knockback direction from attacker to bot
        org.bukkit.entity.Entity attacker = event.getDamager();
        double dx = victim.getLocation().getX() - attacker.getLocation().getX();
        double dz = victim.getLocation().getZ() - attacker.getLocation().getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001) {
            dx = Math.random() - 0.5;
            dz = Math.random() - 0.5;
            dist = Math.sqrt(dx * dx + dz * dz);
        }

        // Normalize and apply knockback strength
        double knockbackStrength = 0.4;
        final double kbX = (dx / dist) * knockbackStrength;
        final double kbZ = (dz / dist) * knockbackStrength;

        // Apply knockback with a 1-tick delay so ServerPlayer.tick() doesn't overwrite it
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!victim.isOnline()) return;
            victim.setVelocity(new Vector(kbX, 0.36, kbZ));
        });
    }

    /**
     * Handle bot damage. Cancel all damage when standStill is active.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        BotNPC bot = botManager.getBotByPlayer(victim);
        if (bot == null) return;
        if (bot.isStandStill()) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent mobs from targeting standstill bots.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player target) {
            BotNPC bot = botManager.getBotByPlayer(target);
            if (bot != null && bot.isStandStill()) {
                event.setCancelled(true);
            }
        }
    }
}
