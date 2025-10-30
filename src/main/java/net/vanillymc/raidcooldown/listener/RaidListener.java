package net.vanillymc.raidcooldown.listener;

import net.vanillymc.raidcooldown.cooldown.CooldownManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.raid.RaidTriggerEvent;

/**
 * Listens for raid trigger events and enforces cooldowns.
 * <p>
 * Intercepts {@link RaidTriggerEvent} at HIGH priority to check and enforce
 * player cooldowns before allowing raids to start. Uses atomic operations
 * to prevent race conditions in concurrent raid triggering scenarios.
 * </p>
 *
 * @author Loralon
 * @version 1.1.0
 */
public class RaidListener implements Listener {

    private final CooldownManager cooldownManager;

    public RaidListener(CooldownManager cooldownManager) {
        this.cooldownManager = cooldownManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidTrigger(RaidTriggerEvent event) {
        Player player = event.getPlayer();

        if (player == null) {
            return; // Safety check
        }

        // Atomically check if player can start raid and set cooldown
        // This prevents race conditions
        if (!cooldownManager.canStartRaidAndSetCooldown(player)) {
            event.setCancelled(true);
        }
    }
}