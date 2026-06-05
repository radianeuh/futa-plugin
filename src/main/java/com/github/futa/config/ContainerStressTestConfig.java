package com.github.futa.config;

import com.zenith.mc.block.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class ContainerStressTestConfig {
    public boolean enabled = false;
    /**
     * 测试箱子位置列表
     */
    public List<BlockPos> testChests = new ArrayList<>();
    /**
     * 每项测试重复次数
     */
    public int repeatCount = 10;
    /**
     * 测试的 actionDelayTicks 值列表
     */
    public int[] delayValues = {0, 1, 2, 3, 5, 10};
}
