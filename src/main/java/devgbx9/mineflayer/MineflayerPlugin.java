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
        getServer().getPluginManager().registerEvents(new BotListener(botManager, this), this);

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
            sender.sendMessage("§6Usage: /mineflayer add <name> | delete <name> | <name> standstill on/off | <name> lookat on/off");
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
                getLogger().info("Spawning bot '" + name + "' (fetching skin from Mojang API)...");
                botManager.createBotAsync(name, spawnLoc, this, (bot) -> {
                    getLogger().info("Bot '" + name + "' spawned by " + player.getName());
                });
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
            default: {
                // Handle: /mineflayer <botname> standstill on/off
                //         /mineflayer <botname> lookat on/off
                String botName = args[0];
                BotNPC bot = botManager.getBot(botName);
                if (bot == null || !bot.isAlive()) {
                    sender.sendMessage("§cBot '" + botName + "' not found.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /mineflayer " + botName + " standstill on/off | lookat on/off");
                    return true;
                }
                String subCmd = args[1].toLowerCase();
                String value = args[2].toLowerCase();
                boolean on = value.equals("on");

                switch (subCmd) {
                    case "standstill": {
                        bot.setStandStill(on);
                        sender.sendMessage("§aBot '" + botName + "' standstill: " + (on ? "§2ON" : "§cOFF"));
                        break;
                    }
                    case "lookat": {
                        bot.setLookAtEnabled(on);
                        sender.sendMessage("§aBot '" + botName + "' lookat: " + (on ? "§2ON" : "§cOFF"));
                        break;
                    }
                    default: {
                        sender.sendMessage("§cUnknown subcommand. Use: standstill, lookat");
                        break;
                    }
                }
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String cmd : List.of("add", "delete")) {
                if (cmd.startsWith(prefix)) result.add(cmd);
            }
            // Also suggest bot names for <botname> subcommands
            for (String n : botManager.getBotNames()) {
                if (n.toLowerCase().startsWith(prefix)) result.add(n);
            }
            return result;
        }
        if (args.length == 2) {
            String first = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();
            if (first.equals("delete")) {
                for (String n : botManager.getBotNames()) {
                    if (n.toLowerCase().startsWith(prefix)) result.add(n);
                }
            } else if (botManager.exists(first)) {
                // Bot name was typed, suggest subcommands
                for (String cmd : List.of("standstill", "lookat")) {
                    if (cmd.startsWith(prefix)) result.add(cmd);
                }
            }
            return result;
        }
        if (args.length == 3 && botManager.exists(args[0].toLowerCase())) {
            String sub = args[1].toLowerCase();
            String prefix = args[2].toLowerCase();
            if (sub.equals("standstill") || sub.equals("lookat")) {
                for (String v : List.of("on", "off")) {
                    if (v.startsWith(prefix)) result.add(v);
                }
            }
            return result;
        }
        return result;
    }
}
