package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.futa.config.ContainerStressTestConfig;
import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.actions.ShiftClick;
import com.zenith.mc.block.BlockPos;
import com.zenith.util.RequestFuture;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.INVENTORY;

/**
 * 容器压力测试模块
 * <p>
 * 测试内容：
 * 1. 打开箱子的 tick 延迟
 * 2. 从箱子提取物品的 tick 延迟
 * 3. 不同 actionDelayTicks 下的成功率
 * 4. 多个箱子的测试对比
 */
public class ContainerStressTestModule extends BaseModule {
    public static final int PRIORITY = 9000;
    private static final int OPEN_TIMEOUT = 40;   // 打开超时 tick
    private static final int EXTRACT_TIMEOUT = 60; // 提取超时 tick
    private static final int CLOSE_TIMEOUT = 20;   // 关闭超时 tick

    private final ContainerStressTestConfig config = PLUGIN_CONFIG.stressTest;

    private State state = State.IDLE;
    private int tickCounter = 0;

    // 当前测试进度
    private int currentChestIndex = 0;
    private int currentDelayIndex = 0;
    private int currentRepeat = 0;

    // 当前测试的 delay 值
    private int currentDelay = 0;

    // 当前箱子坐标
    private BlockPos currentChest = BlockPos.ZERO;

    // 操作 futures
    private RequestFuture inventoryActionFuture = RequestFuture.rejected;

    // ========== 数据收集 ==========
    // delay值 → 每次打开耗时列表
    private final Map<Integer, List<Long>> openTicksByDelay = new HashMap<>();
    // delay值 → 每次提取耗时列表
    private final Map<Integer, List<Long>> extractTicksByDelay = new HashMap<>();
    // delay值 → [成功数, 总数]
    private final Map<Integer, int[]> openSuccessByDelay = new HashMap<>();
    private final Map<Integer, int[]> extractSuccessByDelay = new HashMap<>();

    // 单次测试的临时数据
    private long testStartTick = 0;
    private boolean chestOpened = false;

    @Override
    public boolean enabledSetting() {
        return config.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::onTick),
                of(ClientBotTick.Stopped.class, e -> reset())
        );
    }

    @Override
    public void onEnable() {
        reset();
        if (config.testChests.isEmpty()) {
            warn("未配置测试箱子，请使用 /stresschest chest add <x> <y> <z> 添加");
            return;
        }
        info("容器压力测试已启动，共 {} 个箱子，{} 组 delay，每组 {} 次重复",
                config.testChests.size(), config.delayValues.length, config.repeatCount);
        startNextTest();
    }

    @Override
    public void onDisable() {
        if (state != State.IDLE && state != State.REPORT) {
            printReport();
        }
        reset();
    }

    private void reset() {
        state = State.IDLE;
        tickCounter = 0;
        currentChestIndex = 0;
        currentDelayIndex = 0;
        currentRepeat = 0;
        currentDelay = 0;
        currentChest = BlockPos.ZERO;
        inventoryActionFuture = RequestFuture.rejected;
        openTicksByDelay.clear();
        extractTicksByDelay.clear();
        openSuccessByDelay.clear();
        extractSuccessByDelay.clear();
    }

    // ==================== 测试流程控制 ====================

    private void startNextTest() {
        if (currentDelayIndex >= config.delayValues.length) {
            // 所有 delay 测试完成
            if (currentChestIndex < config.testChests.size() - 1) {
                // 切换到下一个箱子
                currentChestIndex++;
                currentDelayIndex = 0;
                currentRepeat = 0;
                info("=== 切换到箱子 {} / {} ===", currentChestIndex + 1, config.testChests.size());
                startNextTest();
            } else {
                // 全部测试完成
                info("所有测试完成，生成报告...");
                state = State.REPORT;
            }
            return;
        }

        currentChest = config.testChests.get(currentChestIndex);
        currentDelay = config.delayValues[currentDelayIndex];
        currentRepeat = 0;

        // 初始化数据结构
        openTicksByDelay.computeIfAbsent(currentDelay, k -> new ArrayList<>());
        extractTicksByDelay.computeIfAbsent(currentDelay, k -> new ArrayList<>());
        openSuccessByDelay.computeIfAbsent(currentDelay, k -> new int[2]);
        extractSuccessByDelay.computeIfAbsent(currentDelay, k -> new int[2]);

        info("=== 开始测试: 箱子 ({},{},{}) delay={} 重复 {}/{} ===",
                currentChest.x(), currentChest.y(), currentChest.z(),
                currentDelay, currentRepeat + 1, config.repeatCount);

        beginOpenTest();
    }

    private void beginOpenTest() {
        // 关闭可能打开的容器
        var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer != null && openContainer.getContainerId() != 0) {
            List<InventoryAction> actions = new ArrayList<>();
            actions.add(new CloseContainer(openContainer.getContainerId()));
            inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actionDelayTicks(0)
                    .actions(actions)
                    .priority(PRIORITY)
                    .build());
            state = State.AWAIT_CLOSE_BEFORE_OPEN;
            return;
        }

        state = State.OPEN_CHEST;
        tickCounter = 0;

        // 发送右键打开包
        sendClientPacketAsync(new ServerboundUseItemOnPacket(
                currentChest.x(), currentChest.y(), currentChest.z(),
                Direction.UP,
                Hand.MAIN_HAND,
                0, 0, 0,
                false, false, 0
        ));

        chestOpened = false;
        testStartTick = tickCounter;

        state = State.AWAIT_OPEN;
    }

    private void beginExtractTest() {
        tickCounter = 0;
        testStartTick = tickCounter;

        var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer == null || openContainer.getContainerId() == 0) {
            // 箱子没打开，记录失败
            recordExtractResult(false);
            beginCloseTest();
            return;
        }

        int containerId = openContainer.getContainerId();

        // 找到箱子里第一个有物品的槽位
        int targetSlot = -1;
        for (int i = 0; i < openContainer.getSize() - 36; i++) {
            if (openContainer.getItemStack(i) != Container.EMPTY_STACK) {
                targetSlot = i;
                break;
            }
        }

        if (targetSlot == -1) {
            // 箱子是空的，跳过提取测试
            recordExtractResult(false);
            beginCloseTest();
            return;
        }

        // 提取物品：ShiftClick 该槽位，然后关闭容器
        List<InventoryAction> actions = new ArrayList<>();
        actions.add(new ShiftClick(containerId, targetSlot, ShiftClickItemAction.LEFT_CLICK));
        actions.add(new CloseContainer(containerId));

        inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                .owner(this)
                .actionDelayTicks(currentDelay)
                .actions(actions)
                .priority(PRIORITY)
                .build());

        state = State.AWAIT_EXTRACT;
    }

    private void beginCloseTest() {
        tickCounter = 0;
        testStartTick = tickCounter;

        var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer == null || openContainer.getContainerId() == 0) {
            // 已经关闭了
            recordCloseResult(true);
            finishSingleTest();
            return;
        }

        List<InventoryAction> actions = new ArrayList<>();
        actions.add(new CloseContainer(openContainer.getContainerId()));
        inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                .owner(this)
                .actionDelayTicks(currentDelay)
                .actions(actions)
                .priority(PRIORITY)
                .build());

        state = State.AWAIT_CLOSE;
    }

    private void finishSingleTest() {
        currentRepeat++;

        if (currentRepeat >= config.repeatCount) {
            // 当前 delay 测试完成，输出当前 delay 的汇总
            long avgOpen = avg(openTicksByDelay.get(currentDelay));
            long avgExtract = avg(extractTicksByDelay.get(currentDelay));
            int[] openSuccess = openSuccessByDelay.get(currentDelay);
            int[] extractSuccess = extractSuccessByDelay.get(currentDelay);
            info("[delay={}] 平均打开: {} tick, 成功: {}/{} ({}) | 平均提取: {} tick, 成功: {}/{} ({})",
                    currentDelay,
                    avgOpen, openSuccess[0], openSuccess[1], percent(openSuccess),
                    avgExtract, extractSuccess[0], extractSuccess[1], percent(extractSuccess));

            currentDelayIndex++;
            startNextTest();
        } else {
            // 继续重复测试
            info("重复 {}/{} 完成，继续...", currentRepeat, config.repeatCount);
            beginOpenTest();
        }
    }

    // ==================== 数据记录 ====================

    private void recordOpenResult(boolean success) {
        long elapsed = tickCounter - testStartTick;
        openTicksByDelay.get(currentDelay).add(elapsed);
        openSuccessByDelay.get(currentDelay)[1]++;
        if (success) {
            openSuccessByDelay.get(currentDelay)[0]++;
        }
    }

    private void recordExtractResult(boolean success) {
        long elapsed = tickCounter - testStartTick;
        extractTicksByDelay.get(currentDelay).add(elapsed);
        extractSuccessByDelay.get(currentDelay)[1]++;
        if (success) {
            extractSuccessByDelay.get(currentDelay)[0]++;
        }
    }

    private void recordCloseResult(boolean success) {
        // 关闭结果不单独统计，仅用于流程控制
    }

    // ==================== Tick 处理 ====================

    private void onTick(ClientBotTick event) {
        if (state == State.IDLE || state == State.REPORT) return;

        tickCounter++;

        switch (state) {
            case AWAIT_CLOSE_BEFORE_OPEN -> {
                if (inventoryActionFuture.isCompleted()) {
                    beginOpenTest();
                } else if (tickCounter > CLOSE_TIMEOUT) {
                    // 强制进入打开流程
                    beginOpenTest();
                }
            }
            case AWAIT_OPEN -> {
                var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                if (openContainer.getContainerId() != 0) {
                    chestOpened = true;
                    recordOpenResult(true);
                    beginExtractTest();
                } else if (tickCounter > OPEN_TIMEOUT) {
                    recordOpenResult(false);
                    beginCloseTest();
                }
            }
            case AWAIT_EXTRACT -> {
                if (inventoryActionFuture.isCompleted()) {
                    recordExtractResult(true);
                    finishSingleTest();
                } else if (tickCounter > EXTRACT_TIMEOUT) {
                    recordExtractResult(false);
                    beginCloseTest();
                }
            }
            case AWAIT_CLOSE -> {
                if (inventoryActionFuture.isCompleted()) {
                    recordCloseResult(true);
                    finishSingleTest();
                } else if (tickCounter > CLOSE_TIMEOUT) {
                    recordCloseResult(false);
                    finishSingleTest();
                }
            }
            case REPORT -> {
                printReport();
                state = State.IDLE;
                config.enabled = false;
                info("压力测试已完成并自动关闭");
            }
            default -> {
            }
        }
    }

    // ==================== 报告 ====================

    private void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 容器压力测试报告 ==========\n");
        sb.append(String.format("箱子数量: %d\n", config.testChests.size()));
        sb.append(String.format("每组重复: %d 次\n", config.repeatCount));
        sb.append(String.format("测试 delay 值: %s\n\n", delayValuesStr()));

        sb.append("--- 各 delay 结果汇总 ---\n");
        sb.append(String.format("%-8s | %-15s | %-12s | %-15s | %-12s\n",
                "Delay", "Avg Open(tick)", "Open Rate", "Avg Extract(tick)", "Extract Rate"));
        sb.append("-".repeat(70)).append("\n");

        int bestDelay = -1;
        double bestScore = -1;

        for (int delay : config.delayValues) {
            List<Long> openTicks = openTicksByDelay.get(delay);
            List<Long> extractTicks = extractTicksByDelay.get(delay);
            int[] openSuccess = openSuccessByDelay.get(delay);
            int[] extractSuccess = extractSuccessByDelay.get(delay);

            if (openSuccess == null || extractSuccess == null) continue;

            long avgOpen = avg(openTicks);
            long avgExtract = avg(extractTicks);
            String openRate = percent(openSuccess);
            String extractRate = percent(extractSuccess);

            sb.append(String.format("%-8d | %-15d | %-12s | %-15d | %-12s\n",
                    delay, avgOpen, openRate, avgExtract, extractRate));

            // 评分：成功率权重更高
            double score = (openSuccess[1] > 0 ? (double) openSuccess[0] / openSuccess[1] : 0) * 50
                    + (extractSuccess[1] > 0 ? (double) extractSuccess[0] / extractSuccess[1] : 0) * 50
                    - avgOpen * 0.5   // 打开越快越好
                    - avgExtract * 0.3; // 提取越快越好
            if (score > bestScore) {
                bestScore = score;
                bestDelay = delay;
            }
        }

        sb.append("\n--- 结论 ---\n");
        if (bestDelay >= 0) {
            sb.append(String.format("推荐 actionDelayTicks: %d (综合评分最高)\n", bestDelay));
        }

        // 打开延迟分析
        long minOpen = Long.MAX_VALUE, maxOpen = 0;
        for (int delay : config.delayValues) {
            List<Long> ticks = openTicksByDelay.get(delay);
            if (ticks != null) {
                for (long t : ticks) {
                    minOpen = Math.min(minOpen, t);
                    maxOpen = Math.max(maxOpen, t);
                }
            }
        }
        if (minOpen < Long.MAX_VALUE) {
            sb.append(String.format("打开延迟范围: %d ~ %d tick\n", minOpen, maxOpen));
        }

        // 提取延迟分析
        long minExtract = Long.MAX_VALUE, maxExtract = 0;
        for (int delay : config.delayValues) {
            List<Long> ticks = extractTicksByDelay.get(delay);
            if (ticks != null) {
                for (long t : ticks) {
                    minExtract = Math.min(minExtract, t);
                    maxExtract = Math.max(maxExtract, t);
                }
            }
        }
        if (minExtract < Long.MAX_VALUE) {
            sb.append(String.format("提取延迟范围: %d ~ %d tick\n", minExtract, maxExtract));
        }

        sb.append("======================================\n");

        String report = sb.toString();
        info("{}", report);
    }

    // ==================== 工具方法 ====================

    private long avg(List<Long> list) {
        if (list == null || list.isEmpty()) return 0;
        long sum = 0;
        for (long v : list) sum += v;
        return sum / list.size();
    }

    private String percent(int[] success) {
        if (success[1] == 0) return "N/A";
        return String.format("%.0f%%", (double) success[0] / success[1] * 100);
    }

    private String delayValuesStr() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < config.delayValues.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(config.delayValues[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    // ==================== 状态枚举 ====================

    public enum State {
        IDLE,
        AWAIT_CLOSE_BEFORE_OPEN,
        OPEN_CHEST,
        AWAIT_OPEN,
        EXTRACT_ITEM,
        AWAIT_EXTRACT,
        CLOSE_CHEST,
        AWAIT_CLOSE,
        NEXT_TEST,
        REPORT
    }
}
