package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.ItemSorterConfig;
import com.github.futa.dto.ChestLocation;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.mc.block.BlockPos;

import static com.github.futa.FutaPlugin.log;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;

public class ItemSorterCommand extends Command {

    private final ItemSorterConfig config = FutaPlugin.PLUGIN_CONFIG.itemSorter;


    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("itemsorter")
                .category(CommandCategory.MODULE)
                .description("物品分类模块管理命令")
                .usageLines(
                        "enable",
                        "disable",
                        "status",
                        "addchest <x> <y> <z>",
                        "removechest <index>",
                        "listchests",
                        "addcategory <category> <item1> [item2] ...",
                        "removecategory <category>",
                        "listcategories",
                        "cachestats",
                        "clearcache",
                        "help"
                )
                .aliases("is", "sorter")
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("itemsorter")
                .executes(c -> {
                    printHelp();
                    return OK;
                })
                .then(literal("enable").executes(c -> {
                    config.enabled = true;
                    c.getSource().getEmbed()
                            .title("物品分类模块已启用")
                            .primaryColor();
                    return OK;
                }))
                .then(literal("disable").executes(c -> {
                    config.enabled = false;
                    c.getSource().getEmbed()
                            .title("物品分类模块已禁用")
                            .primaryColor();
                    return OK;
                }))
                .then(literal("status").executes(c -> {
                    c.getSource().getEmbed()
                            .title("物品分类模块状态")
                            .addField("启用状态", config.enabled ? "已启用" : "已禁用", true)
                            .addField("箱子搜索半径", String.valueOf(config.chestSearchRadius), true)
                            .addField("处理间隔", config.processingIntervalTicks + " ticks", true)
                            .addField("源箱子数量", String.valueOf(config.chestLocations.size()), true)
                            .addField("自定义分类数量", String.valueOf(config.customCategories.size()), true)
                            .addField("智能分类", config.enableSmartClassification ? "已启用" : "已禁用", true)
                            .addField("处理创造物品", config.processCreativeItems ? "已启用" : "已禁用", true)
                            .addField("缺省分类", "物品自己作为分类名", true)
                            .primaryColor();
                    return OK;
                }))
                .then(literal("addchest").then(argument("pos", blockPos()).executes(c -> {
                    BlockPos pos = getBlockPos(c, "pos");

                    ChestLocation chest = new ChestLocation(pos.x(), pos.y(), pos.z());
                    config.chestLocations.add(chest);
                    c.getSource().getEmbed()
                            .title("已添加源箱子坐标")
                            .primaryColor();
                })))
                .then(literal("cachestats").executes(c -> {
                    // 这里需要访问模块实例来获取缓存统计
                    c.getSource().getEmbed()
                            .title("箱子缓存统计")
                            .description("使用 'itemsorter clearcache' 清空缓存")
                            .primaryColor();
                    return OK;
                }))
                .then(literal("clearcache").executes(c -> {
                    config.chestCache.clear();
                    c.getSource().getEmbed()
                            .title("箱子缓存已清空")
                            .primaryColor();
                    return OK;
                }))
                .then(literal("help").executes(c -> {
                    printHelp();
                    return OK;
                }));
    }


    private void printHelp() {
        log.info("物品分类模块命令帮助:");
        log.info("使用 'help itemsorter' 查看详细用法");
    }

}
