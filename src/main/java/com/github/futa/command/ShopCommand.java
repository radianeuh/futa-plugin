package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.ShopConfig;
import com.github.futa.module.Shop;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class ShopCommand extends Command {

    ShopConfig config = FutaPlugin.PLUGIN_CONFIG.shop;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("shop")
                .category(CommandCategory.MODULE)
                .description("""
                        打广告.
                        """)
                .usageLines(
                        "on/off",
                        "whisper on/off",
                        "whilePlayerConnected on/off",
                        "delaySeconds <int>",
                        "randomOrder on/off",
                        "appendRandom on/off",
                        "list",
                        "clear",
                        "add <message>",
                        "addAt <index> <message>",
                        "del <index>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("shop")
                .then(argument("toggle", toggle()).executes(c -> {
                    config.enabled = getToggle(c, "toggle");
                    MODULE.get(Shop.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Shop " + toggleStrCaps(config.enabled));
                    return OK;
                }))
                .then(literal("whisper")
                        .then(argument("toggle", toggle()).executes(c -> {
                            config.whisper = getToggle(c, "toggle");
                            c.getSource().getEmbed()
                                    .title("Whisper " + toggleStrCaps(config.whisper));
                            return OK;
                        })))
                .then(literal("whilePlayerConnected")
                        .then(argument("toggle", toggle()).executes(c -> {
                            config.whilePlayerConnected = getToggle(c, "toggle");
                            c.getSource().getEmbed()
                                    .title("While Player Connected " + toggleStrCaps(config.whilePlayerConnected));
                            return OK;
                        })))
                .then(literal("delaySeconds")
                        .then(argument("delaySeconds", integer(1)).executes(c -> {
                            config.delaySeconds = IntegerArgumentType.getInteger(c, "delaySeconds");
                            c.getSource().getEmbed()
                                    .title("Delay Updated!");
                            return OK;
                        })))
                .then(literal("randomOrder")
                        .then(argument("toggle", toggle()).executes(c -> {
                            config.randomOrder = getToggle(c, "toggle");
                            c.getSource().getEmbed()
                                    .title("Random Order " + toggleStrCaps(config.randomOrder));
                            return OK;
                        })))
                .then(literal("appendRandom")
                        .then(argument("toggle", toggle()).executes(c -> {
                            config.appendRandom = getToggle(c, "toggle");
                            c.getSource().getEmbed()
                                    .title("Append Random " + toggleStrCaps(config.appendRandom));
                            return OK;
                        })))
                .then(literal("list").executes(c -> {
                    c.getSource().getEmbed()
                            .title("Status");
                    return OK;
                }))
                .then(literal("clear").executes(c -> {
                    config.messages.clear();
                    c.getSource().getEmbed()
                            .title("Messages Cleared!");
                    return OK;
                }))
                .then(literal("add")
                        .then(argument("message", greedyString()).executes(c -> {
                            final String message = StringArgumentType.getString(c, "message");
                            config.messages.add(message);
                            c.getSource().getEmbed()
                                    .primaryColor()
                                    .title("Message Added!");
                            return OK;
                        })))
                .then(literal("addAt")
                        .then(argument("index", integer(0))
                                .then(argument("message", greedyString()).executes(c -> {
                                    final int index = IntegerArgumentType.getInteger(c, "index");
                                    final String message = StringArgumentType.getString(c, "message");
                                    try {
                                        config.messages.add(index, message);
                                        c.getSource().getEmbed()
                                                .title("Message Added!");
                                        return OK;
                                    } catch (final Exception e) {
                                        c.getSource().getEmbed()
                                                .title("Invalid Index!");
                                        return ERROR;
                                    }
                                }))))
                .then(literal("del")
                        .then(argument("index", integer(0)).executes(c -> {
                            final int index = IntegerArgumentType.getInteger(c, "index");
                            try {
                                config.messages.remove(index);
                                addListDescription(c.getSource().getEmbed()
                                        .title("Message Removed!"));
                                return OK;
                            } catch (final Exception e) {
                                c.getSource().getEmbed()
                                        .title("Invalid Index!");
                                return ERROR;
                            }
                        })));
    }

    @Override
    public void defaultEmbed(final Embed builder) {
        addListDescription(builder.description("""
                **不知道.
                """))
                .addField("Shop", toggleStr(config.enabled), false)
                .addField("Whisper", toggleStr(config.whisper), false)
                .addField("While Player Connected", toggleStr(config.whilePlayerConnected), false)
                .addField("Delay", config.delaySeconds, false)
                .addField("Random Order", toggleStr(config.randomOrder), false)
                .addField("Append Random", toggleStr(config.appendRandom), false)
                .primaryColor();
    }

    private Embed addListDescription(final Embed embedBuilder) {
        final List<String> messages = new ArrayList<>();
        for (int index = 0; index < config.messages.size(); index++) {
            messages.add("`" + index + ":` " + config.messages.get(index));
        }
        String str = String.join("\n", messages);
        if (embedBuilder.isDescriptionPresent())
            return embedBuilder.description(embedBuilder.description() + str);
        else
            return embedBuilder.description(str);
    }
}
