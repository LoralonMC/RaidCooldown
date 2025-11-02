package dev.oakheart.raidcooldown.listener;

import dev.oakheart.raidcooldown.cooldown.CooldownManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for raid trigger events and enforces cooldowns.
 * <p>
 * Intercepts {@link RaidTriggerEvent} at HIGH priority to check and enforce
 * player cooldowns before allowing raids to start. Uses atomic operations
 * to prevent race conditions in concurrent raid triggering scenarios.
 * </p>
 *
 * @author Loralon
 * @version 1.2.0
 */
public class RaidListener implements Listener {

    private final CooldownManager cooldownManager;

    public RaidListener(@NotNull CooldownManager cooldownManager) {
        this.cooldownManager = cooldownManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidTrigger(@NotNull RaidTriggerEvent event) {
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
