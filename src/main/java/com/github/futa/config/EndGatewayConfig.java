package com.github.futa.config;

import com.zenith.mc.block.BlockPos;

public class EndGatewayConfig {
    public boolean enabled = false;
    public BlockPos gatewayPosition = BlockPos.ZERO;
    public int detectionRadius = 50;
    public boolean autoEnableInOverworld = true;
    public int pathfindingTimeoutSeconds = 30;
}
