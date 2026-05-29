package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.AutoChestManagerConfig;
import com.github.futa.dto.ChestLocation;
import com.github.futa.module.AutoChestManagerModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.command.brigadier.BlockPosArgument;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoChestManagerCommand extends Command {
    private final AutoChestManagerConfig config = FutaPlugin.PLUGIN_CONFIG.autoChest;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autochest")
                .category(CommandCategory.MODULE)
                .description("自动箱子管理模块控制命令")
                .usageLines(
                        "on/off",
                        "setTrash x y z - 设置垃圾桶坐标",
                        "setShulker - 设置潜影盒坐标(需要的物品)",
                        "addItem <物品ID> - 添加需要的物品",
                        "removeItem <物品ID> - 移除需要的物品",
                        "addTrash <物品ID> - 添加垃圾物品",
                        "removeTrash <物品ID> - 移除垃圾物品",
                        "clearItems - 清空所有需要的物品",
                        "clearTrashs - 清空所有垃圾物品",
                        "setInterval <秒数> - 设置处理间隔时间",
                        "addChest x y z - 添加要处理的箱子坐标",
                        "removeChest x y z - 移除要处理的箱子坐标",
                        "listChests - 列出所有要处理的箱子",
                        "clearChests - 清空所有要处理的箱子",
                        "listItems - 列出所有需要的物品",
                        "status - 查看当前配置状态"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autochest")
                .then(argument("toggle", toggle()).executes(c -> {
                    config.enabled = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("autochest " + toggleStrCaps(config.enabled));
                    MODULE.get(AutoChestManagerModule.class).syncEnabledFromConfig();
                }))
                .then(literal("setTrash").then(argument("pos", blockPos()).executes(c -> {
                    var pos = BlockPosArgument.getBlockPos(c, "pos");
                    int x = pos.x();
                    int y = pos.y();
                    int z = pos.z();

                    config.trashX = pos.x();
                    config.trashY = pos.y();
                    config.trashZ = pos.z();
                    c.getSource().getEmbed().title("设置成功 " + x + ", " + y + ", " + z)
                            .successColor();
                    return OK;
                })))

                .then(literal("setShulker").then(argument("pos", blockPos()).executes(c -> {
                    var pos = BlockPosArgument.getBlockPos(c, "pos");
                    int x = pos.x();
                    int y = pos.y();
                    int z = pos.z();

                    config.shulkerX = x;
                    config.shulkerY = y;
                    config.shulkerZ = z;
                    c.getSource().getEmbed().title("设置成功 ").addField("已设置位置为潜影盒: ", x + ", " + y + ", " + z);
                    return OK;
                })))
                .then(literal("addItem")
                        .then(argument("item", wordWithChars())
                                .executes(c -> {
                                    String item = getString(c, "item");
                                    config.wantedItems.add(item);
                                    c.getSource().getEmbed().title("添加成功: " + item).successColor();
                                    return OK;
                                })
                        )
                )
                .then(literal("addTrash")
                        .then(argument("item", wordWithChars())
                                .executes(c -> {
                                    String item = getString(c, "item");
                                    config.trashItems.add(item);
                                    c.getSource().getEmbed().title("添加成功: " + item).successColor();
                                    return OK;
                                })
                        )
                )
                .then(literal("removeItem")
                        .then(argument("item", wordWithChars())
                                .executes(c -> {
                                    String item = getString(c, "item");
                                    config.wantedItems.remove(item);
                                    c.getSource().getEmbed().title("移除成功：" + item).successColor();
                                    return OK;
                                })
                        )
                )
                .then(literal("removeTrash")
                        .then(argument("item", wordWithChars())
                                .executes(c -> {
                                    String item = getString(c, "item");
                                    config.trashItems.remove(item);
                                    c.getSource().getEmbed().title("移除成功：" + item).successColor();
                                    return OK;
                                })
                        )
                )
                .then(literal("clearItems").executes(c -> {
                    int clearedCount = config.wantedItems.size();
                    config.wantedItems.clear();
                    c.getSource().getEmbed()
                            .title("清空成功")
                            .description("已清空 " + clearedCount + " 个需要的物品")
                            .successColor();
                    return OK;
                }))
                .then(literal("clearTrashs").executes(c -> {
                    int clearedCount = config.trashItems.size();
                    config.trashItems.clear();
                    c.getSource().getEmbed()
                            .title("清空成功")
                            .description("已清空 " + clearedCount + " 个垃圾物品")
                            .successColor();
                    return OK;
                }))
                .then(literal("setInterval")
                        .then(argument("seconds", integer(1, 3600))
                                .executes(c -> {
                                    int seconds = getInteger(c, "seconds");
                                    config.interval = seconds;
                                    c.getSource().getEmbed()
                                            .title("设置成功")
                                            .description("处理间隔已设置为 " + seconds + " 秒")
                                            .successColor();
                                    return OK;
                                })
                        )
                )
                .then(literal("addChest")
                        .then(argument("pos", blockPos())
                                .executes(c -> {
                                    var pos = BlockPosArgument.getBlockPos(c, "pos");
                                    ChestLocation chestLocation = new ChestLocation(pos.x(), pos.y(), pos.z());

                                    if (config.chestLocations.contains(chestLocation)) {
                                        c.getSource().getEmbed()
                                                .errorColor()
                                                .title("添加失败")
                                                .description("箱子坐标已存在: " + chestLocation.toString());
                                    } else {
                                        config.chestLocations.add(chestLocation);
                                        c.getSource().getEmbed()
                                                .successColor()
                                                .title("添加成功")
                                                .description("已添加箱子坐标: " + chestLocation.toString());
                                    }
                                    return OK;
                                })
                        )
                )
                .then(literal("removeChest")
                        .then(argument("pos", blockPos())
                                .executes(c -> {
                                    var pos = BlockPosArgument.getBlockPos(c, "pos");
                                    ChestLocation chestLocation = new ChestLocation(pos.x(), pos.y(), pos.z());

                                    if (config.chestLocations.remove(chestLocation)) {
                                        c.getSource().getEmbed()
                                                .successColor()
                                                .title("移除成功")
                                                .description("已移除箱子坐标: " + chestLocation.toString());
                                    } else {
                                        c.getSource().getEmbed()
                                                .errorColor()
                                                .title("移除失败")
                                                .description("箱子坐标不存在: " + chestLocation.toString());
                                    }
                                    return OK;
                                })
                        )
                )
                .then(literal("listChests").executes(c -> {
                    var embed = c.getSource().getEmbed().title("要处理的箱子列表");
                    if (config.chestLocations.isEmpty()) {
                        embed.description("没有配置要处理的箱子");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < config.chestLocations.size(); i++) {
                            ChestLocation chest = config.chestLocations.get(i);
                            sb.append("**").append(i + 1).append(".** ").append(chest.toString());
                            if (chest.isDoubleChest) {
                                sb.append(" (大箱子)");
                            }
                            if (i < config.chestLocations.size() - 1) {
                                sb.append("\n");
                            }
                        }
                        embed.description(sb.toString());
                    }
                    return OK;
                }))
                .then(literal("clearChests").executes(c -> {
                    int clearedCount = config.chestLocations.size();
                    config.chestLocations.clear();
                    c.getSource().getEmbed()
                            .title("清空成功")
                            .description("已清空 " + clearedCount + " 个要处理的箱子")
                            .successColor();
                    return OK;
                }))
                .then(literal("listItems").executes(c -> {
                    var embed = c.getSource().getEmbed().title("需要的物品列表");
                    if (config.wantedItems.isEmpty()) {
                        embed.description("没有配置需要的物品");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (String item : config.wantedItems) {
                            sb.append("- ").append(item).append("\n");
                        }
                        embed.description(sb.toString());
                    }
                    return OK;
                }))
                .then(literal("status").executes(c -> {
                    var embed = c.getSource().getEmbed().title("自动箱子管理状态");
                    embed.addField("垃圾桶坐标", config.trashX + ", " + config.trashY + ", " + config.trashZ, true);
                    embed.addField("潜影盒坐标", config.shulkerX + ", " + config.shulkerY + ", " + config.shulkerZ, true);
                    embed.addField("需要物品数量", String.valueOf(config.wantedItems.size()), true);
                    embed.addField("处理间隔", config.interval + " 秒", true);
                    embed.addField("要处理的箱子数量", String.valueOf(config.chestLocations.size()), true);
                    return OK;
                }));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.primaryColor()
                .addField("垃圾桶坐标", config.trashX + ", " + config.trashY + ", " + config.trashZ)
                .addField("潜影盒坐标", config.shulkerX + ", " + config.shulkerY + ", " + config.shulkerZ)
                .addField("需要物品数量", String.valueOf(config.wantedItems.size()))
                .addField("处理间隔", config.interval + " 秒")
                .addField("要处理的箱子数量", String.valueOf(config.chestLocations.size()));
    }
}
