package com.github.futa.config;

public class ElytraUnbreakConfig {
    public boolean enabled = false;

    /**
     * 鞘翅装备/卸下的切换周期（tick数）。
     * 值越小切换越频繁，耐久消耗越少，但网络开销越大。
     */
    public int period = 16;

    /**
     * 在无法继续滑翔时发送跳跃包防止被踢。
     */
    public boolean antiKick = true;
}