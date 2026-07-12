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
            botManager.removeBot(name);
            Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' died.");
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
     * Prevent the server from kicking the bot for any reason
     * (timeout, flying, moved too quickly, etc.)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerKick(PlayerKickEvent event) {
        BotNPC bot = botManager.getBotByPlayer(event.getPlayer());
        if (bot != null) {
            event.setCancelled(true);
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
     * Ensure bots take damage properly.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        BotNPC bot = botManager.getBotByPlayer(victim);
        if (bot == null) return;
        // Don't cancel — let the damage go through so the bot takes damage normally
    }
}
