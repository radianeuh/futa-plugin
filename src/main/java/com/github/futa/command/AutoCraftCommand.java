package com.github.futa.command;

import com.github.futa.module.AutoCraftModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.LongArgumentType.getLong;
import static com.mojang.brigadier.arguments.LongArgumentType.longArg;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;
import static com.zenith.command.brigadier.ItemArgument.getItem;
import static com.zenith.command.brigadier.ItemArgument.item;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoCraftCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autocraft")
                .category(CommandCategory.MODULE)
                .description("""
                        Automatically crafts items using materials from source chests.
                        
                        Gathers materials from source chests, crafts items at nearby workbenches,
                        and stores results in a result chest. Supports golden apples, firework rockets,
                        and golden carrots with automatic intermediate crafting.
                        
                        `sourceChests` -> list of chest positions to gather materials from
                        `resultChest` -> chest position to store crafted items
                        `batchSize` -> how many items to craft per batch
                        `maxDistanceFromWorkbench` -> maximum distance to search for workbenches
                        `restSecend` -> sec to rest when out of materials
                        `delayBetweenActions` -> milliseconds to wait between actions
                        `allowHandCrafting` -> whether to allow crafting in player inventory
                        `enabledRecipes` -> list of recipes to craft (GOLDEN_APPLE, FIREWORK_ROCKET, GOLDEN_CARROT)
                        """)
                .usageLines(
                        "on/off",
                        "sourceChests add <x> <y> <z>",
                        "sourceChests del <index>",
                        "sourceChests clear",
                        "sourceChests list",
                        "resultChest <x> <y> <z>",
                        "recipes add <recipe>",
                        "recipes del <recipe>",
                        "recipes clear",
                        "recipes list",
                        "recipes available",
                        "batchSize <size>",
                        "maxDistanceFromWorkbench <blocks>",
                        "restSecend <s>",
                        "delayBetweenActions <milliseconds>",
                        "allowHandCrafting <true/false>",
                        "retryAttempts <attempts>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autocraft")
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.autoCraft.enabled = getToggle(c, "toggle");
                    MODULE.get(AutoCraftModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Auto Craft " + toggleStrCaps(PLUGIN_CONFIG.autoCraft.enabled))
                            .primaryColor();
                }))
                .then(literal("sourceChests")
                        .then(literal("add").then(argument("pos", blockPos()).executes(c -> {
                            var pos = getBlockPos(c, "pos");
                            if (!PLUGIN_CONFIG.autoCraft.sourceChests.contains(pos)) {
                                PLUGIN_CONFIG.autoCraft.sourceChests.add(pos);
                            }
                            c.getSource().getEmbed()
                                    .title("Source Chest Added")
                                    .description("Added chest at: " + formatPos(pos));
                            return OK;
                        })))
                        .then(literal("del").then(argument("index", integer(0, 100)).executes(c -> {
                            int index = getInteger(c, "index");
                            if (index >= 0 && index < PLUGIN_CONFIG.autoCraft.sourceChests.size()) {
                                var removed = PLUGIN_CONFIG.autoCraft.sourceChests.remove(index);
                                c.getSource().getEmbed()
                                        .title("Source Chest Removed")
                                        .description("Removed chest at: " + formatPos(removed));
                            } else {
                                c.getSource().getEmbed()
                                        .title("Invalid Index")
                                        .description("Index must be between 0 and " + (PLUGIN_CONFIG.autoCraft.sourceChests.size() - 1));
                                return ERROR;
                            }
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            int count = PLUGIN_CONFIG.autoCraft.sourceChests.size();
                            PLUGIN_CONFIG.autoCraft.sourceChests.clear();
                            c.getSource().getEmbed()
                                    .title("Source Chests Cleared")
                                    .description("Cleared " + count + " chests");
                        }))
                        .then(literal("list").executes(c -> {
                            if (PLUGIN_CONFIG.autoCraft.sourceChests.isEmpty()) {
                                c.getSource().getEmbed()
                                        .title("Source Chests")
                                        .description("No source chests configured. Use `sourceChests add <x> <y> <z>` to add chests.");
                            } else {
                                StringBuilder sb = new StringBuilder();
                                sb.append("Configured source chests:\n\n");
                                for (int i = 0; i < PLUGIN_CONFIG.autoCraft.sourceChests.size(); i++) {
                                    var pos = PLUGIN_CONFIG.autoCraft.sourceChests.get(i);
                                    sb.append("**").append(i).append("**: ").append(formatPos(pos)).append("\n");
                                }
                                c.getSource().getEmbed()
                                        .title("Source Chests")
                                        .description(sb.toString());
                            }
                        }))
                )
                .then(literal("resultChest").then(argument("pos", blockPos()).executes(c -> {
                    PLUGIN_CONFIG.autoCraft.resultChest = getBlockPos(c, "pos");
                    c.getSource().getEmbed()
                            .title("Result Chest Set")
                            .description("Set result chest to: " + formatPos(PLUGIN_CONFIG.autoCraft.resultChest));
                })))
                .then(literal("workbench").then(argument("pos", blockPos()).executes(c -> {
                    PLUGIN_CONFIG.autoCraft.workbench = getBlockPos(c, "pos");
                    c.getSource().getEmbed()
                            .title("workbench Set")
                            .description("Set workbench to: " + formatPos(PLUGIN_CONFIG.autoCraft.workbench));
                })))
                .then(literal("batchSize").then(argument("size", integer(1, 64)).executes(c -> {
                    PLUGIN_CONFIG.autoCraft.batchSize = getInteger(c, "size");
                    c.getSource().getEmbed()
                            .title("Batch Size Set")
                            .description("Set batch size to: " + PLUGIN_CONFIG.autoCraft.batchSize);
                })))
                .then(literal("maxDistanceFromWorkbench").then(argument("blocks", integer(1, 16)).executes(c -> {
                    PLUGIN_CONFIG.autoCraft.maxDistanceFromWorkbench = getInteger(c, "blocks");
                    c.getSource().getEmbed()
                            .title("Max Distance From Workbench Set")
                            .description("Set max distance to: " + PLUGIN_CONFIG.autoCraft.maxDistanceFromWorkbench + " blocks");
                })))
                .then(literal("restSecend").then(argument("s", integer(1, 60)).executes(c -> {
                    PLUGIN_CONFIG.autoCraft.restSecend = getInteger(c, "s");
                    c.getSource().getEmbed()
                            .title("Rest Time Set")
                            .description("Set rest time to: " + PLUGIN_CONFIG.autoCraft.restSecend + " s");
                })))
                .then(literal("delayBetweenActions").then(argument("milliseconds", longArg(50L, 10000L)).executes(c -> {
                    PLUGIN_CONFIG.autoCraft.delayBetweenActions = getLong(c, "milliseconds");
                    c.getSource().getEmbed()
                            .title("Action Delay Set")
                            .description("Set action delay to: " + PLUGIN_CONFIG.autoCraft.delayBetweenActions + "ms");
                })))
                .then(literal("allowHandCrafting").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.autoCraft.allowHandCrafting = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Hand Crafting " + (PLUGIN_CONFIG.autoCraft.allowHandCrafting ? "Enabled" : "Disabled"));
                    return OK;
                })))
                .then(literal("recipe").then(argument("item", item()).executes(c -> {
                    var itemData = getItem(c, "item");
                    PLUGIN_CONFIG.autoCraft.recipe = (itemData.name());
                    c.getSource().getEmbed()
                            .title("Item Added");
                    return OK;
                })))
                .then(literal("retryAttempts").then(argument("attempts", integer(1, 10)).executes(c -> {
                    PLUGIN_CONFIG.autoCraft.retryAttempts = getInteger(c, "attempts");
                    c.getSource().getEmbed()
                            .title("Retry Attempts Set")
                            .description("Set retry attempts to: " + PLUGIN_CONFIG.autoCraft.retryAttempts);
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        StringBuilder sourceChestsStr = new StringBuilder();
        if (PLUGIN_CONFIG.autoCraft.sourceChests.isEmpty()) {
            sourceChestsStr.append("[None]");
        } else {
            for (int i = 0; i < PLUGIN_CONFIG.autoCraft.sourceChests.size(); i++) {
                if (i > 0) sourceChestsStr.append(", ");
                sourceChestsStr.append(i).append(":").append(formatPos(PLUGIN_CONFIG.autoCraft.sourceChests.get(i)));
            }
        }



        embed
                .addField("Auto Craft", toggleStr(PLUGIN_CONFIG.autoCraft.enabled))
                .addField("Source Chests", sourceChestsStr.toString())
                .addField("Result Chest", "||" + (CONFIG.discord.reportCoords ? PLUGIN_CONFIG.autoCraft.resultChest : "Coords disabled") + "||")
                .addField("Enabled Recipe", PLUGIN_CONFIG.autoCraft.recipe)
                .addField("Batch Size", PLUGIN_CONFIG.autoCraft.batchSize)
                .addField("Max Distance From Workbench", PLUGIN_CONFIG.autoCraft.maxDistanceFromWorkbench + " blocks")
                .addField("Rest Time", PLUGIN_CONFIG.autoCraft.restSecend + " s")
                .addField("Action Delay", PLUGIN_CONFIG.autoCraft.delayBetweenActions + "ms")
                .addField("Allow Hand Crafting", toggleStr(PLUGIN_CONFIG.autoCraft.allowHandCrafting))
                .addField("Retry Attempts", PLUGIN_CONFIG.autoCraft.retryAttempts)
                .primaryColor();
    }

    private String formatPos(Object pos) {
        if (pos == null) return "null";
        return pos.toString().replace("BlockPos{x=", "").replace("}", "").replace(", y=", ", ").replace(", z=", ", ");
    }
}
