package com.github.futa.config;

import com.google.common.collect.Lists;

import java.util.List;

public class AutoCrystalConfig {
    public boolean enabled = false;

    // 目标选择配置
    public int targetRange = 15; // 目标选择范围
    public int placeRange = 6;   // 水晶放置范围
    public double minHealth = 5.0;  // 最小目标生命值
    public double maxHealth = 20.0; // 最大目标生命值

    // 操作配置
    public int delayTicks = 10; // 水晶爆炸延迟（游戏刻）
    public boolean safeMode = true; // 安全模式（避免自爆）
    public int minCrystalDistance = 3; // 水晶与玩家的最小距离

    // 高级配置
    public boolean autoSwitch = true; // 自动切换到水晶
    public boolean checkObstacles = true; // 检查障碍物
    public boolean prioritizeArmor = true; // 优先攻击高护甲目标
    public int maxCrystalsActive = 5; // 最大同时激活水晶数

    // 黑名单和白名单
    public List<String> blacklistedPlayers = Lists.newArrayList(); // 玩家黑名单
    public List<String> whitelistedPlayers = Lists.newArrayList(); // 玩家白名单（优先）

    // 调试选项
    public boolean debugMode = false;

    // 构造器
    public AutoCrystalConfig() {
        // 默认配置
        this.blacklistedPlayers.add("friend1"); // 示例：不攻击的朋友
    }

    // 验证配置
    public boolean isValid() {
        return targetRange > 0 &&
                placeRange > 0 &&
                placeRange <= targetRange &&
                delayTicks > 0 &&
                minHealth >= 0 &&
                maxHealth > minHealth;
    }

    // 重置为默认配置
    public void resetToDefaults() {
        this.enabled = false;
        this.targetRange = 15;
        this.placeRange = 6;
        this.minHealth = 5.0;
        this.maxHealth = 20.0;
        this.delayTicks = 10;
        this.safeMode = true;
        this.minCrystalDistance = 3;
        this.autoSwitch = true;
        this.checkObstacles = true;
        this.prioritizeArmor = true;
        this.maxCrystalsActive = 5;
        this.debugMode = false;
        this.blacklistedPlayers.clear();
        this.blacklistedPlayers.add("friend1");
        this.whitelistedPlayers.clear();
    }
}
