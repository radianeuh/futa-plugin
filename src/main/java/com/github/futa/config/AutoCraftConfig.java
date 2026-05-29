package com.github.futa.config;

import com.google.common.collect.Lists;
import com.zenith.mc.block.BlockPos;

import java.util.List;

public class AutoCraftConfig {
    public boolean enabled = false;
    public BlockPos resultChest = BlockPos.ZERO;
    public BlockPos workbench = BlockPos.ZERO;
    public List<BlockPos> sourceChests = Lists.newArrayList();
    public boolean allowHandCrafting = true;
    public int retryAttempts = 3;
    public long delayBetweenActions = 40L;
    public int actionDelayTick = 1;
    public int batchSize = 64;
    public int maxDistanceFromWorkbench = 4;
    public long restSecend = 1;
    public String recipe = "GOLDEN_APPLE";
}
