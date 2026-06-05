package com.github.futa.config;

import java.util.List;

public class BaseFinderConfig {
    public boolean enabled = false;

    // 方块检测器开关
    public boolean portalFinder = true;
    public boolean shulkerFinder = true;
    public boolean beaconFinder = true;

    // 实体检测器开关
    public boolean itemFrameFinder = true;
    public boolean enderPearlFinder = true;
    public boolean nameTagFinder = true;
    public boolean villagerFinder = true;
    public boolean boatFinder = true;
    public int entityScanDelay = 20;

    // 自定义方块列表
    public boolean blockListEnabled = true;
    public List<String> blockList = List.of(
            "minecraft:crafter",
            "minecraft:ender_chest",
            "minecraft:beacon",
            "minecraft:redstone_block",
            "minecraft:slime_block",
            "minecraft:diamond_block"
    );
    public int blockListThreshold = 4;

    // 通用设置
    public boolean displayCoords = true;
    public int tickDelay = 5;

    // 数据持久化
    public boolean saveToFile = true;
    public boolean loadOnStart = true;

    // 扫描设置
    public int scanIntervalTicks = 20;
}
