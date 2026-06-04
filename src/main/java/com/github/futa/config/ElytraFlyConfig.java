package com.github.futa.config;

public class ElytraFlyConfig {

    public boolean enabled = false;

    /**
     * elytraFly 模块的调试模式。
     * 启用时，会输出一些调试信息，如玩家高度、pitch 等。
     */
    public boolean debug = false;

    public boolean elytraUnbreak = true;

    public boolean antiKick = true;

    /**
     * pitch40 下边界高度。
     * 当玩家高度低于此值时，停止俯冲并开始抬头。
     */
    public double pitch40LowerBounds = 80;
    public double period = 16;

    /**
     * pitch40 上边界高度。
     * 当玩家高度高于此值时，停止抬头并开始俯冲。
     */
    public double pitch40UpperBounds = 120;

    /**
     * pitch 旋转速度（度/tick）。
     * 每 tick pitch 变化的角度。
     */
    public double pitch40RotationSpeed = 4;

    /**
     * 是否阻止进入未加载的区块。
     * 启用时，如果目标位置的区块未加载，则停止水平移动。
     */
    public boolean noUnloadedChunks = true;

    /**
     * 上下边界之间的间距。
     * 用于重连时或在自动调整边界时确定高度范围。
     */
    public double boundGap = 60;
}
