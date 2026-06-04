package com.github.futa.config;

public class ElytraFlyConfig {

    public boolean enabled = false;

    /**
     * elytraFly 模块的调试模式。
     * 启用时，会输出一些调试信息，如玩家高度、pitch 等。
     */
    public boolean debug = false;

    public int debugLogPeriod = 2;

    /**
     * pitch40 下边界高度。
     * 当玩家高度低于此值时，停止俯冲并开始抬头。
     */
    public double pitch40LowerBounds = 80;

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

    /**
     * 目标 X 坐标，到达附近后自动下线。
     */
    public double targetX = 0;

    /**
     * 目标 Z 坐标，到达附近后自动下线。
     */
    public double targetZ = 0;

    /**
     * 是否启用到达坐标附近自动下线功能。
     */
    public boolean disconnectOnReach = false;

    /**
     * 到达目标坐标的判定距离（格）。
     * 当玩家与目标坐标的距离小于此值时触发下线。
     */
    public double disconnectDistance = 2000;

    /**
     * 低于此 Y 坐标自动下线。
     * 默认 0 表示禁用此功能。
     */
    public double disconnectOnLowY = 100;
}
