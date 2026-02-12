package dev.oakheart.raidcooldown.listeners;

import dev.oakheart.raidcooldown.cooldown.CooldownManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.jetbrains.annotations.NotNull;

public class RaidListener implements Listener {

    private final CooldownManager cooldownManager;

    public RaidListener(@NotNull CooldownManager cooldownManager) {
        this.cooldownManager = cooldownManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidTrigger(@NotNull RaidTriggerEvent event) {
        Player player = event.getPlayer();

        // Atomically check if player can start raid and set cooldown
        // This prevents race conditions
        if (!cooldownManager.canStartRaidAndSetCooldown(player)) {
            event.setCancelled(true);
        }
    }
}
