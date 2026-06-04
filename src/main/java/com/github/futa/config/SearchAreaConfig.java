package com.github.futa.config;

public class SearchAreaConfig {

    public boolean enabled = false;

    /**
     * 调试模式
     */
    public boolean debug = false;

    /**
     * 搜索模式：Rectangle 或 Spiral
     */
    public String mode = "Rectangle";

    /**
     * 矩形起始点 X 坐标（仅矩形模式）
     */
    public int startX = 0;

    /**
     * 矩形起始点 Y 坐标（仅矩形模式）
     */
    public int startY = 64;

    /**
     * 矩形起始点 Z 坐标（仅矩形模式）
     */
    public int startZ = 0;

    /**
     * 矩形终点 X 坐标（仅矩形模式）
     */
    public int endX = 500;

    /**
     * 矩形终点 Y 坐标（仅矩形模式）
     */
    public int endY = 64;

    /**
     * 矩形终点 Z 坐标（仅矩形模式）
     */
    public int endZ = 500;

    /**
     * 路径间隔（chunk 数量，默认12）
     * 实际间隔 = pathGap * 16 格
     */
    public int pathGap = 12;

    /**
     * 保存数据的文件夹名
     */
    public String saveName = "save";

    /**
     * 完成后断开连接（仅矩形模式）
     */
    public boolean disconnectOnCompletion = false;

    /**
     * 到达起始点的判定距离（格）
     */
    public double reachStartDistance = 5.0;

    /**
     * 完成路径的判定距离（格）
     */
    public double completeDistance = 20.0;
}
