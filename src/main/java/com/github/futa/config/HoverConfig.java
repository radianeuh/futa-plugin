package com.github.futa.config;

public class HoverConfig {
    public boolean enabled = false;

    /**
     * 悬停高度偏移（相对于当前位置）。
     * 正值向上，负值向下。
     */
    public double heightOffset = 0;

    /**
     * 是否启用抗重力（通过周期性跳跃抵消重力）。
     */
    public boolean antiGravity = true;

    /**
     * 跳跃间隔（tick数）。
     * 值越小跳跃越频繁，悬停越稳定，但网络开销越大。
     */
    public int jumpInterval = 2;
}