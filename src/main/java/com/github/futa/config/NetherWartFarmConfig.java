package com.github.futa.config;

import com.zenith.mc.block.BlockPos;

public class NetherWartFarmConfig {
    public boolean enabled = false;
    public boolean debugMode = false;
    public int searchRadius = 32;
    public int searchYRange = 2;
    public int actionDelay = 3;
    public int restDuration = 60;

    public boolean preferredPlanting = true;
    public boolean avoidOther = false;

    // 种植箱子位置
    public BlockPos storageChest = BlockPos.ZERO;

    public void init() {
        // 初始化默认配置
    }
}
