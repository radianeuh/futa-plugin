package com.github.futa.config;

import com.zenith.mc.block.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class EnchantBookSorterConfig {
    public boolean enabled = false;
    public boolean debugMode = false;
    public int delayBetweenActions = 10;
    public int actionDelayTick = 3;
    public int restDuration = 60; // 完成后休息时间（tick）

    // 源箱子列表（从这些箱子取出附魔书）
    public List<BlockPos> sourceChests = new ArrayList<>();

    // 附魔类型对应的专用箱子
    public Map<String, BlockPos> enchantmentChests = new HashMap<>();

    // 杂物箱列表（存放没有专用箱子的附魔书）
    public List<BlockPos> miscChests = new ArrayList<>();

    public void init() {
        // 初始化默认配置
        if (enchantmentChests.isEmpty()) {
            // 示例：为常见附魔类型配置专用箱子
            enchantmentChests.put("sharpness", BlockPos.ZERO);
            enchantmentChests.put("protection", BlockPos.ZERO);
            enchantmentChests.put("efficiency", BlockPos.ZERO);
            enchantmentChests.put("unbreaking", BlockPos.ZERO);
            enchantmentChests.put("mending", BlockPos.ZERO);
            // ... 其他附魔类型
    }
}
}
