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

        damaged.setFireTicks(0);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!botManager.exists(event.getPlayer().getName())) return;
        event.setCancelled(true);
        event.getPlayer().setHealth(20.0);
    }
}
