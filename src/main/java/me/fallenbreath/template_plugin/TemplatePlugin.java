package me.fallenbreath.template_plugin;

import org.bukkit.plugin.java.JavaPlugin;

public class TemplatePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info(getName() + " v" + getPluginMeta().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info(getName() + " disabled!");
    }
}
