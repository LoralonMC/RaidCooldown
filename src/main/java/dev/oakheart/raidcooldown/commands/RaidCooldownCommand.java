package dev.oakheart.raidcooldown.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.oakheart.raidcooldown.RaidCooldown;
import dev.oakheart.raidcooldown.config.ConfigManager;
import dev.oakheart.raidcooldown.cooldown.CooldownManager;
import dev.oakheart.command.CommandRegistrar;
import dev.oakheart.message.MessageManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
        CommandRegistrar.register(plugin, buildCommand(), "Manage raid cooldowns", List.of("rc", "raidcd"));
    }

    private LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("raidcooldown")
                // Base command - self check (player only)
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        messageManager.send(sender, "only-players");
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
                                        messageManager.send(sender, "player-not-found",
                                                Placeholder.unparsed("player", "unknown"));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (players.isEmpty()) {
                                        messageManager.send(sender, "player-not-found",
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
                                        messageManager.send(sender, "player-not-found",
                                                Placeholder.unparsed("player", "unknown"));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (players.isEmpty()) {
                                        messageManager.send(sender, "player-not-found",
                                                Placeholder.unparsed("player", "unknown"));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    Player target = players.getFirst();
                                    cooldownManager.removeCooldown(target.getUniqueId());
                                    messageManager.send(sender, "cooldown-reset",
                                            Placeholder.unparsed("player", target.getName()));
                                    messageManager.send(target, "cooldown-reset-notification");
                                    return Command.SINGLE_SUCCESS;
                                })))
                // reload
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("raidcooldown.reload"))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            try {
                                if (plugin.reloadPlugin()) {
                                    messageManager.sendCommand(sender, "reload-success");
                                } else {
                                    messageManager.sendCommand(sender, "reload-error",
                                            Placeholder.unparsed("error", "Configuration validation failed"));
                                }
                            } catch (Exception e) {
                                messageManager.sendCommand(sender, "reload-error",
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

                            messageManager.send(sender, "info-header");
                            messageManager.send(sender, "info-active-cooldowns",
                                    Placeholder.unparsed("count", String.valueOf(activeCooldowns)));
                            messageManager.send(sender, "info-cooldown-duration",
                                    Placeholder.unparsed("duration", String.valueOf(cooldownHours)));
                            messageManager.send(sender, "info-config-valid",
                                    Placeholder.unparsed("valid", String.valueOf(true)));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
