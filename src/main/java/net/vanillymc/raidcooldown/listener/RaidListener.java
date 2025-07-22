package net.vanillymc.raidcooldown.listener;

import net.vanillymc.raidcooldown.cooldown.CooldownManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.raid.RaidTriggerEvent;

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

        // Check if player can start a raid
        if (!cooldownManager.canStartRaid(player)) {
            event.setCancelled(true);
            return;
        }

        // Player can start the raid, set their cooldown
        cooldownManager.setCooldown(player);
    }
}