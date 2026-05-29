package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.futa.config.EnchantBookSorterConfig;
import com.github.futa.util.EnchantmentUtil;
import com.github.rfresh2.EventConsumer;
import com.google.common.collect.Lists;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.util.RequestFuture;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.*;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;


public class EnchantBookSorterModule extends BaseModule {
    public static final int PRIORITY = 700;

    private State state = State.SCAN_SOURCE_CHESTS;
    private int currentSourceChestIndex = 0;
    private int currentMiscChestIndex = 0;

    // 当前处理的附魔书
    private List<ItemStack> enchantBooksToSort = new ArrayList<>();
    private ItemStack currentProcessingBook = null;
    private String currentEnchantmentType = "";

    // 路径和操作相关
    private PathingRequestFuture pathingFuture = PathingRequestFuture.rejected;
    private RequestFuture inventoryActionFuture = RequestFuture.rejected;
    private final Timer actionDelayTimer = Timers.tickTimer();
    private final Timer interactTimer = Timers.tickTimer();

    // 箱子状态跟踪
    private Set<String> fullEnchantmentChests = new HashSet<>(); // 已满的专用箱子类型
    private Set<Integer> processedSourceChests = new HashSet<>(); // 已处理的源箱子

    // 统计信息
    private int totalBooksSorted = 0;
    private int totalBooksToMisc = 0;
    private Map<String, Integer> sortedBookStats = new HashMap<>(); // 各类型附魔书分类统计

    public static EnchantBookSorterConfig config = PLUGIN_CONFIG.enchantBookSorter;

    @Override
    public boolean enabledSetting() {
        return config.enabled;
    }

    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::onTick),
                of(ClientBotTick.Stopped.class, e -> reset())
        );
    }

    @Override
    public void onEnable() {
        config.init();
        initializeEnchantmentMapping();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        state = State.SCAN_SOURCE_CHESTS;
        currentSourceChestIndex = 0;
        currentMiscChestIndex = 0;
        enchantBooksToSort.clear();
        currentProcessingBook = null;
        currentEnchantmentType = "";
        processedSourceChests.clear();
        fullEnchantmentChests.clear();
        totalBooksSorted = 0;
        totalBooksToMisc = 0;
        sortedBookStats.clear();

        pathingFuture = PathingRequestFuture.rejected;
        inventoryActionFuture = RequestFuture.rejected;
        actionDelayTimer.reset();
        interactTimer.reset();
    }

    private void initializeEnchantmentMapping() {
        // 清空已满箱子记录
        fullEnchantmentChests.clear();
    }

    private void onTick(ClientBotTick event) {
        switch (state) {
            case SCAN_SOURCE_CHESTS -> {
                if (pathingFuture.isCompleted() && inventoryActionFuture.isCompleted()) {
                    if (currentSourceChestIndex >= config.sourceChests.size()) {
                        getPlayerInventoryBook();
                        // 所有源箱子都已扫描完毕
                        if (enchantBooksToSort.isEmpty()) {
                            info("附魔书分类完成！统计：总计分类 " + totalBooksSorted + " 本书，其中 " + totalBooksToMisc + " 本放入杂物箱");
                            printSortingStats();
                            setState(State.COMPLETED);
                            return;
                        } else {
                            // 开始处理收集到的附魔书
                            setState(State.PROCESS_ENCHANT_BOOKS);
                            return;
                        }
                    }

                    // 检查当前箱子是否已处理过
                    if (processedSourceChests.contains(currentSourceChestIndex)) {
                        currentSourceChestIndex++;
                        return;
                    }

                    openSourceChest(currentSourceChestIndex);
                    setState(State.OPEN_SOURCE_CHEST);
                }
            }

            case OPEN_SOURCE_CHEST -> {
                if (pathingFuture.isCompleted()) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() != 0) {
                        setState(State.COLLECT_ENCHANT_BOOKS);
                    } else {
                        if (interactTimer.tick(40)) {
                            warn("无法打开源箱子 " + (currentSourceChestIndex + 1) + "，跳过");
                            currentSourceChestIndex++;
                            setState(State.SCAN_SOURCE_CHESTS);
                        }
                    }
                }
            }

            case COLLECT_ENCHANT_BOOKS -> {
                if (actionDelayTimer.tick(config.delayBetweenActions)) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    List<InventoryAction> actions = Lists.newArrayList();

                    // 收集所有附魔书
                    var collectActions = collectEnchantBooksFromChest(openContainer);
                    actions.addAll(collectActions);

                    actions.add(new CloseContainer(openContainer.getContainerId()));

                    inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                            .owner(this)
                            .actionDelayTicks(config.actionDelayTick)
                            .actions(actions)
                            .priority(PRIORITY)
                            .build());

                    setState(State.AWAIT_COLLECT_COMPLETE);
                    actionDelayTimer.reset();
                }
            }

            case AWAIT_COLLECT_COMPLETE -> {
                if (inventoryActionFuture.isCompleted()) {
                    processedSourceChests.add(currentSourceChestIndex);
                    currentSourceChestIndex++;

                    info("从箱子 " + currentSourceChestIndex + " 收集了 " + getCollectedBooksCount() + " 本附魔书");

                    // 如果背包快满了，先去分类
                    if (getPlayerInventorySpace() < 10) {
                        setState(State.PROCESS_ENCHANT_BOOKS);
                    } else {
                        setState(State.SCAN_SOURCE_CHESTS);
                    }
                }
            }

            case PROCESS_ENCHANT_BOOKS -> {
                if (pathingFuture.isCompleted() && inventoryActionFuture.isCompleted()) {
                    getPlayerInventoryBook();
                    if (enchantBooksToSort.isEmpty()) {
                        // 没有待分类的书，继续扫描源箱子
                        setState(State.SCAN_SOURCE_CHESTS);
                        return;
                    }

                    // 选择下一本要处理的附魔书
                    currentProcessingBook = enchantBooksToSort.remove(0);
                    currentEnchantmentType = identifyEnchantmentType(currentProcessingBook);

                    if (!currentEnchantmentType.equals("") && config.enchantmentChests.containsKey(currentEnchantmentType)) {
                        // 检查该类型的专用箱子是否已满
                        if (fullEnchantmentChests.contains(currentEnchantmentType)) {
                            // 专用箱子已满，放入杂物箱
                            info("专用箱子已满 " + currentEnchantmentType + "，将附魔书放入杂物箱");
                            setState(State.OPEN_MISC_CHEST);
                        } else {
                            // 有专门的箱子存放这类附魔
                            setState(State.OPEN_TARGET_CHEST);
                        }
                    } else {
                        // 没有配置专用箱子，放入杂物箱
                        info("没有配置专用箱子 " + currentEnchantmentType + "，将附魔书放入杂物箱");
                        setState(State.OPEN_MISC_CHEST);
                    }
                }
            }

            case OPEN_TARGET_CHEST -> {
                if (pathingFuture.isCompleted() && inventoryActionFuture.isCompleted()) {
                    if (!config.enchantmentChests.containsKey(currentEnchantmentType)) {
                        // 配置中没有该类型的专用箱子，放入杂物箱
                        info("没有配置专用箱子 " + currentEnchantmentType + "，将附魔书放入杂物箱");
                        setState(State.OPEN_MISC_CHEST);
                        return;
                    }

                    openTargetChest(currentEnchantmentType);
                    setState(State.AWAIT_TARGET_CHEST);
                }
            }

            case AWAIT_TARGET_CHEST -> {
                if (pathingFuture.isCompleted()) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() != 0) {
                        setState(State.DEPOSIT_TO_TARGET);
                    } else {
                        if (interactTimer.tick(40)) {
                            setState(State.OPEN_TARGET_CHEST);
                        }
                    }
                }
            }

            case DEPOSIT_TO_TARGET -> {
                var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                List<InventoryAction> actions = Lists.newArrayList();

                // 尝试存放当前处理的附魔书
                boolean deposited = depositEnchantBook(openContainer, currentProcessingBook, actions);

                actions.add(new CloseContainer(openContainer.getContainerId()));

                inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actionDelayTicks(config.actionDelayTick)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());

                setState(deposited ? State.AWAIT_DEPOSIT_TARGET : State.AWAIT_DEPOSIT_TARGET_FAILED);
                actionDelayTimer.reset();
            }

            case AWAIT_DEPOSIT_TARGET -> {
                if (inventoryActionFuture.isCompleted()) {
                    // 成功存放
                    totalBooksSorted++;
                    sortedBookStats.put(currentEnchantmentType,
                            sortedBookStats.getOrDefault(currentEnchantmentType, 0) + 1);
                    info("成功将 " + EnchantmentUtil.getChinese(currentEnchantmentType) + " 附魔书存放到专用箱子");

                    currentProcessingBook = null;
                    currentEnchantmentType = "";
                    setState(State.PROCESS_ENCHANT_BOOKS);
                }
            }

            case AWAIT_DEPOSIT_TARGET_FAILED -> {
                if (inventoryActionFuture.isCompleted()) {
                    // 存放失败，该类型的专用箱子已满
                    warn("附魔类型 " + EnchantmentUtil.getChinese(currentEnchantmentType) + " 的专用箱子已满，转移到杂物箱");
                    fullEnchantmentChests.add(currentEnchantmentType);
                    setState(State.OPEN_MISC_CHEST);
                }
            }

            case OPEN_MISC_CHEST -> {
                if (pathingFuture.isCompleted() && inventoryActionFuture.isCompleted()) {
                    if (currentMiscChestIndex >= config.miscChests.size()) {
                        warn("所有杂物箱都已满！无法继续分类");
                        setState(State.COMPLETED);
                        return;
                    }

                    openMiscChest(currentMiscChestIndex);
                    setState(State.AWAIT_MISC_CHEST);
                }
            }

            case AWAIT_MISC_CHEST -> {
                if (pathingFuture.isCompleted()) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() != 0) {
                        setState(State.DEPOSIT_TO_MISC);
                    } else {
                        if (interactTimer.tick(40)) {
                            currentMiscChestIndex++;
                            setState(State.OPEN_MISC_CHEST);
                        }
                    }
                }
            }

            case DEPOSIT_TO_MISC -> {
                var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                List<InventoryAction> actions = Lists.newArrayList();

                boolean deposited = depositEnchantBook(openContainer, currentProcessingBook, actions);

                actions.add(new CloseContainer(openContainer.getContainerId()));

                inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actionDelayTicks(config.actionDelayTick)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());

                setState(deposited ? State.AWAIT_DEPOSIT_MISC : State.AWAIT_DEPOSIT_MISC_FAILED);
                actionDelayTimer.reset();
            }

            case AWAIT_DEPOSIT_MISC -> {
                if (inventoryActionFuture.isCompleted()) {
                    totalBooksSorted++;
                    totalBooksToMisc++;
                    info("将附魔书存放到杂物箱");

                    currentProcessingBook = null;
                    currentEnchantmentType = "";
                    currentMiscChestIndex = 0;
                    setState(State.PROCESS_ENCHANT_BOOKS);
                }
            }

            case AWAIT_DEPOSIT_MISC_FAILED -> {
                if (inventoryActionFuture.isCompleted()) {
                    warn("杂物箱已满，尝试下一个");
                    currentMiscChestIndex++;
                    setState(State.OPEN_MISC_CHEST);
                }
            }

            case COMPLETED -> {
                // 分类完成，可以休息或重新开始
                if (actionDelayTimer.tick(config.restDuration)) {
                    reset();
                }
            }
        }
    }

    private void getPlayerInventoryBook() {
        enchantBooksToSort.clear();
        var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        final int containerTopInvEndIndex = container.getSize() - 36;
        for (int i = container.getSize() - 1; i >= containerTopInvEndIndex; i--) {
            ItemStack itemStack = container.getItemStack(i);
            if (itemStack != Container.EMPTY_STACK && EnchantmentUtil.isEnchantedBook(itemStack)) {
                enchantBooksToSort.add(itemStack);
            }
        }
    }

    // 辅助方法实现
    private List<InventoryAction> collectEnchantBooksFromChest(Container openContainer) {
        return InventoryActionMacros.withdraw(
                openContainer.getContainerId(),
                itemStack -> {
                    if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                        enchantBooksToSort.add(itemStack);
                        return true;
                    }
                    return false;
                },
                64 // 最多取64本
        );
    }

    private String identifyEnchantmentType(ItemStack enchantBook) {
        Map<String, Integer> enchantments = EnchantmentUtil.getEnchantmentMap(enchantBook);
        // 返回第一个附魔类型，或者根据优先级返回最重要的附魔类型
        return enchantments.keySet().stream()
                .filter(type -> EnchantmentUtil.isMaxLevel(type, enchantments.get(type)))
                .findFirst()
                .orElse("");
    }

    private boolean depositEnchantBook(Container openContainer, ItemStack book, List<InventoryAction> actions) {
        // 检查箱子是否有空间
        int emptySlots = getContainerEmptySlots(openContainer);
        if (emptySlots > 0) {
            actions.addAll(InventoryActionMacros.deposit(
                    openContainer.getContainerId(),
                    itemStack -> itemStack != Container.EMPTY_STACK &&
                            EnchantmentUtil.isEnchantedBook(itemStack) &&
                            (currentEnchantmentType.equals(identifyEnchantmentType(itemStack)) || itemStack.equals(currentProcessingBook)))
            );
            return true;
        }
        return false;
    }

    private int getContainerEmptySlots(Container container) {
        int emptySlots = 0;
        for (int i = 0; i < container.getSize() - 36; i++) { // 排除玩家背包
            if (container.getItemStack(i) == Container.EMPTY_STACK) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    private int getPlayerInventorySpace() {
        var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        int emptySlots = 0;
        final int containerTopInvEndIndex = container.getSize() - 36;
        for (int i = container.getSize() - 1; i >= containerTopInvEndIndex; i--) {
            if (container.getItemStack(i) == Container.EMPTY_STACK) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    private int getCollectedBooksCount() {
        return enchantBooksToSort.size();
    }

    private void openSourceChest(int index) {
        var chestPos = config.sourceChests.get(index);
        pathingFuture = BARITONE.rightClickBlock(chestPos.x(), chestPos.y(), chestPos.z());
        pathingFuture.addExecutedListener(f -> interactTimer.reset());
    }

    private void openTargetChest(String enchantmentType) {
        var chestPos = config.enchantmentChests.get(enchantmentType);
        pathingFuture = BARITONE.rightClickBlock(chestPos.x(), chestPos.y(), chestPos.z());
        pathingFuture.addExecutedListener(f -> interactTimer.reset());
    }

    private void openMiscChest(int index) {
        var chestPos = config.miscChests.get(index);
        pathingFuture = BARITONE.rightClickBlock(chestPos.x(), chestPos.y(), chestPos.z());
        pathingFuture.addExecutedListener(f -> interactTimer.reset());
    }

    private void printSortingStats() {
        info("=== 附魔书分类统计 ===");
        for (Map.Entry<String, Integer> entry : sortedBookStats.entrySet()) {
            info(EnchantmentUtil.getChinese(entry.getKey()) + ": " + entry.getValue() + " 本");
        }
        info("杂物箱: " + totalBooksToMisc + " 本");
        info("总计: " + totalBooksSorted + " 本");
    }

    private void setState(State newState) {
        if (config.debugMode) {
            debug("EnchantBookSorter state change: {} -> {}", state, newState);
        }
        this.state = newState;
    }

    public enum State {
        SCAN_SOURCE_CHESTS,          // 扫描源箱子
        OPEN_SOURCE_CHEST,           // 打开源箱子
        COLLECT_ENCHANT_BOOKS,       // 收集附魔书
        AWAIT_COLLECT_COMPLETE,      // 等待收集完成
        PROCESS_ENCHANT_BOOKS,       // 处理附魔书
        OPEN_TARGET_CHEST,           // 打开目标箱子（专用）
        AWAIT_TARGET_CHEST,          // 等待目标箱子打开
        DEPOSIT_TO_TARGET,           // 存放到目标箱子
        AWAIT_DEPOSIT_TARGET,        // 等待存放到目标箱子完成
        AWAIT_DEPOSIT_TARGET_FAILED, // 目标箱子存放失败
        OPEN_MISC_CHEST,             // 打开杂物箱
        AWAIT_MISC_CHEST,            // 等待杂物箱打开
        DEPOSIT_TO_MISC,             // 存放到杂物箱
        AWAIT_DEPOSIT_MISC,          // 等待存放到杂物箱完成
        AWAIT_DEPOSIT_MISC_FAILED,   // 杂物箱存放失败
        COMPLETED                    // 完成
    }
}

