package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.futa.FutaPlugin;
import com.github.futa.config.SearchAreaConfig;
import com.github.rfresh2.EventConsumer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zenith.Proxy;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.player.Bot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;

/**
 * SearchAreaModule 模块 - 自动区块加载/搜索模块
 * <p>
 * 用于配合 ElytraFlyModule，通过预定义路径自动遍历区域加载区块。
 * <p>
 * 支持两种搜索模式：
 * 1. Rectangle - 矩形锯齿形遍历
 * 2. Spiral - 螺旋扩展遍历
 */
public class SearchAreaModule extends BaseModule {

    SearchAreaConfig config = FutaPlugin.PLUGIN_CONFIG.searchArea;

    private static final long AUTO_SAVE_INTERVAL_MS = 10 * 60 * 1000; // 10 分钟
    private long lastSaveTime = 0;

    private PathingData pathingData;
    private SearchAreaMode searchMode;

    // ==================== 枚举定义 ====================

    /**
     * 搜索模式枚举
     */
    public enum SearchAreaModes {
        Rectangle,
        Spiral
    }

    // ==================== 路径数据类 ====================

    /**
     * 路径数据基类
     */
    public static abstract class PathingData {
        public int[] initialPos = new int[3];
        public int[] currPos = new int[3];
        public double yawDirection = -90.0;
        public boolean mainPath = true;
    }

    /**
     * 矩形模式路径数据
     */
    public static class PathingDataRectangle extends PathingData {
        public int[] targetPos = new int[3];
        public int lastCompleteRowZ = 0;
    }

    /**
     * 螺旋模式路径数据
     */
    public static class PathingDataSpiral extends PathingData {
        public int spiralWidth = 192;
        public int spiralHeight = 192;
    }

    // ==================== 模式基类 ====================

    /**
     * 搜索模式基类
     */
    public static abstract class SearchAreaMode {
        protected SearchAreaModule module;
        protected PathingData data;
        protected SearchAreaConfig config;

        public SearchAreaMode(SearchAreaModule module, PathingData data, SearchAreaConfig config) {
            this.module = module;
            this.data = data;
            this.config = config;
        }

        /**
         * 每 tick 执行的逻辑
         */
        public abstract void onTick(EntityPlayer player);

        /**
         * 检查路径是否完成
         */
        public abstract boolean isComplete(EntityPlayer player);

        /**
         * 保存路径数据到 JSON
         */
        public abstract void saveData(String savePath);

        /**
         * 加载路径数据
         */
        protected String getModeName() {
            return "Unknown";
        }
    }

    // ==================== 矩形模式 ====================

    /**
     * 矩形锯齿形遍历模式
     */
    public static class Rectangle extends SearchAreaMode {

        public Rectangle(SearchAreaModule module, PathingDataRectangle data, SearchAreaConfig config) {
            super(module, data, config);
        }

        @Override
        public void onTick(EntityPlayer player) {
            PathingDataRectangle rectData = (PathingDataRectangle) data;

            // 到达起始点逻辑
            if (module.goingToStart) {
                double dx = player.getX() - data.currPos[0];
                double dz = player.getZ() - data.currPos[2];
                double distance = Math.sqrt(dx * dx + dz * dz);

                if (distance < config.reachStartDistance) {
                    module.goingToStart = false;
                    module.info("已到达起始点，开始搜索");
                    module.info("预估完成时间将在搜索过程中计算");
                } else {
                    // 朝起始点转向
                    PLUGIN_CONFIG.elytraFly.targetX = data.currPos[0];
                    PLUGIN_CONFIG.elytraFly.targetZ = data.currPos[2];
                }
                return;
            }

            // 主路径逻辑
            if (data.mainPath) {
                // 水平移动（X 方向）
                handleHorizontalMovement(player, rectData);
            } else {
                // 垂直移动（Z 方向）
                handleVerticalMovement(player, rectData);
            }
        }

        private void handleHorizontalMovement(EntityPlayer player, PathingDataRectangle rectData) {
            // 确定 X 方向
            double targetX;
            if (data.yawDirection == -90.0) {
                // 向 +X
                targetX = rectData.targetPos[0];
            } else {
                // 向 -X
                targetX = rectData.initialPos[0];
            }

            // 检查是否到达 X 边界
            double dx = player.getX() - targetX;
            if (Math.abs(dx) < 5) {
                // 到达边界，切换到垂直移动
                data.mainPath = false;

                // 记录当前 Z 作为完成行
                rectData.lastCompleteRowZ = (int) player.getZ();

                // 计算下一个 Z 位置
                int nextZ;
                if (data.yawDirection == -90.0) {
                    // 向 +X 完成后，向 +Z 移动
                    nextZ = (int) player.getZ() + config.pathGap * 16;
                } else {
                    // 向 -X 完成后，向 -Z 移动
                    nextZ = (int) player.getZ() - config.pathGap * 16;
                }

                // 设置新的 currPos
                data.currPos[0] = (int) player.getX();
                data.currPos[2] = nextZ;

                // 切换 X 方向
                data.yawDirection = (data.yawDirection == -90.0) ? 90.0 : -90.0;
            } else {
                // 继续水平移动
                PLUGIN_CONFIG.elytraFly.targetX = targetX;
                PLUGIN_CONFIG.elytraFly.targetZ = (int) player.getZ();
            }
        }

        private void handleVerticalMovement(EntityPlayer player, PathingDataRectangle rectData) {
            // 检查是否到达 Z 目标
            double dz = player.getZ() - data.currPos[2];
            if (Math.abs(dz) < 5) {
                // 到达目标 Z，切换回主路径
                data.mainPath = true;
            } else {
                // 继续垂直移动

                PLUGIN_CONFIG.elytraFly.targetX = (int) player.getX();
                PLUGIN_CONFIG.elytraFly.targetZ = data.currPos[2];

            }
        }

        @Override
        public boolean isComplete(EntityPlayer player) {
            PathingDataRectangle rectData = (PathingDataRectangle) data;
            double dx = player.getX() - rectData.targetPos[0];
            double dz = player.getZ() - rectData.targetPos[2];
            double distance = Math.sqrt(dx * dx + dz * dz);
            return distance < config.completeDistance;
        }

        @Override
        protected String getModeName() {
            return "Rectangle";
        }

        @Override
        public void saveData(String savePath) {
            try {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(data);
                Files.createDirectories(Path.of(savePath));
                Files.writeString(Path.of(savePath, getModeName() + ".json"), json);
            } catch (IOException e) {
                module.error("保存矩形路径数据失败: " + e.getMessage());
            }
        }
    }

    // ==================== 螺旋模式 ====================

    /**
     * 螺旋扩展遍历模式
     */
    public static class Spiral extends SearchAreaMode {

        public Spiral(SearchAreaModule module, PathingDataSpiral data, SearchAreaConfig config) {
            super(module, data, config);
        }

        @Override
        public void onTick(EntityPlayer player) {
            PathingDataSpiral spiralData = (PathingDataSpiral) data;

            if (data.mainPath) {
                // 水平移动（X 方向）
                handleHorizontalMovement(player, spiralData);
            } else {
                // 垂直移动（Z 方向）
                handleVerticalMovement(player, spiralData);
            }
        }

        private void handleHorizontalMovement(EntityPlayer player, PathingDataSpiral spiralData) {
            // 确定 X 方向和目标
            double targetX;
            if (data.yawDirection == -90.0) {
                // 向 +X
                targetX = data.currPos[0] + spiralData.spiralWidth;
            } else {
                // 向 -X
                targetX = data.currPos[0] - spiralData.spiralWidth;
            }

            // 检查是否到达 X 边界
            double dx = player.getX() - targetX;
            if (Math.abs(dx) < 5) {
                // 到达边界，切换到垂直移动
                data.mainPath = false;

                // 更新 currPos
                data.currPos[0] = (int) targetX;

                // 切换 X 方向
                data.yawDirection = (data.yawDirection == -90.0) ? 90.0 : -90.0;

                // 更新螺旋宽度（下次水平移动的距离）
                spiralData.spiralWidth += config.pathGap * 16;
            } else {
                // 继续水平移动
                PLUGIN_CONFIG.elytraFly.targetX = targetX;
                PLUGIN_CONFIG.elytraFly.targetZ = (int) player.getZ();
            }
        }

        private void handleVerticalMovement(EntityPlayer player, PathingDataSpiral spiralData) {
            // 确定 Z 方向和目标
            double targetZ;
            if (data.yawDirection == 90.0) {
                // 向 +Z（注意：这里的方向是接上一步水平移动后的方向）
                targetZ = data.currPos[2] + spiralData.spiralHeight;
            } else {
                // 向 -Z
                targetZ = data.currPos[2] - spiralData.spiralHeight;
            }

            // 检查是否到达 Z 边界
            double dz = player.getZ() - targetZ;
            if (Math.abs(dz) < 5) {
                // 到达边界，切换回水平移动
                data.mainPath = true;

                // 更新 currPos
                data.currPos[2] = (int) targetZ;

                // 切换方向，为下一轮水平移动做准备
                // 垂直移动完成后，切换方向以开始新的螺旋圈
                data.yawDirection = (data.yawDirection == 90.0) ? -90.0 : 90.0;

                // 更新螺旋高度（下次垂直移动的距离）
                spiralData.spiralHeight += config.pathGap * 16;
            } else {
                // 继续垂直移动
                PLUGIN_CONFIG.elytraFly.targetX = (int) player.getX();
                PLUGIN_CONFIG.elytraFly.targetZ = targetZ;
            }
        }

        @Override
        public boolean isComplete(EntityPlayer player) {
            // 螺旋模式永不完成，除非手动停止
            return false;
        }

        @Override
        protected String getModeName() {
            return "Spiral";
        }

        @Override
        public void saveData(String savePath) {
            try {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(data);
                Files.createDirectories(Path.of(savePath));
                Files.writeString(Path.of(savePath, getModeName() + ".json"), json);
            } catch (IOException e) {
                module.error("保存螺旋路径数据失败: " + e.getMessage());
            }
        }
    }

    // ==================== 模块核心逻辑 ====================

    boolean goingToStart = false;

    @Override
    public boolean enabledSetting() {
        return config.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, Bot.POST_TICK_PRIORITY - 500, this::onTick),
                of(ClientBotTick.Starting.class, this::onTickStart)
        );
    }

    private void onTickStart(ClientBotTick.Starting starting) {
        if (AutoLoginModule.isIn3cSpawn()) {
            return;
        }

        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) {
            return;
        }

        // 加载或创建路径数据
        loadOrCreatePathData();

        // 重置状态
        goingToStart = true;
        lastSaveTime = System.currentTimeMillis();

        info("SearchAreaModule 已启动，模式: " + config.mode);
    }

    private void onTick(ClientBotTick event) {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) return;
        if (AutoLoginModule.isIn3cSpawn()) {
            return;
        }

        if (searchMode == null) {
            return;
        }

        // 自动保存检查（每10分钟）
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSaveTime > AUTO_SAVE_INTERVAL_MS) {
            savePathData();
            lastSaveTime = currentTime;
        }

        if (PLUGIN_CONFIG.elytraFly.disconnectOnReach == true) {
            PLUGIN_CONFIG.elytraFly.disconnectOnReach = false;
        }
        // 执行模式逻辑
        searchMode.onTick(player);

        // 检查是否完成（仅矩形模式）
        if (searchMode.isComplete(player)) {
            info("搜索路径已完成");
            if (config.disconnectOnCompletion) {
                info("正在断开连接...");
                Proxy.getInstance().disconnect();
            }
        }
    }

    @Override
    public void onDisable() {
        savePathData();
        // 清理 ElytraFly 的目标坐标，防止关闭后残留导致转圈
        PLUGIN_CONFIG.elytraFly.targetX = 0;
        PLUGIN_CONFIG.elytraFly.targetZ = 0;
        info("SearchAreaModule 已停用，进度已保存");
    }

    /**
     * 加载或创建路径数据
     */
    private void loadOrCreatePathData() {
        String savePath = getSavePath();
        Path filePath = Path.of(savePath, config.mode + ".json");

        if (Files.exists(filePath)) {
            // 加载已有数据
            try {
                String json = Files.readString(filePath);
                Gson gson = new Gson();

                if (config.mode.equals("Rectangle")) {
                    pathingData = gson.fromJson(json, PathingDataRectangle.class);
                    searchMode = new Rectangle(this, (PathingDataRectangle) pathingData, config);
                } else if (config.mode.equals("Spiral")) {
                    pathingData = gson.fromJson(json, PathingDataSpiral.class);
                    searchMode = new Spiral(this, (PathingDataSpiral) pathingData, config);
                }

                info("已加载保存的路径数据");
                return;
            } catch (Exception e) {
                error("加载路径数据失败: " + e.getMessage());
            }
        }

        // 创建新数据
        createNewPathData();
    }

    /**
     * 创建新的路径数据
     */
    public void createNewPathData() {
        var player = CACHE.getPlayerCache().getThePlayer();

        if (config.mode.equals("Rectangle")) {
            PathingDataRectangle rectData = new PathingDataRectangle();
            rectData.initialPos = new int[]{config.startX, config.startY, config.startZ};
            rectData.currPos = new int[]{(int) player.getX(), (int) player.getY(), (int) player.getZ()};
            rectData.targetPos = new int[]{config.endX, config.endY, config.endZ};
            rectData.yawDirection = -90.0; // 初始向 +X
            rectData.mainPath = true;
            rectData.lastCompleteRowZ = (int) player.getZ();

            pathingData = rectData;
            searchMode = new Rectangle(this, rectData, config);

        } else if (config.mode.equals("Spiral")) {
            PathingDataSpiral spiralData = new PathingDataSpiral();
            spiralData.initialPos = new int[]{(int) player.getX(), (int) player.getY(), (int) player.getZ()};
            spiralData.currPos = new int[]{(int) player.getX(), (int) player.getY(), (int) player.getZ()};
            spiralData.yawDirection = -90.0;
            spiralData.mainPath = true;
            spiralData.spiralWidth = config.pathGap * 16;
            spiralData.spiralHeight = config.pathGap * 16;

            pathingData = spiralData;
            searchMode = new Spiral(this, spiralData, config);
        }

        info("已创建新的路径数据");
    }

    /**
     * 保存路径数据
     */
    private void savePathData() {
        if (searchMode != null && pathingData != null) {
            searchMode.saveData(getSavePath());
        }
    }

    /**
     * 获取保存路径
     */
    private String getSavePath() {
        return "search-area/" + config.saveName;
    }
}
