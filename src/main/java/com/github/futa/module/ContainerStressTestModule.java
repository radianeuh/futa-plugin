package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.futa.config.ContainerStressTestConfig;
import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.mc.block.BlockPos;
import com.zenith.util.RequestFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

/**
 * 容器压力测试模块
 * <p>
 * 测试内容：
 * 1. 打开箱子的 tick 延迟
 * 2. 存货（deposit）的 tick 延迟 — 使用 InventoryActionMacros.deposit
 * 3. 取货（withdraw）的 tick 延迟 — 使用 InventoryActionMacros.withdraw
 * 4. 不同 actionDelayTicks 下的成功率
 * 5. 多个箱子的测试对比
 */
public class ContainerStressTestModule extends BaseModule {
    public static final int PRIORITY = 9000;
    private static final int OPEN_TIMEOUT = 40;
    private static final int ACTION_TIMEOUT = 80;
    private static final int CLOSE_TIMEOUT = 20;

    private final ContainerStressTestConfig config = PLUGIN_CONFIG.stressTest;

    private State state = State.IDLE;
    private int tickCounter = 0;

    // 当前测试进度
    private int currentChestIndex = 0;
    private int currentDelayIndex = 0;
    private int currentRepeat = 0;
    private int currentDelay = 0;
    private BlockPos currentChest = BlockPos.ZERO;

    // 操作 futures
    private RequestFuture inventoryActionFuture = RequestFuture.rejected;

    // ========== 数据收集 ==========
    private final Map<Integer, List<Long>> openTicksByDelay = new HashMap<>();
    private final Map<Integer, List<Long>> depositTicksByDelay = new HashMap<>();
    private final Map<Integer, List<Long>> withdrawTicksByDelay = new HashMap<>();
    private final Map<Integer, int[]> openSuccessByDelay = new HashMap<>();
    private final Map<Integer, int[]> depositSuccessByDelay = new HashMap<>();
    private final Map<Integer, int[]> withdrawSuccessByDelay = new HashMap<>();

    // 单次测试的临时数据
    private long testStartTick = 0;

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
        depositTicksByDelay.clear();
        withdrawTicksByDelay.clear();
        openSuccessByDelay.clear();
        depositSuccessByDelay.clear();
        withdrawSuccessByDelay.clear();
    }

    // ==================== 测试流程控制 ====================

    private void startNextTest() {
        if (currentDelayIndex >= config.delayValues.length) {
            if (currentChestIndex < config.testChests.size() - 1) {
                currentChestIndex++;
                currentDelayIndex = 0;
                currentRepeat = 0;
                info("=== 切换到箱子 {} / {} ===", currentChestIndex + 1, config.testChests.size());
                startNextTest();
            } else {
                info("所有测试完成，生成报告...");
                state = State.REPORT;
            }
            return;
        }

        currentChest = config.testChests.get(currentChestIndex);
        currentDelay = config.delayValues[currentDelayIndex];
        currentRepeat = 0;

        openTicksByDelay.computeIfAbsent(currentDelay, k -> new ArrayList<>());
        depositTicksByDelay.computeIfAbsent(currentDelay, k -> new ArrayList<>());
        withdrawTicksByDelay.computeIfAbsent(currentDelay, k -> new ArrayList<>());
        openSuccessByDelay.computeIfAbsent(currentDelay, k -> new int[2]);
        depositSuccessByDelay.computeIfAbsent(currentDelay, k -> new int[2]);
        withdrawSuccessByDelay.computeIfAbsent(currentDelay, k -> new int[2]);

        info("=== 开始测试: 箱子 ({},{},{}) delay={} 重复 {}/{} ===",
                currentChest.x(), currentChest.y(), currentChest.z(),
                currentDelay, currentRepeat + 1, config.repeatCount);

        beginOpenForDeposit();
    }

    /**
     * 第一轮：打开箱子，准备存货
     */
    private void beginOpenForDeposit() {
        closeIfOpen();
        state = State.AWAIT_CLOSE_FOR_DEPOSIT_OPEN;
    }

    /**
     * 第二轮：打开箱子，准备取货
     */
    private void beginOpenForWithdraw() {
        closeIfOpen();
        state = State.AWAIT_CLOSE_FOR_WITHDRAW_OPEN;
    }

    private void closeIfOpen() {
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
        }
    }

    private void doOpenChest() {
        tickCounter = 0;
        testStartTick = tickCounter;
        BARITONE.rightClickBlock(currentChest.x(), currentChest.y(), currentChest.z());
        state = State.AWAIT_OPEN;
    }

    private void doDeposit() {
        tickCounter = 0;
        testStartTick = tickCounter;

        var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer == null || openContainer.getContainerId() == 0) {
            recordDepositResult(false);
            doCloseForDeposit();
            return;
        }

        int containerId = openContainer.getContainerId();
        List<InventoryAction> actions = InventoryActionMacros.deposit(containerId);

        if (actions.isEmpty()) {
            // 玩家背包没有物品可存，跳过deposit测试
            recordDepositResult(true);
            doCloseForDeposit();
            return;
        }

        actions.add(new CloseContainer(containerId));
        inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                .owner(this)
                .actionDelayTicks(currentDelay)
                .actions(actions)
                .priority(PRIORITY)
                .build());
        state = State.AWAIT_DEPOSIT;
    }

    private void doCloseForDeposit() {
        tickCounter = 0;
        testStartTick = tickCounter;

        var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer == null || openContainer.getContainerId() == 0) {
            // 已关闭，直接进入第二轮
            beginOpenForWithdraw();
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
        state = State.AWAIT_CLOSE_FOR_DEPOSIT;
    }

    private void doWithdraw() {
        tickCounter = 0;
        testStartTick = tickCounter;

        var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer == null || openContainer.getContainerId() == 0) {
            recordWithdrawResult(false);
            doCloseForWithdraw();
            return;
        }

        int containerId = openContainer.getContainerId();
        List<InventoryAction> actions = InventoryActionMacros.withdraw(containerId);

        if (actions.isEmpty()) {
            // 箱子是空的，跳过withdraw测试
            recordWithdrawResult(true);
            doCloseForWithdraw();
            return;
        }

        actions.add(new CloseContainer(containerId));
        inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                .owner(this)
                .actionDelayTicks(currentDelay)
                .actions(actions)
                .priority(PRIORITY)
                .build());
        state = State.AWAIT_WITHDRAW;
    }

    private void doCloseForWithdraw() {
        tickCounter = 0;
        testStartTick = tickCounter;

        var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer == null || openContainer.getContainerId() == 0) {
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
        state = State.AWAIT_CLOSE_FOR_WITHDRAW;
    }

    private void finishSingleTest() {
        currentRepeat++;

        if (currentRepeat >= config.repeatCount) {
            long avgOpen = avg(openTicksByDelay.get(currentDelay));
            long avgDeposit = avg(depositTicksByDelay.get(currentDelay));
            long avgWithdraw = avg(withdrawTicksByDelay.get(currentDelay));
            int[] openSuccess = openSuccessByDelay.get(currentDelay);
            int[] depositSuccess = depositSuccessByDelay.get(currentDelay);
            int[] withdrawSuccess = withdrawSuccessByDelay.get(currentDelay);
            info("[delay={}] 打开: {} tick ({}) | 存货: {} tick ({}) | 取货: {} tick ({})",
                    currentDelay,
                    avgOpen, percent(openSuccess),
                    avgDeposit, percent(depositSuccess),
                    avgWithdraw, percent(withdrawSuccess));

            currentDelayIndex++;
            startNextTest();
        } else {
            info("重复 {}/{} 完成，继续...", currentRepeat, config.repeatCount);
            beginOpenForDeposit();
        }
    }

    // ==================== 数据记录 ====================

    private void recordOpenResult(boolean success) {
        long elapsed = tickCounter - testStartTick;
        openTicksByDelay.get(currentDelay).add(elapsed);
        openSuccessByDelay.get(currentDelay)[1]++;
        if (success) openSuccessByDelay.get(currentDelay)[0]++;
    }

    private void recordDepositResult(boolean success) {
        long elapsed = tickCounter - testStartTick;
        depositTicksByDelay.get(currentDelay).add(elapsed);
        depositSuccessByDelay.get(currentDelay)[1]++;
        if (success) depositSuccessByDelay.get(currentDelay)[0]++;
    }

    private void recordWithdrawResult(boolean success) {
        long elapsed = tickCounter - testStartTick;
        withdrawTicksByDelay.get(currentDelay).add(elapsed);
        withdrawSuccessByDelay.get(currentDelay)[1]++;
        if (success) withdrawSuccessByDelay.get(currentDelay)[0]++;
    }

    // ==================== Tick 处理 ====================

    private void onTick(ClientBotTick event) {
        if (state == State.IDLE || state == State.REPORT) return;

        tickCounter++;

        switch (state) {
            // ---- 第一轮：存货 ----
            case AWAIT_CLOSE_FOR_DEPOSIT_OPEN -> {
                if (inventoryActionFuture.isCompleted() || tickCounter > CLOSE_TIMEOUT) {
                    doOpenChest();
                }
            }
            case AWAIT_OPEN -> {
                var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                if (openContainer.getContainerId() != 0) {
                    recordOpenResult(true);
                    doDeposit();
                } else if (tickCounter > OPEN_TIMEOUT) {
                    recordOpenResult(false);
                    state = State.IDLE;
                    currentRepeat++;
                    if (currentRepeat < config.repeatCount) beginOpenForDeposit();
                    else finishSingleTest();
                }
            }
            case AWAIT_DEPOSIT -> {
                if (inventoryActionFuture.isCompleted()) {
                    recordDepositResult(true);
                    doCloseForDeposit();
                } else if (tickCounter > ACTION_TIMEOUT) {
                    recordDepositResult(false);
                    doCloseForDeposit();
                }
            }
            case AWAIT_CLOSE_FOR_DEPOSIT -> {
                if (inventoryActionFuture.isCompleted() || tickCounter > CLOSE_TIMEOUT) {
                    // 第一轮完成，开始第二轮取货
                    beginOpenForWithdraw();
                }
            }

            // ---- 第二轮：取货 ----
            case AWAIT_CLOSE_FOR_WITHDRAW_OPEN -> {
                if (inventoryActionFuture.isCompleted() || tickCounter > CLOSE_TIMEOUT) {
                    doOpenChest();
                }
            }
            case AWAIT_WITHDRAW -> {
                if (inventoryActionFuture.isCompleted()) {
                    recordWithdrawResult(true);
                    doCloseForWithdraw();
                } else if (tickCounter > ACTION_TIMEOUT) {
                    recordWithdrawResult(false);
                    doCloseForWithdraw();
                }
            }
            case AWAIT_CLOSE_FOR_WITHDRAW -> {
                if (inventoryActionFuture.isCompleted() || tickCounter > CLOSE_TIMEOUT) {
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
        sb.append(String.format("%-8s | %-12s | %-10s | %-12s | %-10s | %-12s | %-10s\n",
                "Delay", "Avg Open", "Open%", "Avg Deposit", "Depo%", "Avg Withdraw", "With%"));
        sb.append("-".repeat(90)).append("\n");

        int bestDelay = -1;
        double bestScore = -1;

        for (int delay : config.delayValues) {
            List<Long> openTicks = openTicksByDelay.get(delay);
            List<Long> depositTicks = depositTicksByDelay.get(delay);
            List<Long> withdrawTicks = withdrawTicksByDelay.get(delay);
            int[] openSuccess = openSuccessByDelay.get(delay);
            int[] depositSuccess = depositSuccessByDelay.get(delay);
            int[] withdrawSuccess = withdrawSuccessByDelay.get(delay);

            if (openSuccess == null) continue;

            long avgOpen = avg(openTicks);
            long avgDeposit = avg(depositTicks);
            long avgWithdraw = avg(withdrawTicks);

            sb.append(String.format("%-8d | %-12d | %-10s | %-12d | %-10s | %-12d | %-10s\n",
                    delay, avgOpen, percent(openSuccess),
                    avgDeposit, percent(depositSuccess),
                    avgWithdraw, percent(withdrawSuccess)));

            double score = 0;
            int weightSum = 0;
            if (openSuccess[1] > 0) {
                score += (double) openSuccess[0] / openSuccess[1] * 34;
                weightSum += 34;
            }
            if (depositSuccess[1] > 0) {
                score += (double) depositSuccess[0] / depositSuccess[1] * 33;
                weightSum += 33;
            }
            if (withdrawSuccess[1] > 0) {
                score += (double) withdrawSuccess[0] / withdrawSuccess[1] * 33;
                weightSum += 33;
            }
            if (weightSum > 0) score = score / weightSum * 100;
            score -= avgOpen * 0.3 + avgDeposit * 0.3 + avgWithdraw * 0.3;

            if (score > bestScore) {
                bestScore = score;
                bestDelay = delay;
            }
        }

        sb.append("\n--- 结论 ---\n");
        if (bestDelay >= 0) {
            sb.append(String.format("推荐 actionDelayTicks: %d (综合评分最高)\n", bestDelay));
        }

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
        if (success == null || success[1] == 0) return "N/A";
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

        // 第一轮：存货
        AWAIT_CLOSE_FOR_DEPOSIT_OPEN,  // 关闭容器后打开（存货准备）
        AWAIT_OPEN,                     // 等待箱子打开
        AWAIT_DEPOSIT,                  // 等待存货完成
        AWAIT_CLOSE_FOR_DEPOSIT,        // 存货后关闭

        // 第二轮：取货
        AWAIT_CLOSE_FOR_WITHDRAW_OPEN,  // 关闭容器后打开（取货准备）
        AWAIT_WITHDRAW,                 // 等待取货完成
        AWAIT_CLOSE_FOR_WITHDRAW,       // 取货后关闭

        REPORT
    }
}
