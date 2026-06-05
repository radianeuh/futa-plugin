package com.github.futa.module;

import com.github.futa.FutaPlugin;
import com.github.futa.config.AutoChestManagerConfig;
import com.github.futa.dto.ChestLocation;
import com.github.futa.util.EnchantmentUtil;
import com.github.futa.util.ZUtil;
import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.chat.SystemChatEvent;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.ClickItem;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.actions.DropItem;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.pathfinder.BlockStateInterface;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.mc.item.ItemData;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.impl.AbstractInventoryModule;
import com.zenith.util.RequestFuture;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.DropItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

public class AutoChestManagerModule extends AbstractInventoryModule {
    private final AutoChestManagerConfig config = FutaPlugin.PLUGIN_CONFIG.autoChest;
    private volatile ScheduledFuture<?> task;
    private static final int SHULKER_BOX_SIZE = 27;
    private long lastProcessTime = 0;
    private boolean isProcessing = false;
    private boolean isReady = true;
    private Container currentOpenContainer = null;

    // 状态管理
    private ProcessingState currentState = ProcessingState.IDLE;
    private RequestFuture currentInventoryFuture;
    private List<ChestLocation> chestsToProcess = new ArrayList<>();
    private int currentChestIndex = 0;
    private long stateStartTime = 0;
    private long nextChestTime = 0; // 下一个箱子处理时间
    private long nextRoundTime = 0; // 下一轮处理时间
    private static final long STATE_TIMEOUT_MS = 100000; // 100秒超时
    private static final long CHEST_DELAY_MS = 1000; // 箱子间延迟1秒
    private static final long ROUND_DELAY_MS = 8000; // 轮次间延迟

    // 处理状态枚举
    private enum ProcessingState {
        IDLE,
        OPENING_CHEST,
        WITHDRAWING_FROM_CHEST,
        CLOSING_CHEST,
        WAITING_NEXT_CHEST,
        OPENING_SHULKER,
        DEPOSITING_TO_SHULKER,
        CLOSING_SHULKER,
        OPENING_TRASH,
        DEPOSITING_TO_TRASH,
        CLOSING_TRASH,
        DROPPING_TRASH,
        PROCESSING_COMPLETE,
        WAITING_NEXT_ROUND
    }

    public AutoChestManagerModule() {
        super(HandRestriction.MAIN_HAND, 0);
    }

    public void processingState() {
        // 状态机处理
        switch (currentState) {
            case IDLE -> {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastProcessTime >= (config.interval * 1000)) {
                    lastProcessTime = currentTime;
                    info("开始新的处理周期");
                    startChestProcessing();
                }
            }
            case OPENING_CHEST -> handleOpeningChest();
            case WITHDRAWING_FROM_CHEST -> handleWithdrawingFromChest();
            case CLOSING_CHEST -> handleClosingChest();
            case WAITING_NEXT_CHEST -> handleWaitingNextChest();
            case OPENING_SHULKER -> handleOpeningShulker();
            case DEPOSITING_TO_SHULKER -> handleDepositingToShulker();
            case CLOSING_SHULKER -> handleClosingShulker();
            case OPENING_TRASH -> handleOpeningTrash();
            case DEPOSITING_TO_TRASH -> handleDepositingToTrash();
            case CLOSING_TRASH -> handleClosingTrash();
            case DROPPING_TRASH -> handleDroppingTrash();
            case PROCESSING_COMPLETE -> handleProcessingComplete();
            case WAITING_NEXT_ROUND -> handleWaitingNextRound();
        }
    }

    @Override
    public boolean itemPredicate(ItemStack itemStack) {
        return isWantedItem(itemStack);
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
        info("自动箱子管理模块已启用");
        startTask();
    }

    @Override
    public void onDisable() {
        info("自动箱子管理模块已禁用");
        stopTask();
    }

    private void onSystemChat(SystemChatEvent event) {
        String message = event.message();

        // 检查登录是否成功 Logged-in due to Session Reconnection.
        if (message.contains("Connecting to the server")) {
            info("bot 进入游戏服中，开始自动箱子管理检测");
            isReady = true;
            startTask();
        }
    }

    private void onClientTick(ClientBotTick event) {
        if (!enabledSetting()) {
            if (currentState != ProcessingState.IDLE) {
                resetState();
            }
            return;
        }
        if (!isReady) {
            return;
        }

        // 检查状态超时
        if (currentState != ProcessingState.IDLE &&
                System.currentTimeMillis() - stateStartTime > STATE_TIMEOUT_MS) {
            error("状态 " + currentState + " 超时，重置状态");
            resetState();
            return;
        }

        processingState();
    }

    private void startTask() {
        if (task != null && !task.isDone()) return;
        // 不再使用定时任务，改用tick事件驱动
        info("自动箱子管理模块任务已启动");
    }

    private void stopTask() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    // 旧的 processNearbyChests 方法已被状态机替代

    // ChestLocation 类已移动到配置文件中

    /**
     * 重置状态机
     */
    private void resetState() {
        info("重置状态机 (当前状态: " + currentState + ")");
        currentState = ProcessingState.IDLE;
        chestsToProcess.clear();
        currentChestIndex = 0;
        isProcessing = false;
        currentInventoryFuture = null;
        nextChestTime = 0;
        nextRoundTime = 0;

        // 确保容器关闭
        try {
            Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer != null && openContainer.getContainerId() != 0) {
                info("关闭残留的容器 ID: " + openContainer.getContainerId());
                List<InventoryAction> actions = new ArrayList<>();
                actions.add(new CloseContainer(openContainer.getContainerId()));
                INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(actions)
                        .priority(600)
                        .build());
            }
        } catch (Exception e) {
            error("重置状态时关闭容器失败: " + e.getMessage());
        }
    }

    /**
     * 设置新状态
     */
    private void setState(ProcessingState newState) {
        info("状态变更: " + currentState + " -> " + newState);
        currentState = newState;
        stateStartTime = System.currentTimeMillis();
    }

    /**
     * 开始处理附近的箱子
     */
    private void startChestProcessing() {
        if (currentState != ProcessingState.IDLE) {
            error("尝试在非IDLE状态下开始处理，当前状态: " + currentState + "，强制重置");
            resetState();
            return;
        }

        isProcessing = true;
        chestsToProcess.clear();
        currentChestIndex = 0;

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
    private void processNextChest() {
        if (currentChestIndex >= chestsToProcess.size()) {
            setState(ProcessingState.PROCESSING_COMPLETE);
            return;
        }

        ChestLocation chest = chestsToProcess.get(currentChestIndex);
        info("处理箱子 " + (currentChestIndex + 1) + "/" + chestsToProcess.size() +
                " 在坐标: " + chest.toString());

        // 确保没有打开的容器
        if (closeCurrentContainer()) {
            return;
        }

        // 打开箱子
        BARITONE.rightClickBlock(chest.x, chest.y, chest.z);

        setState(ProcessingState.OPENING_CHEST);

    }

    /**
     * 处理打开箱子状态
     */
    private void handleOpeningChest() {
        Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer != null && openContainer.getContainerId() != 0) {
            info("箱子已打开，容器ID: " + openContainer.getContainerId());

            // 分析箱子内容并提取物品
            List<InventoryAction> withdrawActions = InventoryActionMacros.withdraw(
                    openContainer.getContainerId(),
                    itemStack -> {
                        if (!shouldExtractItem(itemStack)) return false;

                        ItemData itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
                        String itemName = itemData != null ? itemData.name() : "unknown";

                        // 判断物品类型用于日志
                        boolean isWanted = isWantedItem(itemStack);
                        boolean isTrash = isTrashItem(itemStack);
                        boolean isEnchantedBook = EnchantmentUtil.isEnchantedBook(itemStack);

                        // 特殊处理附魔书的日志
                        if (isEnchantedBook) {
                            Map<String, Integer> map = EnchantmentUtil.getEnchantmentMapCN(itemStack);

                            if (isWanted) {
                                info("提取附魔书(包含需要的附魔):" + EnchantmentUtil.mapToJsonStringStream(map));
                            } else {
                                info("提取附魔书(无需要的附魔，作为垃圾处理):" + EnchantmentUtil.mapToJsonStringStream(map));
                            }
                        } else {
                            info("提取物品: " + itemName + (isWanted ? " (需要)" : " (垃圾)"));
                        }

                        return true;
                    }, 20
            );

            if (!withdrawActions.isEmpty()) {
                currentInventoryFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(withdrawActions)
                        .priority(600)
                        .build());
                setState(ProcessingState.WITHDRAWING_FROM_CHEST);
            } else {
                info("箱子中没有需要的物品");
                closeCurrentContainer(openContainer.getContainerId());

                setState(ProcessingState.CLOSING_CHEST);
            }
        }
    }

    /**
     * 处理从箱子提取物品状态
     */
    private void handleWithdrawingFromChest() {
        if (currentInventoryFuture != null && currentInventoryFuture.isCompleted()) {
            info("物品提取完成");

            if (closeCurrentContainer()) {
                return;
            }

            // 检查玩家库存中的物品
            Container playerInv = CACHE.getPlayerCache().getInventoryCache().getPlayerInventory();
            boolean hasWantedItems = false;
            boolean hasTrashItems = false;

            for (int slot = 9; slot < 36; slot++) {
                ItemStack item = playerInv.getItemStack(slot);
                if (item != null && item != Container.EMPTY_STACK) {
                    ItemData itemData = ItemRegistry.REGISTRY.get(item.getId());
                    if (itemData != null) {
                        String itemName = itemData.name();
                        // 特殊处理附魔书
                        if ("enchanted_book".equals(itemName)) {
                            if (isWantedEnchantedBook(item)) {
                                hasWantedItems = true;
                            } else {
                                hasTrashItems = true;
                            }
                        } else if (isWantedItem(item)) {
                            hasWantedItems = true;
                        } else if (isTrashItem(item)) {
                            hasTrashItems = true;
                        }
                    }
                }
            }

            if (hasWantedItems) {
                // 处理需要的物品
                if (!ensureShulkerBoxExists()) {
                    error("无法确保潜影盒存在");
                    moveToNextChest();
                    return;
                }
                BARITONE.rightClickBlock(config.shulkerX, config.shulkerY, config.shulkerZ);
                setState(ProcessingState.OPENING_SHULKER);
            } else if (hasTrashItems) {
                // 处理垃圾物品
                if (config.trashX != 0 || config.trashY != 0 || config.trashZ != 0) {
                    BARITONE.rightClickBlock(config.trashX, config.trashY, config.trashZ);
                    setState(ProcessingState.OPENING_TRASH);
                } else {
                    setState(ProcessingState.DROPPING_TRASH);
                }
            } else {
                info("没有需要处理的物品");
                moveToNextChest();
            }
        }
    }

    /**
     * 处理关闭箱子状态
     */
    private void handleClosingChest() {
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
    private void moveToNextChest() {
        currentChestIndex++;
        if (currentChestIndex >= chestsToProcess.size()) {
            setState(ProcessingState.PROCESSING_COMPLETE);
        } else {
            // 设置下一个箱子处理时间
            nextChestTime = System.currentTimeMillis() + CHEST_DELAY_MS;
            setState(ProcessingState.WAITING_NEXT_CHEST);
        }
    }

    /**
     * 处理等待下一个箱子状态
     */
    private void handleWaitingNextChest() {
        if (System.currentTimeMillis() >= nextChestTime) {
            processNextChest();
        }
    }

    /**
     * 处理打开潜影盒状态
     */
    private void handleOpeningShulker() {
        Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer != null && openContainer.getContainerId() != 0) {
            // 验证是否为潜影盒
            int blockStateId = BlockStateInterface.getId(config.shulkerX, config.shulkerY, config.shulkerZ);
            if (!ZUtil.isShulkerBox(blockStateId)) {
                error("打开的容器不是潜影盒");
                moveToNextChest();
                return;
            }

            info("潜影盒已打开，容器ID: " + openContainer.getContainerId());

            // 检查潜影盒是否已满
            if (isShulkerBoxFull(openContainer)) {
                info("潜影盒已满，跳过存储");
                closeCurrentContainer(openContainer.getContainerId());

                setState(ProcessingState.CLOSING_SHULKER);
                return;
            }

            // 存储需要的物品
            List<InventoryAction> depositActions = InventoryActionMacros.deposit(
                    openContainer.getContainerId(),
                    itemStack -> {
                        if (itemStack == null || itemStack == Container.EMPTY_STACK) return false;
                        boolean isWanted = isWantedItem(itemStack);
                        if (isWanted) {
                            ItemData itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
                            String itemName = itemData != null ? itemData.name() : "unknown";
                            info("存储需要的物品: " + itemName);
                        }
                        return isWanted;
                    }
            );

            depositActions.add(new CloseContainer(openContainer.getContainerId()));
            currentInventoryFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actions(depositActions)
                    .priority(600)
                    .build());
            setState(ProcessingState.DEPOSITING_TO_SHULKER);
        }
    }

    /**
     * 处理存储到潜影盒状态
     */
    private void handleDepositingToShulker() {
        if (currentInventoryFuture != null && currentInventoryFuture.isCompleted()) {
            info("物品已存储到潜影盒");

            // 检查是否还有垃圾物品需要处理
            Container playerInv = CACHE.getPlayerCache().getInventoryCache().getPlayerInventory();
            boolean hasTrashItems = false;

            for (int slot = 9; slot < 45; slot++) {
                ItemStack item = playerInv.getItemStack(slot);
                if (item != null && item != Container.EMPTY_STACK && isTrashItem(item)) {
                    hasTrashItems = true;
                    break;
                }
            }

            if (hasTrashItems) {
                if (config.trashX != 0 || config.trashY != 0 || config.trashZ != 0) {
                    BARITONE.rightClickBlock(config.trashX, config.trashY, config.trashZ);
                    setState(ProcessingState.OPENING_TRASH);
                } else {
                    setState(ProcessingState.DROPPING_TRASH);
                }
            } else {
                moveToNextChest();
            }
        }
    }

    /**
     * 处理关闭潜影盒状态
     */
    private void handleClosingShulker() {
        if (currentInventoryFuture != null && currentInventoryFuture.isCompleted()) {
            info("潜影盒已关闭");
            moveToNextChest();
        } else if (currentInventoryFuture == null) {
            error("关闭潜影盒状态但 currentInventoryFuture 为 null，强制移动到下一个箱子");
            moveToNextChest();
        } else {
            // 添加调试信息
            info("等待潜影盒关闭操作完成... (future completed: " + currentInventoryFuture.isCompleted() + ")");
        }
    }

    /**
     * 处理打开垃圾桶状态
     */
    private void handleOpeningTrash() {
        Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer != null && openContainer.getContainerId() != 0) {
            // 验证是否为有效的垃圾桶容器
            int blockStateId = BlockStateInterface.getId(config.trashX, config.trashY, config.trashZ);
            if (!isValidTrashContainer(blockStateId)) {
                error("打开的容器不是有效的垃圾桶");
                closeCurrentContainer();
                setState(ProcessingState.CLOSING_TRASH);
                return;
            }

            info("垃圾桶已打开，容器ID: " + openContainer.getContainerId());

            // 存储垃圾物品
            List<InventoryAction> depositActions = InventoryActionMacros.deposit(
                    openContainer.getContainerId(),
                    itemStack -> {
                        boolean isTrash = isTrashItem(itemStack);
                        if (isTrash) {
                            ItemData itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
                            String itemName = itemData != null ? itemData.name() : "unknown";
                            if ("enchanted_book".equals(itemName)) {
                                info("存储垃圾附魔书: " + EnchantmentUtil.getEnchantmentJson(itemStack));
                            } else {
                                info("存储垃圾物品: " + itemName);
                            }
                        }
                        return isTrash;
                    }, 9
            );

            depositActions.add(new CloseContainer(openContainer.getContainerId()));
            currentInventoryFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actions(depositActions)
                    .priority(600)
                    .build());
            setState(ProcessingState.DEPOSITING_TO_TRASH);
        }
    }

    /**
     * 处理存储到垃圾桶状态
     */
    private void handleDepositingToTrash() {
        if (currentInventoryFuture != null && currentInventoryFuture.isCompleted()) {
            info("垃圾物品已存储到垃圾桶");
            moveToNextChest();
        }
    }

    /**
     * 处理关闭垃圾桶状态
     */
    private void handleClosingTrash() {
        if (currentInventoryFuture != null && currentInventoryFuture.isCompleted()) {
            setState(ProcessingState.DROPPING_TRASH);
        }
    }

    /**
     * 处理丢弃垃圾状态
     */
    private void handleDroppingTrash() {
        Container playerInv = CACHE.getPlayerCache().getInventoryCache().getPlayerInventory();
        List<InventoryAction> dropActions = new ArrayList<>();

        for (int slot = 9; slot < 45; slot++) {
            ItemStack item = playerInv.getItemStack(slot);
            if (item != null && item != Container.EMPTY_STACK && isTrashItem(item)) {
                ItemData itemData = ItemRegistry.REGISTRY.get(item.getId());
                String itemName = itemData != null ? itemData.name() : "unknown";
                if ("enchanted_book".equals(itemName)) {
                    info("丢弃垃圾附魔书: " + EnchantmentUtil.getEnchantmentJson(item));
                } else {
                    info("丢弃垃圾物品: " + itemName);
                }
                dropActions.add(new DropItem(0, slot, DropItemAction.DROP_SELECTED_STACK));
            }
        }

        if (!dropActions.isEmpty()) {
            currentInventoryFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actions(dropActions)
                    .priority(600)
                    .build());
            setState(ProcessingState.PROCESSING_COMPLETE);
        } else {
            info("没有垃圾物品需要丢弃");
            moveToNextChest();
        }
    }

    /**
     * 处理处理完成状态
     */
    private void handleProcessingComplete() {
        info("所有箱子处理完成，等待 " + (ROUND_DELAY_MS / 1000) + " 秒后开始下一轮");
        nextRoundTime = System.currentTimeMillis() + ROUND_DELAY_MS;
        setState(ProcessingState.WAITING_NEXT_ROUND);
    }

    /**
     * 处理等待下一轮状态
     */
    private void handleWaitingNextRound() {
        if (System.currentTimeMillis() >= nextRoundTime) {
            info("等待结束，重置状态机准备下一轮");
            resetState();
        }
    }

    // isChest 方法已删除，不再需要搜索箱子

    /**
     * 判断物品是否为需要的物品（包括附魔书的特殊判断）
     */
    private boolean isWantedItem(ItemStack item) {
        if (item == null || item == Container.EMPTY_STACK) return false;

        ItemData itemData = ItemRegistry.REGISTRY.get(item.getId());
        if (itemData == null) return false;

        String itemName = itemData.name();

        // 如果是附魔书，需要检查具体附魔
        if ("enchanted_book".equals(itemName)) {
            return isWantedEnchantedBook(item);
        }

        // 其他物品直接检查物品列表
        return config.wantedItems.contains(itemName);
    }

    /**
     * 判断物品是否应该被提取（包括所有附魔书）
     */
    private boolean shouldExtractItem(ItemStack item) {
        if (item == null || item == Container.EMPTY_STACK) return false;

        ItemData itemData = ItemRegistry.REGISTRY.get(item.getId());
        if (itemData == null) return false;

        String itemName = itemData.name();

        // 所有附魔书都要提取
        if ("enchanted_book".equals(itemName)) {
            return true;
        }

        // 其他物品检查是否在需要列表或垃圾列表中
        return config.wantedItems.contains(itemName) || isTrashItem(item);
    }

    /**
     * 判断物品是否为垃圾（包括没有需要附魔的附魔书）
     */
    private boolean isTrashItem(ItemStack item) {
        if (item == null || item == Container.EMPTY_STACK) return false;

        ItemData itemData = ItemRegistry.REGISTRY.get(item.getId());
        if (itemData == null) return false;

        String itemName = itemData.name();

        // 如果是附魔书，检查是否没有需要的附魔
        if ("enchanted_book".equals(itemName)) {
            return !isWantedEnchantedBook(item); // 没有需要的附魔就是垃圾
        }
        if (itemName.startsWith("music_disc_")) {
            return true;
        }
        // 其他物品检查垃圾列表
        return config.trashItems.contains(itemName);
    }

    /**
     * 判断附魔书是否包含需要的附魔
     */
    private boolean isWantedEnchantedBook(ItemStack item) {
        try {
            Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(item);
            if (enchantmentMap == null) return false;


            // 检查是否包含任何需要的附魔
            for (String wantedEnchantment : config.wantedEnchantments) {

                if (enchantmentMap.containsKey(wantedEnchantment)) {
                    Integer level = enchantmentMap.get(wantedEnchantment);

                    boolean maxLevel = EnchantmentUtil.isMaxLevel(wantedEnchantment, level);
                    if (maxLevel) {
                        info("发现需要附魔书，包含附魔: " + EnchantmentUtil.getChinese(wantedEnchantment) + ", level: " + level);
                        return true;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            error("检查附魔书时出错: " + e.getMessage());
            return false;
        }
    }

    private boolean isEnderChest(int x, int y, int z) {
        int blockStateId = BlockStateInterface.getId(x, y, z);
        return blockStateId >= BlockRegistry.ENDER_CHEST.minStateId() && blockStateId <= BlockRegistry.ENDER_CHEST.maxStateId();
    }

    @Deprecated
    private boolean isChest(int blockStateId) {
        return blockStateId >= BlockRegistry.CHEST.minStateId() && blockStateId <= BlockRegistry.CHEST.maxStateId();
    }

    /**
     * 搜索附近的末影箱
     */
    @Deprecated
    public boolean searchForEnderChest(int radius) {
        int px = (int) CACHE.getPlayerCache().getX();
        int py = (int) CACHE.getPlayerCache().getY();
        int pz = (int) CACHE.getPlayerCache().getZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = px + dx;
                    int y = py + dy;
                    int z = pz + dz;
                    if (isEnderChest(x, y, z)) {
                        // 找到末影箱
                        // 可在此处直接调用BARITONE.rightClickBlock(x, y, z)等操作
                        // 这里只返回true，实际用法可根据需要扩展
                        return true;
                    }
                }
            }
        }
        return false;
    }


    /**
     * 验证是否为有效的垃圾桶容器
     */
    private boolean isValidTrashContainer(int blockStateId) {
        // 检查是否为箱子、桶或其他容器方块
        return (blockStateId >= BlockRegistry.CHEST.minStateId() && blockStateId <= BlockRegistry.CHEST.maxStateId()) ||
                (blockStateId >= BlockRegistry.DROPPER.minStateId() && blockStateId <= BlockRegistry.DROPPER.maxStateId());
//        // 检查是否为箱子、桶或其他容器方块
//        return (blockStateId >= BlockRegistry.CHEST.minStateId() && blockStateId <= BlockRegistry.CHEST.maxStateId()) ||
//               (blockStateId >= BlockRegistry.BARREL.minStateId() && blockStateId <= BlockRegistry.BARREL.maxStateId()) ||
//               (blockStateId >= BlockRegistry.HOPPER.minStateId() && blockStateId <= BlockRegistry.HOPPER.maxStateId()) ||
//               (blockStateId >= BlockRegistry.DROPPER.minStateId() && blockStateId <= BlockRegistry.DROPPER.maxStateId()) ||
//               (blockStateId >= BlockRegistry.DISPENSER.minStateId() && blockStateId <= BlockRegistry.DISPENSER.maxStateId());
    }

    /**
     * 判断潜影盒是否已满
     */
    private boolean isShulkerBoxFull(Container shulker) {
        for (int i = 0; i < shulker.getSize() - 36; i++) {
            ItemStack item = shulker.getItemStack(i);
            if (item == null || item == Container.EMPTY_STACK) {
                return false;
            }
        }
        return true;
    }

    // 简化潜影盒逻辑，不再需要打掉潜影盒

    /**
     * 在指定坐标放置新的潜影盒（先切主手，再rightClickBlock）
     */
    private void placeNewShulkerBox(int x, int y, int z) {
        int shulkerSlot = findShulkerInInventory();
        if (shulkerSlot == -1) {
            info("背包中没有潜影盒，无法放置");
            return;
        }

        info("正在放置新的潜影盒: " + x + ", " + y + ", " + z);

        // 用背包操作将潜影盒移到主手（槽位36）
        List<InventoryAction> actions = new ArrayList<>();
        actions.add(new ClickItem(shulkerSlot, ClickItemAction.LEFT_CLICK));
        actions.add(new ClickItem(36, ClickItemAction.LEFT_CLICK)); // 36为主手槽位
        INVENTORY.submit(InventoryActionRequest.builder()
                .owner(this)
                .actions(actions)
                .priority(600)
                .build());

        try {
            Thread.sleep(500); // 等待物品移动完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 放置潜影盒
        BARITONE.rightClickBlock(x, y, z);

        try {
            Thread.sleep(500); // 等待放置完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 查找背包中的潜影盒物品栏slot
     */
    private int findShulkerInInventory() {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();


        for (int slot = 9; slot <= 44; slot++) {
            ItemStack item = inv.get(slot);
            if (item != null && item != Container.EMPTY_STACK) {
                ItemData data = ItemRegistry.REGISTRY.get(item.getId());
                if (data != null && data == ItemRegistry.SHULKER_BOX) {
                    info("在背包槽位 " + slot + " 找到潜影盒: " + data.name());
                    return slot;
                }
            }
        }
        info("背包中未找到潜影盒");
        return -1;
    }

    /**
     * 直接从玩家库存丢弃垃圾物品
     */
    private void dropTrashItemsFromInventory() {
        try {
            Container playerInv = CACHE.getPlayerCache().getInventoryCache().getPlayerInventory();
            List<InventoryAction> dropActions = new ArrayList<>();

            // 查找玩家库存中的垃圾物品并丢弃
            for (int slot = 9; slot < 45; slot++) { // 主库存槽位
                ItemStack item = playerInv.getItemStack(slot);
                if (item != null && item != Container.EMPTY_STACK && isTrashItem(item)) {
                    ItemData itemData = ItemRegistry.REGISTRY.get(item.getId());
                    String itemName = itemData != null ? itemData.name() : "unknown";
                    if ("enchanted_book".equals(itemName)) {
                        info("丢弃垃圾附魔书: " + itemName + " (无需要的附魔)");
                    } else {
                        info("丢弃垃圾物品: " + itemName);
                    }
                    dropActions.add(new DropItem(0, slot, DropItemAction.DROP_SELECTED_STACK));
                }
            }

            if (!dropActions.isEmpty()) {
                info("丢弃 " + dropActions.size() + " 个垃圾物品");
                INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(dropActions)
                        .priority(600)
                        .build());

                Thread.sleep(300);
            } else {
                info("玩家库存中没有垃圾物品需要丢弃");
            }

        } catch (Exception e) {
            error("丢弃垃圾物品失败: " + e.getMessage());
        }
    }

    /**
     * 查找玩家库存中的空槽位
     */
    private int findEmptySlotInPlayerInventory() {
        Container playerInv = CACHE.getPlayerCache().getInventoryCache().getPlayerInventory();
        // 检查主库存槽位 (9-35)
        for (int slot = 9; slot < 45; slot++) {
            ItemStack item = playerInv.getItemStack(slot);
            if (item == null || item == Container.EMPTY_STACK) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * 关闭当前容器
     */
    public boolean closeCurrentContainer(int containerId) {

        // 确保容器关闭

        List<InventoryAction> actions = new ArrayList<>();
        actions.add(new CloseContainer(containerId));
        INVENTORY.submit(InventoryActionRequest.builder()
                .owner(this)
                .actions(actions)
                .priority(600)
                .build());
        currentOpenContainer = null;

        return true;

    }

    /**
     * 关闭当前容器
     */
    public boolean closeCurrentContainer() {

        // 确保容器关闭
        try {
            Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer != null && openContainer.getContainerId() != 0) {
                info("正在关闭当前容器 ID: " + openContainer.getContainerId());
                List<InventoryAction> actions = new ArrayList<>();
                actions.add(new CloseContainer(openContainer.getContainerId()));
                INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(actions)
                        .priority(600)
                        .build());
                currentOpenContainer = null;

                return true;
            }
        } catch (Exception e) {
            error("关闭容器失败: " + e.getMessage());
        }

        return false;
    }

    // 这些方法已被使用 InventoryActionMacros 的新方法替代

    /**
     * 在容器中查找空槽位
     */
    private int findEmptySlotInContainer(Container container) {
        for (int slot = 0; slot < container.getSize() - 36; slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item == null || item == Container.EMPTY_STACK) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * 确保潜影盒存在
     */
    private boolean ensureShulkerBoxExists() {
        if (config.shulkerX == 0 && config.shulkerY == 0 && config.shulkerZ == 0) {
            info("未配置潜影盒坐标");
            return false;
        }

        int blockStateId = BlockStateInterface.getId(config.shulkerX, config.shulkerY, config.shulkerZ);
        if (!ZUtil.isShulkerBox(blockStateId)) {
            info("配置的坐标处没有潜影盒，尝试放置新的潜影盒");
            placeNewShulkerBox(config.shulkerX, config.shulkerY, config.shulkerZ);

            // 检查是否放置成功
            try {
                Thread.sleep(1000);
                blockStateId = BlockStateInterface.getId(config.shulkerX, config.shulkerY, config.shulkerZ);
                return ZUtil.isShulkerBox(blockStateId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return true;
    }

    /**
     * 重新打开箱子（用于处理完物品后继续处理下一个物品）
     */
    private void reopenChest() {
        // 这里需要记住当前处理的箱子坐标
        // 为简化，暂时不实现重新打开逻辑
        // 实际使用中，每次处理完一个箱子后会自然进入下一个箱子
        try {
            Thread.sleep(500); // 给一些时间让操作完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    private int countInvEmptySlots() {
        int count = 0;
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 9; i <= 44; i++) {
            if (inv.get(i) == Container.EMPTY_STACK) {
                count++;
            }
        }
        return count;
    }
}
