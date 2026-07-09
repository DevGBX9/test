package devgbx9.mineflayer;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class MineflayerPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private BotManager botManager;

    @Override
    public void onEnable() {
        getCommand("mineflayer").setExecutor(this);
        getCommand("mineflayer").setTabCompleter(this);

        botManager = new BotManager();
        getServer().getPluginManager().registerEvents(new BotListener(botManager), this);

        startTickTask();

        getLogger().info(getName() + " v" + getPluginMeta().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
        if (botManager != null) {
            botManager.removeAll();
        }
    }

    private void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (botManager == null) return;
                for (BotNPC bot : botManager.getAllBots()) {
                    bot.tick();
                }
            }
        }.runTaskTimer(this, 20L, 1L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§6Usage: /mineflayer add <name>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add": {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /mineflayer add <name>");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly players can use this command.");
                    return true;
                }

                String name = args[1];
                if (botManager.exists(name)) {
                    sender.sendMessage("§cBot '" + name + "' already exists.");
                    return true;
                }

                Location spawnLoc = player.getLocation();
                botManager.createBot(name, spawnLoc);
                sender.sendMessage("§aBot '" + name + "' spawned with skin.");
                getLogger().info("Bot '" + name + "' spawned by " + player.getName());
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
                } else {
                    sender.sendMessage("§cBot '" + name + "' not found.");
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("add", "delete");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            String prefix = args[1].toLowerCase();
            List<String> result = new ArrayList<>();
            for (String n : botManager.getBotNames()) {
                if (n.toLowerCase().startsWith(prefix)) result.add(n);
            }
            return result;
        }
        return new ArrayList<>();
    }
}
