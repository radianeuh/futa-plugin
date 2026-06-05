package com.github.futa.command;

import com.github.futa.module.ContainerStressTestModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class ContainerStressTestCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("stresschest")
                .category(CommandCategory.MODULE)
                .description("""
                        容器压力测试工具

                        测试打开箱子、提取物品的 tick 延迟和成功率
                        """)
                .usageLines(
                        "on/off",
                        "chest add <x> <y> <z>",
                        "chest del <index>",
                        "chest clear",
                        "chest list",
                        "repeat <count>",
                        "delays <d1> <d2> ..."
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("stresschest")
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.stressTest.enabled = getToggle(c, "toggle");
                    MODULE.get(ContainerStressTestModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Stress Test " + toggleStrCaps(PLUGIN_CONFIG.stressTest.enabled))
                            .primaryColor();
                }))
                .then(literal("chest")
                        .then(literal("add").then(argument("pos", blockPos()).executes(c -> {
                            var pos = getBlockPos(c, "pos");
                            if (!PLUGIN_CONFIG.stressTest.testChests.contains(pos)) {
                                PLUGIN_CONFIG.stressTest.testChests.add(pos);
                            }
                            c.getSource().getEmbed()
                                    .title("Chest Added")
                                    .description("Added chest at: " + pos);
                        })))
                        .then(literal("del").then(argument("index", integer(0, 100)).executes(c -> {
                            int index = getInteger(c, "index");
                            if (index >= 0 && index < PLUGIN_CONFIG.stressTest.testChests.size()) {
                                var removed = PLUGIN_CONFIG.stressTest.testChests.remove(index);
                                c.getSource().getEmbed()
                                        .title("Chest Removed")
                                        .description("Removed chest at: " + removed);
                            } else {
                                c.getSource().getEmbed()
                                        .title("Invalid Index");
                                return ERROR;
                            }
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            int count = PLUGIN_CONFIG.stressTest.testChests.size();
                            PLUGIN_CONFIG.stressTest.testChests.clear();
                            c.getSource().getEmbed()
                                    .title("Chests Cleared")
                                    .description("Cleared " + count + " chests");
                        }))
                        .then(literal("list").executes(c -> {
                            if (PLUGIN_CONFIG.stressTest.testChests.isEmpty()) {
                                c.getSource().getEmbed()
                                        .title("Test Chests")
                                        .description("No test chests configured.");
                            } else {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < PLUGIN_CONFIG.stressTest.testChests.size(); i++) {
                                    sb.append(i).append(": ").append(PLUGIN_CONFIG.stressTest.testChests.get(i)).append("\n");
                                }
                                c.getSource().getEmbed()
                                        .title("Test Chests")
                                        .description(sb.toString());
                            }
                        }))
                )
                .then(literal("repeat").then(argument("count", integer(1, 100)).executes(c -> {
                    PLUGIN_CONFIG.stressTest.repeatCount = getInteger(c, "count");
                    c.getSource().getEmbed()
                            .title("Repeat Count Set")
                            .description("Set repeat count to: " + PLUGIN_CONFIG.stressTest.repeatCount);
                })))
                .then(literal("delays").then(argument("d1", integer(0, 100)).executes(c -> {
                    PLUGIN_CONFIG.stressTest.delayValues = new int[]{getInteger(c, "d1")};
                    c.getSource().getEmbed()
                            .title("Delays Set")
                            .description("Delays: " + java.util.Arrays.toString(PLUGIN_CONFIG.stressTest.delayValues));
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
                .addField("Stress Test", toggleStr(PLUGIN_CONFIG.stressTest.enabled))
                .addField("Test Chests", String.valueOf(PLUGIN_CONFIG.stressTest.testChests.size()))
                .addField("Repeat Count", String.valueOf(PLUGIN_CONFIG.stressTest.repeatCount))
                .addField("Delay Values", java.util.Arrays.toString(PLUGIN_CONFIG.stressTest.delayValues))
                .primaryColor();
    }
}
