package net.vanillymc.raidcooldown.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.logging.Logger;

public class ConfigManager {

    private final JavaPlugin plugin;
    private final Logger logger;

    private FileConfiguration config;
    private File cooldownFile;
    private FileConfiguration cooldownConfig;

    // Config constants
    private static final String COOLDOWN_SECONDS_KEY = "raidCooldownSeconds";
    private static final int DEFAULT_COOLDOWN_SECONDS = 86400; // 24 hours

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        initialize();
    }

    private void initialize() {
        // Save default config if it doesn't exist
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();

        // Set up cooldown data file
        setupCooldownFile();
    }

    private void setupCooldownFile() {
        cooldownFile = new File(plugin.getDataFolder(), "cooldowns.yml");

        if (!cooldownFile.exists()) {
            try {
                if (!cooldownFile.getParentFile().exists()) {
                    cooldownFile.getParentFile().mkdirs();
                }
                cooldownFile.createNewFile();
                logger.info("Created new cooldowns.yml file");
            } catch (IOException e) {
                logger.severe("Could not create cooldowns.yml file: " + e.getMessage());
                throw new RuntimeException("Failed to create cooldown file", e);
            }
        }

        this.cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
        logger.info("Configuration reloaded successfully");
    }

    // Main config getters
    public Duration getCooldownDuration() {
        int seconds = config.getInt(COOLDOWN_SECONDS_KEY, DEFAULT_COOLDOWN_SECONDS);
        return Duration.ofSeconds(Math.max(0, seconds)); // Ensure non-negative
    }

    public String getMessage(String key) {
        return config.getString("messages." + key, "Message not found: " + key);
    }

    public String getMessage(String key, String defaultValue) {
        return config.getString("messages." + key, defaultValue);
    }

    // Cooldown data file operations
    public FileConfiguration getCooldownConfig() {
        return cooldownConfig;
    }

    public void saveCooldownConfig() {
        try {
            cooldownConfig.save(cooldownFile);
        } catch (IOException e) {
            logger.severe("Could not save cooldown data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void reloadCooldownConfig() {
        this.cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
    }

    // Validation methods
    public boolean isValidConfig() {
        return config.contains(COOLDOWN_SECONDS_KEY) &&
                config.contains("messages") &&
                getCooldownDuration().getSeconds() >= 0;
    }

    // Getters for specific config values with validation
    public int getCooldownSeconds() {
        return Math.max(0, config.getInt(COOLDOWN_SECONDS_KEY, DEFAULT_COOLDOWN_SECONDS));
    }
}