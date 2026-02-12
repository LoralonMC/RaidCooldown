package dev.oakheart.raidcooldown.command;

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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
                        messageManager.sendMessage(sender, MessageManager.ONLY_PLAYERS);
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
                                        messageManager.sendMessage(sender, MessageManager.PLAYER_NOT_FOUND,
                                                Placeholder.unparsed("player", "unknown"));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (players.isEmpty()) {
                                        messageManager.sendMessage(sender, MessageManager.PLAYER_NOT_FOUND,
                                                Placeholder.unparsed("player", "unknown"));
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
                                        messageManager.sendMessage(sender, MessageManager.PLAYER_NOT_FOUND,
                                                Placeholder.unparsed("player", "unknown"));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (players.isEmpty()) {
                                        messageManager.sendMessage(sender, MessageManager.PLAYER_NOT_FOUND,
                                                Placeholder.unparsed("player", "unknown"));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    Player target = players.getFirst();
                                    cooldownManager.removeCooldown(target.getUniqueId());
                                    messageManager.sendMessage(sender, MessageManager.COOLDOWN_RESET,
                                            Placeholder.unparsed("player", target.getName()));
                                    messageManager.sendMessage(target, MessageManager.COOLDOWN_RESET_NOTIFICATION);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // reload
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("raidcooldown.reload"))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            try {
                                if (configManager.reload()) {
                                    cooldownManager.restartTasks();
                                    messageManager.sendMessage(sender, MessageManager.RELOAD_SUCCESS);
                                } else {
                                    messageManager.sendMessage(sender, MessageManager.RELOAD_ERROR,
                                            Placeholder.unparsed("error", "Configuration validation failed"));
                                }
                            } catch (Exception e) {
                                messageManager.sendMessage(sender, MessageManager.RELOAD_ERROR,
                                        Placeholder.unparsed("error", e.getMessage()));
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
                            boolean configValid = configManager.isValidConfig();

                            messageManager.sendMessage(sender, MessageManager.INFO_HEADER);
                            messageManager.sendMessage(sender, MessageManager.INFO_ACTIVE_COOLDOWNS,
                                    Placeholder.unparsed("count", String.valueOf(activeCooldowns)));
                            messageManager.sendMessage(sender, MessageManager.INFO_COOLDOWN_DURATION,
                                    Placeholder.unparsed("duration", String.valueOf(cooldownHours)));
                            messageManager.sendMessage(sender, MessageManager.INFO_CONFIG_VALID,
                                    Placeholder.unparsed("valid", String.valueOf(configValid)));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
