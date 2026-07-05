package devgbx9.mineflayer;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BotManager {

    private final Map<String, BotNPC> bots = new HashMap<>();

    public BotNPC createBot(String name, Location location, Player source) {
        BotNPC bot = new BotNPC(name, UUID.randomUUID());
        bot.spawn(location, source);
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

    public boolean exists(String name) {
        return bots.containsKey(name.toLowerCase());
    }

    public BotNPC getBot(String name) {
        return bots.get(name.toLowerCase());
    }

    public void removeAll() {
        for (BotNPC bot : bots.values()) {
            bot.remove();
        }
        bots.clear();
    }

    public List<String> getBotNames() {
        return List.copyOf(bots.keySet());
    }

    public List<BotNPC> getAllBots() {
        return List.copyOf(bots.values());
    }
}
