package devgbx9.mineflayer;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MineflayerPlugin extends JavaPlugin implements CommandExecutor {

    private BotManager botManager;

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
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§6Usage: /mineflayer <add|delete> <name>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add": {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mineflayer add <name>");
                    return true;
                }
                String name = args[1];
                if (botManager.exists(name)) {
                    player.sendMessage("§cBot '" + name + "' already exists.");
                    return true;
                }
                botManager.createBot(name, player.getLocation());
                player.sendMessage("§aBot '" + name + "' spawned.");
                return true;
            }
            case "delete": {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mineflayer delete <name>");
                    return true;
                }
                String name = args[1];
                if (botManager.removeBot(name)) {
                    player.sendMessage("§aBot '" + name + "' removed.");
                } else {
                    player.sendMessage("§cBot '" + name + "' not found.");
                }
                return true;
            }
        }
        return false;
    }
}
