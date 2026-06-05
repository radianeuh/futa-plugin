package com.github.futa.command;

import com.github.futa.module.BaseFinder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class BaseFinderCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("basefinder")
                .category(CommandCategory.MODULE)
                .description("""
                        BaseFinder - 基地检测模块
                        
                        自动扫描渲染距离内的区块，检测潜在的玩家基地。
                        支持检测：
                        - 传送门 (Portal)
                        - 潜影盒 (Shulker)
                        - 物品展示框 (Item Frame)
                        - 末影珍珠 (Ender Pearl)
                        - 命名牌实体 (NameTagged Entity)
                        - 村民 (Leveled Villager)
                        - 船 (Boat)
                        - 自定义方块列表
                        """)
                .usageLines(
                        "on/off",
                        "portalFinder on/off",
                        "shulkerFinder on/off",
                        "itemFrameFinder on/off",
                        "enderPearlFinder on/off",
                        "nameTagFinder on/off",
                        "villagerFinder on/off",
                        "boatFinder on/off",
                        "blockListEnabled on/off",
                        "blockList add <blockName>",
                        "blockList del <index>",
                        "blockList clear",
                        "blockList list",
                        "blockListThreshold <threshold>",
                        "scanIntervalTicks <ticks>",
                        "saveToFile on/off",
                        "loadOnStart on/off",
                        "displayCoords on/off",
                        "info"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("basefinder")
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.enabled = getToggle(c, "toggle");
                    MODULE.get(BaseFinder.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("BaseFinder " + toggleStrCaps(PLUGIN_CONFIG.baseFinder.enabled))
                            .primaryColor();
                    return OK;
                }))
                .then(literal("portalFinder").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.portalFinder = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Portal Finder " + toggleStrCaps(PLUGIN_CONFIG.baseFinder.portalFinder))
                            .primaryColor();
                    return OK;
                })))
                .then(literal("shulkerFinder").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.shulkerFinder = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Shulker Finder " + toggleStrCaps(PLUGIN_CONFIG.baseFinder.shulkerFinder))
                            .primaryColor();
                    return OK;
                })))
                .then(literal("itemFrameFinder").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.itemFrameFinder = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Item Frame Finder " + toggleStrCaps(PLUGIN_CONFIG.baseFinder.itemFrameFinder))
                            .primaryColor();
                    return OK;
                })))
                .then(literal("enderPearlFinder").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.enderPearlFinder = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Ender Pearl Finder " + toggleStrCaps(PLUGIN_CONFIG.baseFinder.enderPearlFinder))
                            .primaryColor();
                    return OK;
                })))
                .then(literal("nameTagFinder").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.nameTagFinder = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("NameTag Finder " + toggleStrCaps(PLUGIN_CONFIG.baseFinder.nameTagFinder))
                            .primaryColor();
                    return OK;
                })))
                .then(literal("villagerFinder").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.villagerFinder = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Villager Finder " + toggleStrCaps(PLUGIN_CONFIG.baseFinder.villagerFinder))
                            .primaryColor();
                    return OK;
                })))
                .then(literal("boatFinder").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.boatFinder = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Boat Finder " + toggleStrCaps(PLUGIN_CONFIG.baseFinder.boatFinder))
                            .primaryColor();
                    return OK;
                })))
                .then(literal("blockListEnabled").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.blockListEnabled = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Block List Finder " + toggleStrCaps(PLUGIN_CONFIG.baseFinder.blockListEnabled))
                            .primaryColor();
                    return OK;
                })))
                .then(literal("blockList")
                        .then(literal("add").then(argument("blockName", string()).executes(c -> {
                            String blockName = getString(c, "blockName");
                            if (!PLUGIN_CONFIG.baseFinder.blockList.contains(blockName)) {
                                PLUGIN_CONFIG.baseFinder.blockList.add(blockName);
                                c.getSource().getEmbed()
                                        .title("Block Added")
                                        .description("Added block: " + blockName);
                            } else {
                                c.getSource().getEmbed()
                                        .title("Block Already Exists")
                                        .description(blockName + " is already in the list");
                            }
                            return OK;
                        })))
                        .then(literal("del").then(argument("index", integer(0, 100)).executes(c -> {
                            int index = getInteger(c, "index");
                            if (index >= 0 && index < PLUGIN_CONFIG.baseFinder.blockList.size()) {
                                String removed = PLUGIN_CONFIG.baseFinder.blockList.remove(index);
                                c.getSource().getEmbed()
                                        .title("Block Removed")
                                        .description("Removed block: " + removed);
                            } else {
                                c.getSource().getEmbed()
                                        .title("Invalid Index")
                                        .description("Index must be between 0 and " + (PLUGIN_CONFIG.baseFinder.blockList.size() - 1));
                                return ERROR;
                            }
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            int count = PLUGIN_CONFIG.baseFinder.blockList.size();
                            PLUGIN_CONFIG.baseFinder.blockList.clear();
                            c.getSource().getEmbed()
                                    .title("Block List Cleared")
                                    .description("Cleared " + count + " blocks");
                            return OK;
                        }))
                        .then(literal("list").executes(c -> {
                            if (PLUGIN_CONFIG.baseFinder.blockList.isEmpty()) {
                                c.getSource().getEmbed()
                                        .title("Block List")
                                        .description("No blocks configured.");
                            } else {
                                StringBuilder sb = new StringBuilder();
                                sb.append("Configured blocks:\n\n");
                                for (int i = 0; i < PLUGIN_CONFIG.baseFinder.blockList.size(); i++) {
                                    sb.append("**").append(i).append("**: ").append(PLUGIN_CONFIG.baseFinder.blockList.get(i)).append("\n");
                                }
                                c.getSource().getEmbed()
                                        .title("Block List")
                                        .description(sb.toString());
                            }
                            return OK;
                        }))
                )
                .then(literal("blockListThreshold").then(argument("threshold", integer(1, 100)).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.blockListThreshold = getInteger(c, "threshold");
                    c.getSource().getEmbed()
                            .title("Block List Threshold Set")
                            .description("Set threshold to: " + PLUGIN_CONFIG.baseFinder.blockListThreshold + " blocks per chunk");
                    return OK;
                })))
                .then(literal("scanIntervalTicks").then(argument("ticks", integer(20, 600)).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.scanIntervalTicks = getInteger(c, "ticks");
                    c.getSource().getEmbed()
                            .title("Scan Interval Set")
                            .description("Set scan interval to: " + PLUGIN_CONFIG.baseFinder.scanIntervalTicks + " ticks (" +
                                    (PLUGIN_CONFIG.baseFinder.scanIntervalTicks / 20.0) + " seconds)");
                    return OK;
                })))
                .then(literal("saveToFile").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.saveToFile = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Save To File " + toggleStrCaps(PLUGIN_CONFIG.baseFinder.saveToFile))
                            .primaryColor();
                    return OK;
                })))
                .then(literal("loadOnStart").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.loadOnStart = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Load On Start " + toggleStrCaps(PLUGIN_CONFIG.baseFinder.loadOnStart))
                            .primaryColor();
                    return OK;
                })))
                .then(literal("displayCoords").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.baseFinder.displayCoords = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Display Coords " + toggleStrCaps(PLUGIN_CONFIG.baseFinder.displayCoords))
                            .primaryColor();
                    return OK;
                })))
                .then(literal("info").executes(c -> {
                    BaseFinder module = MODULE.get(BaseFinder.class);
                    java.util.List<String> locations = module.getDetectedLocations();

                    if (locations.isEmpty()) {
                        c.getSource().getEmbed()
                                .title("BaseFinder 信息")
                                .description("暂无检测到的坐标")
                                .primaryColor();
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append("已检测到 ").append(locations.size()).append(" 个位置:\n\n");
                        for (int i = 0; i < Math.min(locations.size(), 20); i++) {
                            sb.append("**").append(i + 1).append("**: ").append(locations.get(i)).append("\n");
                        }
                        if (locations.size() > 20) {
                            sb.append("\n... 还有 ").append(locations.size() - 20).append(" 条记录");
                        }
                        c.getSource().getEmbed()
                                .title("BaseFinder 检测结果")
                                .description(sb.toString())
                                .primaryColor();
                    }
                    return OK;
                }));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
                .addField("BaseFinder", toggleStr(PLUGIN_CONFIG.baseFinder.enabled))
                .addField("Portal Finder", toggleStr(PLUGIN_CONFIG.baseFinder.portalFinder))
                .addField("Shulker Finder", toggleStr(PLUGIN_CONFIG.baseFinder.shulkerFinder))
                .addField("Item Frame Finder", toggleStr(PLUGIN_CONFIG.baseFinder.itemFrameFinder))
                .addField("Ender Pearl Finder", toggleStr(PLUGIN_CONFIG.baseFinder.enderPearlFinder))
                .addField("NameTag Finder", toggleStr(PLUGIN_CONFIG.baseFinder.nameTagFinder))
                .addField("Villager Finder", toggleStr(PLUGIN_CONFIG.baseFinder.villagerFinder))
                .addField("Boat Finder", toggleStr(PLUGIN_CONFIG.baseFinder.boatFinder))
                .addField("Block List Enabled", toggleStr(PLUGIN_CONFIG.baseFinder.blockListEnabled))
                .addField("Block List", PLUGIN_CONFIG.baseFinder.blockList.isEmpty() ?
                        "[None]" : String.join(", ", PLUGIN_CONFIG.baseFinder.blockList))
                .addField("Block List Threshold", PLUGIN_CONFIG.baseFinder.blockListThreshold + " blocks")
                .addField("Scan Interval", PLUGIN_CONFIG.baseFinder.scanIntervalTicks + " ticks (" +
                        (PLUGIN_CONFIG.baseFinder.scanIntervalTicks / 20.0) + "s)")
                .addField("Save To File", toggleStr(PLUGIN_CONFIG.baseFinder.saveToFile))
                .addField("Load On Start", toggleStr(PLUGIN_CONFIG.baseFinder.loadOnStart))
                .addField("Display Coords", toggleStr(PLUGIN_CONFIG.baseFinder.displayCoords))
                .primaryColor();
    }
}
