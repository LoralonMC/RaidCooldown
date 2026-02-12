package dev.oakheart.raidcooldown;

import dev.oakheart.raidcooldown.command.RaidCooldownCommand;
import dev.oakheart.raidcooldown.config.ConfigManager;
import dev.oakheart.raidcooldown.cooldown.CooldownManager;
import dev.oakheart.raidcooldown.listeners.RaidListener;
import dev.oakheart.raidcooldown.message.MessageManager;
import dev.oakheart.raidcooldown.placeholder.RaidCooldownExpansion;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

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

            getLogger().info("RaidCooldown plugin has been enabled!");
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
        getLogger().info("RaidCooldown plugin has been disabled!");
    }

    private void initializeComponents() {
        // Initialize in dependency order
        this.configManager = new ConfigManager(this);
        configManager.load();
        this.messageManager = new MessageManager(configManager);
        this.cooldownManager = new CooldownManager(this, configManager, messageManager);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new RaidListener(cooldownManager), this
        );
    }

    private void registerCommands() {
        new RaidCooldownCommand(this, cooldownManager, messageManager, configManager).register();
    }

    private void initializeMetrics() {
        // Initialize bStats metrics
        int pluginId = 26656;
        new Metrics(this, pluginId);
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RaidCooldownExpansion(this).register();
            getLogger().info("PlaceholderAPI integration enabled!");
        }
    }

    // Getters for other classes that might need access
    @NotNull
    public ConfigManager getConfigManager() {
        return configManager;
    }

    @NotNull
    public MessageManager getMessageManager() {
        return messageManager;
    }

    @NotNull
    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
}
