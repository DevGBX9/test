package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player victim)) return;
        BotNPC bot = botManager.getBotByPlayer(victim);
        if (bot == null) return;

        Entity damager = event.getDamager();
        double dx = victim.getX() - damager.getX();
        double dz = victim.getZ() - damager.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist > 0.001) {
            dx /= dist;
            dz /= dist;
        }
        bot.nativeKnockback(0.4, dx, dz);
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
