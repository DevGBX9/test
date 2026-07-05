package devgbx9.mineflayer;

import io.papermc.paper.event.player.PlayerKickEvent;
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
    public void onPlayerKick(PlayerKickEvent event) {
        String name = event.getPlayer().getName();
        if (botManager.exists(name)) {
            event.setCancelled(true);
            botManager.removeBot(name);
        }
    }
}
