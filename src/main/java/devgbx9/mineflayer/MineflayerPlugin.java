package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class MineflayerPlugin extends JavaPlugin implements CommandExecutor {

    private BotManager botManager;
    private final Random random = new Random();

    @Override
    public void onEnable() {
        getCommand("mineflayer").setExecutor(this);
        botManager = new BotManager();
        getLogger().info(getName() + " v" + getPluginMeta().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (botManager != null) {
            botManager.removeAll();
        }
        getLogger().info(getName() + " disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§6Usage: /mineflayer <add|delete> <name>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add": {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /mineflayer add <name>");
                    return true;
                }
                String name = args[1];
                if (botManager.exists(name)) {
                    sender.sendMessage("§cBot '" + name + "' already exists.");
                    return true;
                }

                Location spawnLoc;
                if (sender instanceof Player player) {
                    spawnLoc = player.getLocation();
                } else {
                    World world = Bukkit.getWorlds().get(0);
                    spawnLoc = randomLocation(world);
                }

                botManager.createBot(name, spawnLoc);
                sender.sendMessage("§aBot '" + name + "' spawned at " + locString(spawnLoc) + ".");
                getLogger().info("Bot '" + name + "' spawned at " + locString(spawnLoc));
                return true;
            }
            case "delete": {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /mineflayer delete <name>");
                    return true;
                }
                String name = args[1];
                if (botManager.removeBot(name)) {
                    sender.sendMessage("§aBot '" + name + "' removed.");
                    getLogger().info("Bot '" + name + "' removed.");
                } else {
                    sender.sendMessage("§cBot '" + name + "' not found.");
                }
                return true;
            }
        }
        return false;
    }

    private Location randomLocation(World world) {
        int x = random.nextInt(2000) - 1000;
        int z = random.nextInt(2000) - 1000;
        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    private static String locString(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
