package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class BotListener implements Listener {

    private final BotManager botManager;

    public BotListener(BotManager botManager) {
        this.botManager = botManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        BotNPC bot = botManager.getBotByPlayer(player);
        if (bot != null) {
            String name = bot.getName();
            botManager.removeBot(name);
            Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' died.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        BotNPC bot = botManager.getBotByPlayer(player);
        if (bot != null) {
            String name = bot.getName();
            // Let the cleanup happen naturally via onDisable or manual removal
            // Bot might have been kicked during removeBot() which triggers this again
            if (botManager.exists(name)) {
                botManager.removeBot(name);
            }
        }
    }
}
