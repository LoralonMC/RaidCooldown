package dev.oakheart.raidcooldown;

import dev.oakheart.raidcooldown.commands.RaidCooldownCommand;
import dev.oakheart.raidcooldown.config.ConfigManager;
import dev.oakheart.raidcooldown.cooldown.CooldownManager;
import dev.oakheart.raidcooldown.listeners.RaidListener;
import dev.oakheart.raidcooldown.message.MessageManager;
import dev.oakheart.raidcooldown.placeholder.RaidCooldownExpansion;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class RaidCooldown extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private CooldownManager cooldownManager;

    @Override
    public void onEnable() {
        try {
            initializeComponents();
            registerListeners();
            registerCommands();
            initializeMetrics();
            registerPlaceholders();

            getLogger().info("RaidCooldown has been enabled!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable RaidCooldown", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (cooldownManager != null) {
            cooldownManager.shutdown();
        }
        getLogger().info("RaidCooldown has been disabled!");
    }

    private void initializeComponents() {
        configManager = new ConfigManager(this);
        configManager.load();

        messageManager = new MessageManager();
        messageManager.load(configManager.getConfig());

        cooldownManager = new CooldownManager(this, configManager, messageManager);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new RaidListener(cooldownManager), this);
    }

    private void registerCommands() {
        new RaidCooldownCommand(this, cooldownManager, messageManager, configManager).register();
    }

    private void initializeMetrics() {
        new Metrics(this, 26656);
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RaidCooldownExpansion(this).register();
            getLogger().info("PlaceholderAPI integration enabled!");
        }
    }

    public boolean reloadPlugin() {
        if (!configManager.reload()) {
            return false;
        }

        messageManager.load(configManager.getConfig());
        cooldownManager.restartTasks();
        return true;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
}
