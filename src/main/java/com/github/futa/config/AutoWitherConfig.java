package com.github.futa.config;

import com.zenith.mc.block.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class AutoWitherConfig {
    public boolean enabled = false;
    public boolean debugMode = false;
    public int actionDelay = 2; // 每次放置后的延迟（ticks）
    public int maxWithers = 6; // 最大同时存在的凋零数量
    public int checkInterval = 5; // 检查凋零数量的间隔（ticks）

    // 放置凋灵的坐标列表
    public List<BlockPos> witherPositions = new ArrayList<>();


    // 灵魂沙箱子位置
    public BlockPos soulSandChest = BlockPos.ZERO;

    // 最少保留的灵魂沙数量
    public int minSoulSand = 24;

    public void init() {
        // 初始化默认配置
        if (witherPositions.isEmpty()) {
            // 添加一些默认位置示例
            witherPositions.add(new BlockPos(0, 100, 0));
            witherPositions.add(new BlockPos(5, 100, 0));
            witherPositions.add(new BlockPos(10, 100, 0));
        }
    }
}
