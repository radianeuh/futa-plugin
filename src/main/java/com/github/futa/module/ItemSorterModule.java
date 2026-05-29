package com.github.futa.module;

import com.github.futa.config.ItemSorterConfig;
import com.github.futa.util.ChestCacheManager;
import com.github.futa.dto.ChestLocation;
import com.github.futa.util.ItemClassifier;
import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.chat.SystemChatEvent;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.mc.item.ItemData;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.impl.AbstractInventoryModule;
import com.zenith.util.RequestFuture;
import com.zenith.util.math.MathHelper;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

public class ItemSorterModule extends AbstractInventoryModule {

    private final ItemSorterConfig config = PLUGIN_CONFIG.itemSorter;
    private final ItemClassifier classifier = new ItemClassifier(config);
    private final ChestCacheManager chestCache;

    // 状态管理
    private ProcessingState currentState = ProcessingState.IDLE;
    private RequestFuture currentInventoryFuture;
    private List<ChestLocation> chestsToProcess = new ArrayList<>();
    private int currentChestIndex = 0;
    private long stateStartTime = 0;
    private long lastProcessTime = 0;
    private long nextChestTime = 0;
    private long nextRoundTime = 0;
    private Container currentOpenContainer = null;
    ChestLocation openedChest = null;
    // 当前处理的物品分类信息
    private Map<String, List<ItemStack>> categorizedItems = new ConcurrentHashMap<>();
    private List<String> categoriesToProcess = new ArrayList<>();
    private int currentCategoryIndex = 0;

    // 时间常量
    private static final long STATE_TIMEOUT_MS = 120000; // 2分钟超时
    private static final long CHEST_DELAY_MS = 100; // 箱子间延迟1秒
    private static final long ROUND_DELAY_MS = 20000; // 轮次间延迟8秒
    private static final long CATEGORY_DELAY_MS = 500; // 分类间延迟0.5秒

    // 处理状态枚举
    private enum ProcessingState {
        IDLE,
        RESETTING,
        OPENING_SOURCE_CHEST,
        WITHDRAWING_FROM_CHEST,
        CLOSING_SOURCE_CHEST,
        WAITING_NEXT_CHEST,
        CATEGORIZING_ITEMS,
        OPENING_TARGET_CONTAINER,
        DEPOSITING_ITEMS,
        CLOSING_TARGET_CONTAINER,
        WAITING_NEXT_CATEGORY,
        PROCESSING_COMPLETE,
        WAITING_NEXT_ROUND
    }

    public ItemSorterModule() {
        super(HandRestriction.MAIN_HAND, 0);
        chestCache = new ChestCacheManager(config, classifier, this);
    }

    @Override
    public boolean itemPredicate(ItemStack itemStack) {
        // 接受所有物品进行分类
        return true;
    }

    @Override
    public int getPriority() {
        return 600;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(SystemChatEvent.class, this::onSystemChat),
                of(ClientBotTick.class, this::onClientTick)
        );
    }

    @Override
    public boolean enabledSetting() {
        return config.enabled;
    }

    @Override
    public void onEnable() {
        config.init();
        info("物品分类模块已启用");
        resetState();
    }

    @Override
    public void onDisable() {
        info("物品分类模块已禁用");
        resetState();
    }

    private synchronized void onSystemChat(SystemChatEvent event) {
        String message = event.message();

        if (message.contains("Connecting to the server")) {
            info("bot 进入游戏服中，开始物品分类检测");
            resetState();
        }
    }

    private synchronized void onClientTick(ClientBotTick event) {
        if (!enabledSetting()) {
            if (currentState != ProcessingState.IDLE) {
                resetState();
            }
            return;
        }

        List<ChestLocation> locations = config.chestLocations;
        if (locations == null || locations.isEmpty()) {
            return;
        }
        ChestLocation location = locations.get(0);

        // 获取位置
        int playerX = (int) CACHE.getPlayerCache().getX();
        int playerY = (int) CACHE.getPlayerCache().getY();
        int playerZ = (int) CACHE.getPlayerCache().getZ();

        double distance3d = MathHelper.manhattanDistance3d(playerX, playerY, playerZ, location.x, location.y, location.z);
        if (distance3d > 200) {
            return;
        }

        // 更新箱子缓存
        chestCache.updateCache();

        // 检查状态超时
        if (currentState != ProcessingState.IDLE &&
                System.currentTimeMillis() - stateStartTime > STATE_TIMEOUT_MS) {
            error("状态 " + currentState + " 超时，重置状态");
            resetState();
            return;
        }


        // 状态机处理
        switch (currentState) {
            case IDLE -> handleIdleState();
            case RESETTING -> handleResettingState();
            case OPENING_SOURCE_CHEST -> handleOpeningSourceChest();
            case WITHDRAWING_FROM_CHEST -> handleWithdrawingFromChest();
            case CLOSING_SOURCE_CHEST -> handleClosingSourceChest();
            case WAITING_NEXT_CHEST -> handleWaitingNextChest();
            case CATEGORIZING_ITEMS -> handleCategorizingItems();
            case OPENING_TARGET_CONTAINER -> handleOpeningTargetContainer();
            case DEPOSITING_ITEMS -> handleDepositingItems();
            case CLOSING_TARGET_CONTAINER -> handleClosingTargetContainer();
            case WAITING_NEXT_CATEGORY -> handleWaitingNextCategory();
            case PROCESSING_COMPLETE -> handleProcessingComplete();
            case WAITING_NEXT_ROUND -> handleWaitingNextRound();
        }
    }

    private void handleResettingState() {
        if (currentInventoryFuture != null && currentInventoryFuture.isCompleted()) {
            setState(ProcessingState.IDLE);
        }

    }

    /**
     * 重置状态机
     */
    private synchronized void resetState() {
        info("重置物品分类状态机 (当前状态: " + currentState + ")");
        currentState = ProcessingState.IDLE;
        chestsToProcess.clear();
        currentChestIndex = 0;
        categorizedItems.clear();
        categoriesToProcess.clear();
        currentCategoryIndex = 0;
        currentInventoryFuture = null;
        nextChestTime = 0;
        nextRoundTime = 0;

        // 确保容器关闭
        try {
            Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer != null && openContainer.getContainerId() != 0) {
                currentOpenContainer = openContainer;
                info("关闭残留的容器 ID: " + openContainer.getContainerId());
                List<InventoryAction> actions = new ArrayList<>();
                actions.add(new CloseContainer(openContainer.getContainerId()));
                INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(actions)
                        .priority(600)
                        .build());
            }


            currentInventoryFuture = BARITONE.pathTo(config.centerX, config.centerY, config.centerZ);

            setState(ProcessingState.RESETTING);
        } catch (Exception e) {
            error("重置状态时关闭容器失败: " + e.getMessage());
        }
    }

    /**
     * 设置新状态
     */
    private synchronized void setState(ProcessingState newState) {
        info("状态变更: " + currentState + " -> " + newState);
        currentState = newState;
        stateStartTime = System.currentTimeMillis();
    }

    /**
     * 处理空闲状态
     */
    private synchronized void handleIdleState() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessTime >= (config.processingIntervalTicks * 50)) {
            lastProcessTime = currentTime;
            info("开始新的物品分类周期");
            startItemSorting();
        }
    }

    /**
     * 开始物品分类处理
     */
    private synchronized void startItemSorting() {
        if (currentState != ProcessingState.IDLE) {
            error("尝试在非IDLE状态下开始处理，当前状态: " + currentState + "，强制重置");
            resetState();
            return;
        }

        chestsToProcess.clear();
        currentChestIndex = 0;
        categorizedItems.clear();

        // 使用配置的箱子列表
        chestsToProcess.addAll(config.chestLocations);

        if (chestsToProcess.isEmpty()) {
            info("配置中没有箱子坐标，跳过处理");
            setState(ProcessingState.PROCESSING_COMPLETE);
            return;
        }

        info("开始处理配置的 " + chestsToProcess.size() + " 个箱子");
        processNextChest();
    }

    /**
     * 处理下一个箱子
     */
    private synchronized void processNextChest() {
        if (currentChestIndex >= chestsToProcess.size()) {
            // 所有箱子处理完成，开始分类物品
            setState(ProcessingState.CATEGORIZING_ITEMS);
            return;
        }

        ChestLocation chest = chestsToProcess.get(currentChestIndex);
        info("处理箱子 " + (currentChestIndex + 1) + "/" + chestsToProcess.size() +
                " 在坐标: " + chest.toString());

        // 确保没有打开的容器
        Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer != null && openContainer.getContainerId() != 0) {
            currentOpenContainer = openContainer;

            List<InventoryAction> actions = new ArrayList<>();
            actions.add(new CloseContainer(openContainer.getContainerId()));
            currentInventoryFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actions(actions)
                    .priority(600)
                    .build());
            setState(ProcessingState.CLOSING_SOURCE_CHEST);
            return;
        }

        // 打开箱子
        BARITONE.rightClickBlock(chest.x, chest.y, chest.z);
        openedChest = chest;

        setState(ProcessingState.OPENING_SOURCE_CHEST);
    }

    /**
     * 处理打开源箱子状态
     */
    private synchronized void handleOpeningSourceChest() {
        Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer != null && openContainer.getContainerId() != 0) {
            currentOpenContainer = openContainer;

            info("箱子已打开，容器ID: " + openContainer.getContainerId());

            // 提取所有物品到玩家库存
            List<InventoryAction> withdrawActions = InventoryActionMacros.withdraw(
                    openContainer.getContainerId(),
                    itemStack -> itemStack != Container.EMPTY_STACK
            );

            if (!withdrawActions.isEmpty()) {
                withdrawActions.add(new CloseContainer(openContainer.getContainerId()));
                currentInventoryFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(withdrawActions)
                        .priority(600)
                        .build());
                setState(ProcessingState.WITHDRAWING_FROM_CHEST);
            } else {
                info("箱子中没有物品");
                List<InventoryAction> closeActions = new ArrayList<>();
                closeActions.add(new CloseContainer(openContainer.getContainerId()));
                currentInventoryFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(closeActions)
                        .priority(600)
                        .build());
                setState(ProcessingState.CLOSING_SOURCE_CHEST);
            }
        }
    }

    /**
     * 处理从箱子提取物品状态
     */
    private synchronized void handleWithdrawingFromChest() {
        if (currentInventoryFuture != null && currentInventoryFuture.isCompleted()) {
            info("物品提取完成");
            moveToNextChest();
        }
    }

    /**
     * 处理关闭源箱子状态
     */
    private synchronized void handleClosingSourceChest() {
        if (currentInventoryFuture != null && currentInventoryFuture.isCompleted()) {
            moveToNextChest();
        } else if (currentInventoryFuture == null) {
            error("关闭箱子状态但 currentInventoryFuture 为 null，强制移动到下一个箱子");
            moveToNextChest();
        }
    }

    /**
     * 移动到下一个箱子
     */
    private synchronized void moveToNextChest() {
        currentChestIndex++;
        if (currentChestIndex >= chestsToProcess.size()) {
            setState(ProcessingState.CATEGORIZING_ITEMS);
        } else {
            // 设置下一个箱子处理时间
            nextChestTime = System.currentTimeMillis() + CHEST_DELAY_MS;
            setState(ProcessingState.WAITING_NEXT_CHEST);
        }
    }

    /**
     * 处理等待下一个箱子状态
     */
    private synchronized void handleWaitingNextChest() {
        if (System.currentTimeMillis() >= nextChestTime) {
            processNextChest();
        }
    }

    /**
     * 处理物品分类状态
     */
    private synchronized void handleCategorizingItems() {
        info("开始分类玩家库存中的物品");

        // 清空之前的分类结果
        categorizedItems.clear();
        categoriesToProcess.clear();
        currentCategoryIndex = 0;

        // 获取玩家库存
        Container playerInv = CACHE.getPlayerCache().getInventoryCache().getPlayerInventory();

        // 分类所有物品 不包括hotbar
        for (int slot = 9; slot < 36; slot++) { // 主库存槽位
            ItemStack item = playerInv.getItemStack(slot);
            if (item != null) {
                String category = classifier.classifyItem(item);

                categorizedItems.computeIfAbsent(category, k -> new ArrayList<>()).add(item);

                ItemData itemData = ItemRegistry.REGISTRY.get(item.getId());
                String itemName = itemData != null ? itemData.name() : "unknown";
                info("物品 " + itemName + " 分类为: " + category);
            }
        }

        if (categorizedItems.isEmpty()) {
            info("没有物品需要分类");
            setState(ProcessingState.PROCESSING_COMPLETE);
            return;
        }

        // 准备处理分类
        categoriesToProcess.addAll(categorizedItems.keySet());
        info("共分类出 " + categoriesToProcess.size() + " 个类别: " + categoriesToProcess);

        // 开始处理第一个分类
        processNextCategory();
    }

    /**
     * 处理下一个分类
     */
    private synchronized void processNextCategory() {
        if (currentCategoryIndex >= categoriesToProcess.size()) {
            setState(ProcessingState.PROCESSING_COMPLETE);
            return;
        }

        String category = categoriesToProcess.get(currentCategoryIndex);
        List<ItemStack> items = categorizedItems.get(category);

        info("处理分类 " + (currentCategoryIndex + 1) + "/" + categoriesToProcess.size() +
                ": " + category + " (" + items.size() + " 个物品)");

        // 使用智能箱子缓存查找目标容器
        ChestLocation targetChest = chestCache.findChestForItem(category);
        if (targetChest == null) {
            info("分类 " + category + " 找不到合适的存储箱子，跳过");
            moveToNextCategory();
            return;
        }

        info("为分类 " + category + " 找到目标箱子: " + targetChest);

        // 打开目标箱子
        BARITONE.rightClickBlock(targetChest.x, targetChest.y, targetChest.z);
        openedChest = targetChest;
        setState(ProcessingState.OPENING_TARGET_CONTAINER);
    }

    /**
     * 处理打开目标容器状态
     */
    private synchronized void handleOpeningTargetContainer() {
        Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer != null && openContainer.getContainerId() != 0) {
            currentOpenContainer = openContainer;

            String category = categoriesToProcess.get(currentCategoryIndex);
            info("目标容器已打开，容器ID: " + openContainer.getContainerId() + "，开始存储分类: " + category);


            boolean shouldReturn = false;
            // 更新箱子缓存
            if (openedChest != null) {
                shouldReturn = chestCache.onChestOpened(openedChest.x, openedChest.y, openedChest.z, openContainer, category);
            }
            if (shouldReturn) {
                List<InventoryAction> closeActions = new ArrayList<>();
                closeActions.add(new CloseContainer(openContainer.getContainerId()));
                currentInventoryFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(closeActions)
                        .priority(600)
                        .build());

                setState(ItemSorterModule.ProcessingState.CLOSING_TARGET_CONTAINER);

                return;
            }

            // 存储当前分类的物品
            List<InventoryAction> depositActions = InventoryActionMacros.deposit(
                    openContainer.getContainerId(),
                    itemStack -> {
                        if (itemStack == null || itemStack == Container.EMPTY_STACK) return false;
                        String itemCategory = classifier.classifyItem(itemStack);
                        boolean shouldDeposit = category.equals(itemCategory);

                        if (shouldDeposit) {
                            ItemData itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
                            String itemName = itemData != null ? itemData.name() : "unknown";
                            info("存储物品: " + itemName + " 到分类: " + category);
                        }

                        return shouldDeposit;
                    },
                    9
            );

            depositActions.add(new CloseContainer(openContainer.getContainerId()));
            currentInventoryFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actions(depositActions)
                    .priority(600)
                    .build());
            setState(ProcessingState.DEPOSITING_ITEMS);
        }
    }

    /**
     * 处理存储物品状态
     */
    private synchronized void handleDepositingItems() {
        if (currentInventoryFuture != null && currentInventoryFuture.isCompleted()) {
            String category = categoriesToProcess.get(currentCategoryIndex);
            info("分类 " + category + " 的物品存储完成");
            moveToNextCategory();
        }
    }

    /**
     * 处理关闭目标容器状态
     */
    private synchronized void handleClosingTargetContainer() {
        if (currentInventoryFuture != null && currentInventoryFuture.isCompleted()) {
            moveToNextCategory();
        } else if (currentInventoryFuture == null) {
            error("关闭目标容器状态但 currentInventoryFuture 为 null，强制移动到下一个分类");
            moveToNextCategory();
        }
    }

    /**
     * 移动到下一个分类
     */
    private synchronized void moveToNextCategory() {
        currentCategoryIndex++;
        if (currentCategoryIndex >= categoriesToProcess.size()) {
            setState(ProcessingState.PROCESSING_COMPLETE);
        } else {
            // 设置下一个分类处理时间
            nextChestTime = System.currentTimeMillis() + CATEGORY_DELAY_MS;
            setState(ProcessingState.WAITING_NEXT_CATEGORY);
        }
    }

    /**
     * 处理等待下一个分类状态
     */
    private synchronized void handleWaitingNextCategory() {
        if (System.currentTimeMillis() >= nextChestTime) {
            processNextCategory();
        }
    }

    /**
     * 处理处理完成状态
     */
    private synchronized void handleProcessingComplete() {
        info("所有物品分类完成，等待 " + (ROUND_DELAY_MS / 1000) + " 秒后开始下一轮");
        nextRoundTime = System.currentTimeMillis() + ROUND_DELAY_MS;
        setState(ProcessingState.WAITING_NEXT_ROUND);
    }

    /**
     * 处理等待下一轮状态
     */
    private synchronized void handleWaitingNextRound() {
        if (System.currentTimeMillis() >= nextRoundTime) {
            info("等待结束，重置状态机准备下一轮");
            resetState();
        }
    }

    /**
     * 获取物品的显示名称
     */
    private String getItemDisplayName(ItemStack itemStack) {
        if (itemStack == null) return "null";

        ItemData itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
        if (itemData == null) return "unknown";

        String name = itemData.name();

        // 如果是附魔物品，添加附魔标记
        if (classifier.isEnchanted(itemStack)) {
            name += " (附魔)";
        }

        return name;
    }

    /**
     * 统计当前库存中各分类的物品数量
     */
    private Map<String, Integer> getInventoryStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        Container playerInv = CACHE.getPlayerCache().getInventoryCache().getPlayerInventory();

        for (int slot = 9; slot < 45; slot++) {
            ItemStack item = playerInv.getItemStack(slot);
            if (item != null && item != Container.EMPTY_STACK) {
                String category = classifier.classifyItem(item);
                stats.put(category, stats.getOrDefault(category, 0) + item.getAmount());
            }
        }

        return stats;
    }

    /**
     * 打印库存统计信息
     */
    private synchronized void printInventoryStatistics() {
        Map<String, Integer> stats = getInventoryStatistics();
        if (stats.isEmpty()) {
            info("库存为空");
            return;
        }

        info("当前库存统计:");
        stats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> info("  " + entry.getKey() + ": " + entry.getValue() + " 个"));
    }


    /**
     * 关闭当前容器
     */
    private synchronized void closeCurrentContainer() {
        try {
            List<InventoryAction> actions = new ArrayList<>();
            actions.add(new CloseContainer());

            INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actions(actions)
                    .priority(600)
                    .build());

            Thread.sleep(100); // 等待容器关闭
            currentOpenContainer = null;

        } catch (Exception e) {
            error("关闭容器失败: " + e.getMessage());
        }
    }
}
