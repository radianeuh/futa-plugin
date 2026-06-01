package com.github.futa.config;

public class AutoTurtleFeedConfig {
    public boolean enabled = false;
    public double maxDistance = 3.5; // 最大交互距离
    public int feedCooldownMinutes = 10; // 喂食冷却时间（分钟）
    public int feedIntervalTick = 1; // 每一只喂食间隔
    public boolean debugMode = false;
}
