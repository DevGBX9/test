package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MineflayerPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private BotManager botManager;
    private final Random random = new Random();
    private BukkitRunnable aiTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getCommand("mineflayer").setExecutor(this);
        getCommand("mineflayer").setTabCompleter(this);
        botManager = new BotManager();
        getServer().getPluginManager().registerEvents(new BotListener(botManager, this), this);

        startAITask();

        getLogger().info(getName() + " v" + getPluginMeta().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (aiTask != null) aiTask.cancel();
        if (botManager != null) {
            botManager.removeAll();
        }
        getLogger().info(getName() + " disabled!");
    }

    private void startAITask() {
        aiTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (BotNPC bot : botManager.getAllBots()) {
                    if (!bot.isAlive()) continue;
                    Player botPlayer = bot.getBukkitPlayer();
                    if (botPlayer == null || !botPlayer.isOnline()) continue;

                    Player target = bot.getTarget();
                    if (target == null || !target.isOnline() || !target.getWorld().equals(botPlayer.getWorld())) {
                        target = findNearestPlayer(botPlayer);
                        bot.setTarget(target);
                    }

                    bot.tick();
                }
            }
        };
        aiTask.runTaskTimer(this, 20L, 1L);
    }

    private Player findNearestPlayer(Player bot) {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(bot) || !p.getWorld().equals(bot.getWorld())) continue;
            double dist = bot.getLocation().distanceSquared(p.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = p;
            }
        }
        return nearest;
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

                Player source = (sender instanceof Player) ? (Player) sender : null;
                Location spawnLoc;
                Player targetPlayer = null;
                if (sender instanceof Player player) {
                    spawnLoc = player.getLocation();
                    Entity lookedAt = player.getTargetEntity(100);
                    if (lookedAt instanceof Player) targetPlayer = (Player) lookedAt;
                } else {
                    World world = Bukkit.getWorlds().get(0);
                    spawnLoc = randomLocation(world);
                }

                BotNPC bot = botManager.createBot(name, spawnLoc, source);
                if (targetPlayer != null) {
                    bot.setTarget(targetPlayer);
                    sender.sendMessage("§aBot '" + name + "' will attack " + targetPlayer.getName());
                }
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            result.add("add");
            result.add("delete");
            return result;
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            List<String> result = new ArrayList<>();
            if (args[0].equalsIgnoreCase("delete")) {
                for (String n : botManager.getBotNames()) {
                    if (n.toLowerCase().startsWith(prefix)) result.add(n);
                }
            } else if (args[0].equalsIgnoreCase("add")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) result.add(p.getName());
                }
            }
            return result;
        }
        return new ArrayList<>();
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
