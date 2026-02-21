package dev.oakheart.raidcooldown.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.oakheart.raidcooldown.RaidCooldown;
import dev.oakheart.raidcooldown.config.ConfigManager;
import dev.oakheart.raidcooldown.cooldown.CooldownManager;
import dev.oakheart.raidcooldown.message.MessageManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class RaidCooldownCommand {

    private final RaidCooldown plugin;
    private final CooldownManager cooldownManager;
    private final MessageManager messageManager;
    private final ConfigManager configManager;

    public RaidCooldownCommand(RaidCooldown plugin, CooldownManager cooldownManager,
                               MessageManager messageManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.messageManager = messageManager;
        this.configManager = configManager;
    }

    public void register() {
        LifecycleEventManager<Plugin> manager = plugin.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(buildCommand(), "Manage raid cooldowns", List.of("rc", "raidcd"));
        });
    }

    private LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("raidcooldown")
                // Base command - self check (player only)
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        messageManager.sendOnlyPlayers(sender);
                        return Command.SINGLE_SUCCESS;
                    }
                    cooldownManager.sendCooldownStatus(player, player);
                    return Command.SINGLE_SUCCESS;
                })
                // check <player>
                .then(Commands.literal("check")
                        .requires(src -> src.getSender().hasPermission("raidcooldown.check"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                    List<Player> players;
                                    try {
                                        players = resolver.resolve(ctx.getSource());
                                    } catch (Exception e) {
                                        messageManager.sendPlayerNotFound(sender, "unknown");
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (players.isEmpty()) {
                                        messageManager.sendPlayerNotFound(sender, "unknown");
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    Player target = players.getFirst();
                                    cooldownManager.sendCooldownStatus(sender, target);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // reset <player>
                .then(Commands.literal("reset")
                        .requires(src -> src.getSender().hasPermission("raidcooldown.reset"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                    List<Player> players;
                                    try {
                                        players = resolver.resolve(ctx.getSource());
                                    } catch (Exception e) {
                                        messageManager.sendPlayerNotFound(sender, "unknown");
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (players.isEmpty()) {
                                        messageManager.sendPlayerNotFound(sender, "unknown");
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    Player target = players.getFirst();
                                    cooldownManager.removeCooldown(target.getUniqueId());
                                    messageManager.sendCooldownReset(sender, target.getName());
                                    messageManager.sendCooldownResetNotification(target);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // reload
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("raidcooldown.reload"))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            try {
                                if (plugin.reloadPlugin()) {
                                    messageManager.sendReloadSuccess(sender);
                                } else {
                                    messageManager.sendReloadError(sender, "Configuration validation failed");
                                }
                            } catch (Exception e) {
                                messageManager.sendReloadError(sender, e.getMessage());
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                // info
                .then(Commands.literal("info")
                        .requires(src -> src.getSender().hasPermission("raidcooldown.info"))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            int activeCooldowns = cooldownManager.getActiveCooldownCount();
                            long cooldownHours = configManager.getCooldownDuration().toHours();

                            messageManager.sendInfoHeader(sender);
                            messageManager.sendInfoActiveCooldowns(sender, activeCooldowns);
                            messageManager.sendInfoCooldownDuration(sender, cooldownHours);
                            messageManager.sendInfoConfigValid(sender, true);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
