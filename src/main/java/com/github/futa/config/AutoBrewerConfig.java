package com.github.futa.config;

import com.google.common.collect.Lists;
import com.zenith.mc.block.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class AutoBrewerConfig {
    public boolean enabled = false;
    public BlockPos brewingStand = BlockPos.ZERO;
    public List<BlockPos> sourceChests = Lists.newArrayList();
    public BlockPos resultChest = BlockPos.ZERO;
    public List<BrewRecipe> recipes = new ArrayList<>();
    public long delayBetweenActions = 5L;
    public int actionDelayTick = 1;
    public int brewWaitTicks = 420; // 酿造等待tick数（~21秒，略大于400tick以确保完成）
    public boolean debugMode = false;

    public static class BrewRecipe {
        public String name;
        public List<String> ingredients; // 有序的材料名列表

        public BrewRecipe() {}

        public BrewRecipe(String name, List<String> ingredients) {
            this.name = name;
            this.ingredients = ingredients;
        }
    }
}
