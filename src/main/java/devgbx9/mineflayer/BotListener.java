package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
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
        Player player = event.getPlayer();
        BotNPC bot = botManager.getBotByPlayer(player);
        if (bot != null) {
            String name = bot.getName();
            botManager.removeBot(name);
            Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' died.");
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        BotNPC bot = botManager.getBotByPlayer((Player) event.getEntity());
        if (bot == null) return;
        if (!(event.getDamager() instanceof Player)) return;

        Player attacker = (Player) event.getDamager();
        double dx = bot.getBukkitPlayer().getX() - attacker.getX();
        double dz = bot.getBukkitPlayer().getZ() - attacker.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist > 0.001) {
            dx /= dist;
            dz /= dist;
        }
        bot.nativeKnockback(0.4, dx, dz);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        BotNPC bot = botManager.getBotByPlayer(player);
        if (bot != null) {
            String name = bot.getName();
            if (botManager.exists(name)) {
                botManager.removeBot(name);
            }
        }
    }
}
