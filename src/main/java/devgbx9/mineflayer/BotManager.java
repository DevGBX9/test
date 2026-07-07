package devgbx9.mineflayer;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class BotManager {

    private final Map<String, BotNPC> bots = new HashMap<>();

    public BotNPC createBot(String name, Location location) {
        BotNPC bot = new BotNPC(name, UUID.randomUUID());
        bot.spawn(location);
        bots.put(name.toLowerCase(), bot);
        return bot;
    }

    public boolean removeBot(String name) {
        BotNPC bot = bots.remove(name.toLowerCase());
        if (bot != null) {
            bot.remove();
            return true;
        }
        return false;
    }

    public void removeAll() {
        for (BotNPC bot : bots.values()) {
            bot.remove();
        }
        bots.clear();
    }

    public BotNPC getBot(String name) {
        return bots.get(name.toLowerCase());
    }

    public BotNPC getBotByPlayer(Player player) {
        for (BotNPC bot : bots.values()) {
            if (bot.isAlive() && bot.getBukkitPlayer() != null && bot.getBukkitPlayer().equals(player)) {
                return bot;
            }
        }
        return null;
    }

    public boolean exists(String name) {
        return bots.containsKey(name.toLowerCase());
    }

    public List<String> getBotNames() {
        return List.copyOf(bots.keySet());
    }

    public List<BotNPC> getAllBots() {
        return List.copyOf(bots.values());
    }
}
