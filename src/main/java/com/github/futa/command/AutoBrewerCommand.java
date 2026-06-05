package com.github.futa.command;

import com.github.futa.config.AutoBrewerConfig;
import com.github.futa.module.AutoBrewerModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import java.util.ArrayList;
import java.util.List;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.LongArgumentType.getLong;
import static com.mojang.brigadier.arguments.LongArgumentType.longArg;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoBrewerCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autobrewer")
                .category(CommandCategory.MODULE)
                .description("""
                        自动酿造药水模块。

                        从原料箱收集水瓶、烈焰粉和材料，在酿造台自动酿造药水，
                        支持多步配方（如先酿粗制药水再酿最终药水）。

                        酿造台槽位: 0-2药水瓶, 3材料, 4燃料(烈焰粉)
                        材料名称: nether_wart, sugar, spider_eye, fermented_spider_eye,
                                  ghast_tear, magma_cream, glistering_melon_slice,
                                  blaze_powder, golden_carrot, dragon_breath,
                                  glowstone_dust, redstone, gunpowder, phantom_membrane
                        """)
                .usageLines(
                        "on/off",
                        "brewingStand <x> <y> <z>",
                        "sourceChests add <x> <y> <z>",
                        "sourceChests del <index>",
                        "sourceChests clear",
                        "sourceChests list",
                        "resultChest <x> <y> <z>",
                        "recipes add <name> <ingredient1> [ingredient2] ...",
                        "recipes del <index>",
                        "recipes clear",
                        "recipes list",
                        "brewWaitTicks <ticks>",
                        "delayBetweenActions <ms>",
                        "debug on/off"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autobrewer")
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.autoBrewer.enabled = getToggle(c, "toggle");
                    MODULE.get(AutoBrewerModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Auto Brewer " + toggleStrCaps(PLUGIN_CONFIG.autoBrewer.enabled))
                            .primaryColor();
                }))
                .then(literal("brewingStand").then(argument("pos", blockPos()).executes(c -> {
                    PLUGIN_CONFIG.autoBrewer.brewingStand = getBlockPos(c, "pos");
                    c.getSource().getEmbed()
                            .title("Brewing Stand Set")
                            .description("Set brewing stand to: " + formatPos(PLUGIN_CONFIG.autoBrewer.brewingStand));
                })))
                .then(literal("sourceChests")
                        .then(literal("add").then(argument("pos", blockPos()).executes(c -> {
                            var pos = getBlockPos(c, "pos");
                            if (!PLUGIN_CONFIG.autoBrewer.sourceChests.contains(pos)) {
                                PLUGIN_CONFIG.autoBrewer.sourceChests.add(pos);
                            }
                            c.getSource().getEmbed()
                                    .title("Source Chest Added")
                                    .description("Added chest at: " + formatPos(pos));
                            return OK;
                        })))
                        .then(literal("del").then(argument("index", integer(0, 100)).executes(c -> {
                            int index = getInteger(c, "index");
                            if (index >= 0 && index < PLUGIN_CONFIG.autoBrewer.sourceChests.size()) {
                                var removed = PLUGIN_CONFIG.autoBrewer.sourceChests.remove(index);
                                c.getSource().getEmbed()
                                        .title("Source Chest Removed")
                                        .description("Removed chest at: " + formatPos(removed));
                            } else {
                                c.getSource().getEmbed()
                                        .title("Invalid Index")
                                        .description("Index must be between 0 and " + (PLUGIN_CONFIG.autoBrewer.sourceChests.size() - 1));
                                return ERROR;
                            }
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            int count = PLUGIN_CONFIG.autoBrewer.sourceChests.size();
                            PLUGIN_CONFIG.autoBrewer.sourceChests.clear();
                            c.getSource().getEmbed()
                                    .title("Source Chests Cleared")
                                    .description("Cleared " + count + " chests");
                        }))
                        .then(literal("list").executes(c -> {
                            if (PLUGIN_CONFIG.autoBrewer.sourceChests.isEmpty()) {
                                c.getSource().getEmbed()
                                        .title("Source Chests")
                                        .description("No source chests configured.");
                            } else {
                                StringBuilder sb = new StringBuilder();
                                sb.append("Configured source chests:\n\n");
                                for (int i = 0; i < PLUGIN_CONFIG.autoBrewer.sourceChests.size(); i++) {
                                    var pos = PLUGIN_CONFIG.autoBrewer.sourceChests.get(i);
                                    sb.append("**").append(i).append("**: ").append(formatPos(pos)).append("\n");
                                }
                                c.getSource().getEmbed()
                                        .title("Source Chests")
                                        .description(sb.toString());
                            }
                        }))
                )
                .then(literal("resultChest").then(argument("pos", blockPos()).executes(c -> {
                    PLUGIN_CONFIG.autoBrewer.resultChest = getBlockPos(c, "pos");
                    c.getSource().getEmbed()
                            .title("Result Chest Set")
                            .description("Set result chest to: " + formatPos(PLUGIN_CONFIG.autoBrewer.resultChest));
                })))
                .then(literal("recipes")
                        .then(literal("add").then(argument("name", string())
                                .then(argument("ingredients", string()).executes(c -> {
                                    String name = getString(c, "name");
                                    String ingredientsStr = getString(c, "ingredients");
                                    List<String> ingredients = List.of(ingredientsStr.split(","));
                                    PLUGIN_CONFIG.autoBrewer.recipes.add(new AutoBrewerConfig.BrewRecipe(name, new ArrayList<>(ingredients)));
                                    c.getSource().getEmbed()
                                            .title("Recipe Added")
                                            .description("Added recipe: " + name + "\nIngredients: " + ingredientsStr);
                                    return OK;
                                }))))
                        .then(literal("del").then(argument("index", integer(0, 100)).executes(c -> {
                            int index = getInteger(c, "index");
                            if (index >= 0 && index < PLUGIN_CONFIG.autoBrewer.recipes.size()) {
                                var removed = PLUGIN_CONFIG.autoBrewer.recipes.remove(index);
                                c.getSource().getEmbed()
                                        .title("Recipe Removed")
                                        .description("Removed recipe: " + removed.name);
                            } else {
                                c.getSource().getEmbed()
                                        .title("Invalid Index")
                                        .description("Index must be between 0 and " + (PLUGIN_CONFIG.autoBrewer.recipes.size() - 1));
                                return ERROR;
                            }
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            int count = PLUGIN_CONFIG.autoBrewer.recipes.size();
                            PLUGIN_CONFIG.autoBrewer.recipes.clear();
                            c.getSource().getEmbed()
                                    .title("Recipes Cleared")
                                    .description("Cleared " + count + " recipes");
                        }))
                        .then(literal("list").executes(c -> {
                            if (PLUGIN_CONFIG.autoBrewer.recipes.isEmpty()) {
                                c.getSource().getEmbed()
                                        .title("Recipes")
                                        .description("No recipes configured.");
                            } else {
                                StringBuilder sb = new StringBuilder();
                                sb.append("Configured recipes:\n\n");
                                for (int i = 0; i < PLUGIN_CONFIG.autoBrewer.recipes.size(); i++) {
                                    var recipe = PLUGIN_CONFIG.autoBrewer.recipes.get(i);
                                    sb.append("**").append(i).append("**: ").append(recipe.name)
                                            .append(" → ").append(String.join(" → ", recipe.ingredients)).append("\n");
                                }
                                c.getSource().getEmbed()
                                        .title("Recipes")
                                        .description(sb.toString());
                            }
                        }))
                )
                .then(literal("brewWaitTicks").then(argument("ticks", integer(100, 1000)).executes(c -> {
                    PLUGIN_CONFIG.autoBrewer.brewWaitTicks = getInteger(c, "ticks");
                    c.getSource().getEmbed()
                            .title("Brew Wait Ticks Set")
                            .description("Set brew wait ticks to: " + PLUGIN_CONFIG.autoBrewer.brewWaitTicks);
                })))
                .then(literal("delayBetweenActions").then(argument("ms", longArg(50L, 10000L)).executes(c -> {
                    PLUGIN_CONFIG.autoBrewer.delayBetweenActions = getLong(c, "ms");
                    c.getSource().getEmbed()
                            .title("Action Delay Set")
                            .description("Set action delay to: " + PLUGIN_CONFIG.autoBrewer.delayBetweenActions + "ms");
                })))
                .then(literal("debug").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.autoBrewer.debugMode = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Debug Mode " + toggleStrCaps(PLUGIN_CONFIG.autoBrewer.debugMode));
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        StringBuilder sourceChestsStr = new StringBuilder();
        if (PLUGIN_CONFIG.autoBrewer.sourceChests.isEmpty()) {
            sourceChestsStr.append("[None]");
        } else {
            for (int i = 0; i < PLUGIN_CONFIG.autoBrewer.sourceChests.size(); i++) {
                if (i > 0) sourceChestsStr.append(", ");
                sourceChestsStr.append(i).append(":").append(formatPos(PLUGIN_CONFIG.autoBrewer.sourceChests.get(i)));
            }
        }

        StringBuilder recipesStr = new StringBuilder();
        if (PLUGIN_CONFIG.autoBrewer.recipes.isEmpty()) {
            recipesStr.append("[None]");
        } else {
            for (int i = 0; i < PLUGIN_CONFIG.autoBrewer.recipes.size(); i++) {
                var recipe = PLUGIN_CONFIG.autoBrewer.recipes.get(i);
                if (i > 0) recipesStr.append("\n");
                recipesStr.append("**").append(i).append("**: ").append(recipe.name)
                        .append(" → ").append(String.join(" → ", recipe.ingredients));
            }
        }

        embed
                .addField("Auto Brewer", toggleStr(PLUGIN_CONFIG.autoBrewer.enabled))
                .addField("Brewing Stand", "||" + (PLUGIN_CONFIG.autoBrewer.debugMode ? formatPos(PLUGIN_CONFIG.autoBrewer.brewingStand) : "Hidden") + "||")
                .addField("Source Chests", sourceChestsStr.toString())
                .addField("Result Chest", "||" + (PLUGIN_CONFIG.autoBrewer.debugMode ? formatPos(PLUGIN_CONFIG.autoBrewer.resultChest) : "Hidden") + "||")
                .addField("Recipes", recipesStr.toString())
                .addField("Brew Wait Ticks", PLUGIN_CONFIG.autoBrewer.brewWaitTicks + " ticks")
                .addField("Action Delay", PLUGIN_CONFIG.autoBrewer.delayBetweenActions + "ms")
                .addField("Debug Mode", toggleStr(PLUGIN_CONFIG.autoBrewer.debugMode))
                .primaryColor();
    }

    private String formatPos(Object pos) {
        if (pos == null) return "null";
        return pos.toString().replace("BlockPos{x=", "").replace("}", "").replace(", y=", ", ").replace(", z=", ", ");
    }
}
