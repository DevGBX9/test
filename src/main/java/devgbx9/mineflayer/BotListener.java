package devgbx9.mineflayer;

import org.bukkit.Bukkit;
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
}
