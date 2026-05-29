package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.EnchantBookSorterConfig;
import com.github.futa.module.EnchantBookSorterModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.mc.block.BlockPos;

import java.util.Map;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class EnchantBookSorterCommand extends Command {

    EnchantBookSorterConfig sorterConfig = FutaPlugin.PLUGIN_CONFIG.enchantBookSorter;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("enchantbooksorter")
                .category(CommandCategory.MODULE)
                .description("""
                        Automatically sorts enchantment books into categorized chests.

                        Sorts enchanted books from source chests into designated enchantment-specific chests.
                        Books without designated chests are placed in misc chests.

                        The module will collect books from source chests, identify enchantment types,
                        and distribute them to appropriate storage locations automatically.
                        """)
                .usageLines(
                        "on/off",
                        "sourceChest add <x> <y> <z>",
                        "sourceChest del <index>",
                        "sourceChest clear",
                        "sourceChest list",
                        "enchantmentChest add <enchantment> <x> <y> <z>",
                        "enchantmentChest del <enchantment>",
                        "enchantmentChest clear",
                        "enchantmentChest list",
                        "miscChest add <x> <y> <z>",
                        "miscChest del <index>",
                        "miscChest clear",
                        "miscChest list",
                        "delayBetweenActions <ticks>",
                        "actionDelayTick <ticks>",
                        "restDuration <ticks>",
                        "debug <true/false>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("enchantbooksorter")
                .then(argument("toggle", toggle()).executes(c -> {
                    sorterConfig.enabled = getToggle(c, "toggle");
                    MODULE.get(EnchantBookSorterModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Enchant Book Sorter " + toggleStrCaps(sorterConfig.enabled))
                            .primaryColor();
                    return OK;
                }))
                .then(literal("sourceChest")
                        .then(literal("add").then(argument("pos", blockPos()).executes(c -> {
                            sorterConfig.sourceChests.add(getBlockPos(c, "pos"));
                            c.getSource().getEmbed()
                                    .title("Source Chest Added");
                            return OK;
                        })))
                        .then(literal("del").then(argument("index", integer(0, 100)).executes(c -> {
                            int index = getInteger(c, "index");
                            if (index < 0 || index >= sorterConfig.sourceChests.size()) {
                                c.getSource().getEmbed()
                                        .title("Invalid Index")
                                        .description("Index must be between 0 and " + (sorterConfig.sourceChests.size() - 1));
                                return ERROR;
                            }
                            sorterConfig.sourceChests.remove(index);
                            c.getSource().getEmbed()
                                    .title("Source Chest Removed");
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            sorterConfig.sourceChests.clear();
                            c.getSource().getEmbed()
                                    .title("Source Chests Cleared");
                            return OK;
                        }))
                        .then(literal("list").executes(c -> {
                            if (sorterConfig.sourceChests.isEmpty()) {
                                c.getSource().getEmbed()
                                        .title("Source Chests")
                                        .description("No source chests configured.");
                                return OK;
                            }

                            StringBuilder sb = new StringBuilder();
                            sb.append("Source Chests:\n\n");
                            for (int i = 0; i < sorterConfig.sourceChests.size(); i++) {
                                sb.append("**").append(i).append("**: ")
                                        .append("||").append(CONFIG.discord.reportCoords ? sorterConfig.sourceChests.get(i) : "Coords disabled")
                                        .append("||\n");
                            }

                            c.getSource().getEmbed()
                                    .title("Source Chests")
                                    .description(sb.toString());
                            return OK;
                        })))
                .then(literal("enchantmentChest")
                        .then(literal("add").then(argument("enchantment", string()).then(argument("pos", blockPos()).executes(c -> {
                            String enchantment = getString(c, "enchantment").toLowerCase();
                            sorterConfig.enchantmentChests.put(enchantment, getBlockPos(c, "pos"));
                            c.getSource().getEmbed()
                                    .title("Enchantment Chest Added")
                                    .description("Added chest for: " + enchantment);
                            return OK;
                        }))))
                        .then(literal("del").then(argument("enchantment", string()).executes(c -> {
                            String enchantment = getString(c, "enchantment").toLowerCase();
                            if (sorterConfig.enchantmentChests.remove(enchantment) != null) {
                                c.getSource().getEmbed()
                                        .title("Enchantment Chest Removed")
                                        .description("Removed chest for: " + enchantment);
                            } else {
                                c.getSource().getEmbed()
                                        .title("Enchantment Not Found")
                                        .description("No chest configured for: " + enchantment);
                            }
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            sorterConfig.enchantmentChests.clear();
                            c.getSource().getEmbed()
                                    .title("Enchantment Chests Cleared");
                            return OK;
                        }))
                        .then(literal("list").executes(c -> {
                            if (sorterConfig.enchantmentChests.isEmpty()) {
                                c.getSource().getEmbed()
                                        .title("Enchantment Chests")
                                        .description("No enchantment chests configured.");
                                return OK;
                            }

                            StringBuilder sb = new StringBuilder();
                            sb.append("Enchantment Chests:\n\n");
                            for (Map.Entry<String, BlockPos> entry : sorterConfig.enchantmentChests.entrySet()) {
                                sb.append("**").append(entry.getKey()).append("**: ")
                                        .append("||").append(CONFIG.discord.reportCoords ? entry.getValue() : "Coords disabled")
                                        .append("||\n");
                            }

                            c.getSource().getEmbed()
                                    .title("Enchantment Chests")
                                    .description(sb.toString());
                            return OK;
                        })))
                .then(literal("miscChest")
                        .then(literal("add").then(argument("pos", blockPos()).executes(c -> {
                            sorterConfig.miscChests.add(getBlockPos(c, "pos"));
                            c.getSource().getEmbed()
                                    .title("Misc Chest Added");
                            return OK;
                        })))
                        .then(literal("del").then(argument("index", integer(0, 100)).executes(c -> {
                            int index = getInteger(c, "index");
                            if (index < 0 || index >= sorterConfig.miscChests.size()) {
                                c.getSource().getEmbed()
                                        .title("Invalid Index")
                                        .description("Index must be between 0 and " + (sorterConfig.miscChests.size() - 1));
                                return ERROR;
                            }
                            sorterConfig.miscChests.remove(index);
                            c.getSource().getEmbed()
                                    .title("Misc Chest Removed");
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            sorterConfig.miscChests.clear();
                            c.getSource().getEmbed()
                                    .title("Misc Chests Cleared");
                            return OK;
                        }))
                        .then(literal("list").executes(c -> {
                            if (sorterConfig.miscChests.isEmpty()) {
                                c.getSource().getEmbed()
                                        .title("Misc Chests")
                                        .description("No misc chests configured.");
                                return OK;
                            }

                            StringBuilder sb = new StringBuilder();
                            sb.append("Misc Chests:\n\n");
                            for (int i = 0; i < sorterConfig.miscChests.size(); i++) {
                                sb.append("**").append(i).append("**: ")
                                        .append("||").append(CONFIG.discord.reportCoords ? sorterConfig.miscChests.get(i) : "Coords disabled")
                                        .append("||\n");
                            }

                            c.getSource().getEmbed()
                                    .title("Misc Chests")
                                    .description(sb.toString());
                            return OK;
                        })))
                .then(literal("delayBetweenActions").then(argument("ticks", integer(1, 1000)).executes(c -> {
                    sorterConfig.delayBetweenActions = getInteger(c, "ticks");
                    c.getSource().getEmbed()
                            .title("Delay Between Actions Set")
                            .description("Set to " + sorterConfig.delayBetweenActions + " ticks");
                    return OK;
                })))
                .then(literal("actionDelayTick").then(argument("ticks", integer(1, 100)).executes(c -> {
                    sorterConfig.actionDelayTick = getInteger(c, "ticks");
                    c.getSource().getEmbed()
                            .title("Action Delay Tick Set")
                            .description("Set to " + sorterConfig.actionDelayTick + " ticks");
                    return OK;
                })))
                .then(literal("restDuration").then(argument("ticks", integer(0, 2000)).executes(c -> {
                    sorterConfig.restDuration = getInteger(c, "ticks");
                    c.getSource().getEmbed()
                            .title("Rest Duration Set")
                            .description("Set to " + sorterConfig.restDuration + " ticks");
                    return OK;
                })))
                .then(literal("debug").then(argument("toggle", toggle()).executes(c -> {
                    sorterConfig.debugMode = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Debug Mode " + (sorterConfig.debugMode ? "Enabled" : "Disabled"));
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.title("Enchant Book Sorter Configuration")
                .addField("Enabled", toggleStr(sorterConfig.enabled))
                .addField("Source Chests", sorterConfig.sourceChests.size() + " configured")
                .addField("Enchantment Chests", sorterConfig.enchantmentChests.size() + " configured")
                .addField("Misc Chests", sorterConfig.miscChests.size() + " configured")
                .addField("Delay Between Actions", sorterConfig.delayBetweenActions + " ticks")
                .addField("Action Delay Tick", sorterConfig.actionDelayTick + " ticks")
                .addField("Rest Duration", sorterConfig.restDuration + " ticks")
                .addField("Debug Mode", toggleStr(sorterConfig.debugMode))
                .primaryColor();
    }
}
