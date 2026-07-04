package devgbx9.mineflayer;

import org.bukkit.plugin.java.JavaPlugin;

public class MineflayerPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info(getName() + " v" + getPluginMeta().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info(getName() + " disabled!");
    }
}
