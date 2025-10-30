package net.vanillymc.raidcooldown;

import net.vanillymc.raidcooldown.command.RaidCooldownCommand;
import net.vanillymc.raidcooldown.config.ConfigManager;
import net.vanillymc.raidcooldown.cooldown.CooldownManager;
import net.vanillymc.raidcooldown.listener.RaidListener;
import net.vanillymc.raidcooldown.message.MessageManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for RaidCooldown.
 * <p>
 * This plugin adds configurable cooldowns to Minecraft raids, preventing players from
 * triggering raids too frequently. Features include persistent storage, automatic cleanup,
 * comprehensive admin commands, and rich message formatting with MiniMessage support.
 * </p>
 *
 * @author Loralon
 * @version 1.1.0
 */
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

            getLogger().info("RaidCooldown plugin has been enabled!");
        } catch (Exception e) {
            getLogger().severe("Failed to enable RaidCooldown plugin: " + e.getMessage());
            e.printStackTrace();
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
        this.messageManager = new MessageManager(configManager);
        this.cooldownManager = new CooldownManager(this, configManager, messageManager);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new RaidListener(cooldownManager), this
        );
    }

    private void registerCommands() {
        RaidCooldownCommand commandExecutor = new RaidCooldownCommand(
                cooldownManager, messageManager, configManager
        );

        org.bukkit.command.PluginCommand command = getCommand("raidcooldown");
        if (command == null) {
            throw new IllegalStateException("Command 'raidcooldown' not found in plugin.yml!");
        }

        command.setExecutor(commandExecutor);
        command.setTabCompleter(commandExecutor);
    }

    // Getters for other classes that might need access
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