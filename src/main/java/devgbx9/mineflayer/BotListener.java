package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
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
    public void onPlayerQuit(PlayerQuitEvent event) {
        String name = event.getPlayer().getName();
        if (botManager.exists(name)) {
            botManager.removeBot(name);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        String name = event.getPlayer().getName();
        if (botManager.exists(name)) {
            botManager.removeBot(name);
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (!msg.startsWith("/kick") && !msg.startsWith("/minecraft:kick")) return;

        String[] parts = msg.split(" ", 3);
        if (parts.length < 2) return;

        String targetName = parts[1];
        if (!botManager.exists(targetName)) return;

        event.setCancelled(true);
        botManager.removeBot(targetName);
        event.getPlayer().sendMessage("§aBot '" + targetName + "' removed.");
        Bukkit.getLogger().info("[Mineflayer] Bot '" + targetName + "' removed via /kick interception");
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player damaged)) return;
        BotNPC bot = botManager.getBot(damaged.getName());
        if (bot == null || !bot.isAlive()) return;

        event.setCancelled(true);
        damaged.setFireTicks(0);

        if (event.getDamager() instanceof Player attacker) {
            double dx = damaged.getLocation().getX() - attacker.getLocation().getX();
            double dz = damaged.getLocation().getZ() - attacker.getLocation().getZ();
            if (dx * dx + dz * dz < 1.0E-4) {
                dx = (Math.random() - Math.random()) * 0.01;
                dz = (Math.random() - Math.random()) * 0.01;
            }
            double len = Math.sqrt(dx * dx + dz * dz);
            double nx = dx / len;
            double nz = dz / len;
            double strength = 0.4;

            Vector vel = damaged.getVelocity();
            vel.setX(vel.getX() / 2.0 + nx * strength);
            vel.setY(Math.min(0.4, vel.getY() / 2.0 + strength));
            vel.setZ(vel.getZ() / 2.0 + nz * strength);
            damaged.setVelocity(vel);
        }
    }
}
