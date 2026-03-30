package dev.oakheart.raidcooldown;

import dev.oakheart.raidcooldown.commands.RaidCooldownCommand;
import dev.oakheart.raidcooldown.config.ConfigManager;
import dev.oakheart.raidcooldown.cooldown.CooldownManager;
import dev.oakheart.raidcooldown.listeners.RaidListener;
import dev.oakheart.raidcooldown.placeholder.RaidCooldownExpansion;
import dev.oakheart.message.MessageManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
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

        messageManager = new MessageManager(this, getLogger());
        messageManager.load();

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

        messageManager.reload();
        cooldownManager.restartTasks();
        return true;
    }

    /**
     * Format a duration using the time-format suffixes from messages.yml.
     */
    public String formatDuration(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "0" + messageManager.getConfig().getString("time-format.second", "s");
        }

        String hourSuffix = messageManager.getConfig().getString("time-format.hour", "h ");
        String minuteSuffix = messageManager.getConfig().getString("time-format.minute", "m ");
        String secondSuffix = messageManager.getConfig().getString("time-format.second", "s");

        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder result = new StringBuilder();
        if (hours > 0) result.append(hours).append(hourSuffix);
        if (minutes > 0) result.append(minutes).append(minuteSuffix);
        if (seconds > 0 || result.isEmpty()) result.append(seconds).append(secondSuffix);

        return result.toString().trim();
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
