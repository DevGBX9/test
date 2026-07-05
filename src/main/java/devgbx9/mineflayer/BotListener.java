package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class BotListener implements Listener {

    private final BotManager botManager;

    public BotListener(BotManager botManager) {
        this.botManager = botManager;
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
}
