package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.futa.config.AutoBrewerConfig;
import com.github.rfresh2.EventConsumer;
import com.google.common.collect.Lists;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.ClickItem;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.actions.ShiftClick;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.util.RequestFuture;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.*;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

/**
 * 自动酿造模块
 * <p>
 * 酿造台槽位布局:
 * - 槽位 0-2: 药水瓶 (3个)
 * - 槽位 3: 材料
 * - 槽位 4: 燃料 (烈焰粉)
 * <p>
 * 酿造流程:
 * 1. 从原料箱收集材料（水瓶、烈焰粉、材料）
 * 2. 打开酿造台
 * 3. 放入燃料、水瓶、材料
 * 4. 等待酿造完成（通过检测材料槽是否被消耗）
 * 5. 取出酿造好的药水
 * 6. 多步配方：重复步骤2-5
 * 7. 存放成品
 */
public class AutoBrewerModule extends BaseModule {
    public static final int PRIORITY = 800;

    private State state = State.GATHER_MATERIALS;
    private int currentSourceChestIndex = 0;
    private int currentRecipeIndex = 0;
    private int currentIngredientIndex = 0; // 当前配方中的材料步骤索引
    private boolean hasBrewedThisStep = false; // 当前步骤是否已酿造

    private PathingRequestFuture pathingFuture = PathingRequestFuture.rejected;
    private RequestFuture inventoryActionFuture = RequestFuture.rejected;
    private final Timer actionDelayTimer = Timers.tickTimer();
    private final Timer interactTimer = Timers.tickTimer();
    private final Timer brewWaitTimer = Timers.tickTimer();

    // 酿造台槽位常量
    private static final int BREWING_SLOT_POTION_0 = 0;
    private static final int BREWING_SLOT_POTION_1 = 1;
    private static final int BREWING_SLOT_POTION_2 = 2;
    private static final int BREWING_SLOT_INGREDIENT = 3;
    private static final int BREWING_SLOT_FUEL = 4;
    private static final int BREWING_STAND_TOTAL_SLOTS = 5;

    public static AutoBrewerConfig config = PLUGIN_CONFIG.autoBrewer;

    // 材料名称到物品ID的映射
    private static final Map<String, Integer> INGREDIENT_MAP = new HashMap<>();

    static {
        INGREDIENT_MAP.put("nether_wart", ItemRegistry.NETHER_WART.id());
        INGREDIENT_MAP.put("sugar", ItemRegistry.SUGAR.id());
        INGREDIENT_MAP.put("spider_eye", ItemRegistry.SPIDER_EYE.id());
        INGREDIENT_MAP.put("fermented_spider_eye", ItemRegistry.FERMENTED_SPIDER_EYE.id());
        INGREDIENT_MAP.put("ghast_tear", ItemRegistry.GHAST_TEAR.id());
        INGREDIENT_MAP.put("magma_cream", ItemRegistry.MAGMA_CREAM.id());
        INGREDIENT_MAP.put("glistering_melon_slice", ItemRegistry.GLISTERING_MELON_SLICE.id());
        INGREDIENT_MAP.put("blaze_powder", ItemRegistry.BLAZE_POWDER.id());
        INGREDIENT_MAP.put("golden_carrot", ItemRegistry.GOLDEN_CARROT.id());
        INGREDIENT_MAP.put("dragon_breath", ItemRegistry.DRAGON_BREATH.id());
        INGREDIENT_MAP.put("glowstone_dust", ItemRegistry.GLOWSTONE_DUST.id());
        INGREDIENT_MAP.put("redstone", ItemRegistry.REDSTONE.id());
        INGREDIENT_MAP.put("gunpowder", ItemRegistry.GUNPOWDER.id());
        INGREDIENT_MAP.put("phantom_membrane", ItemRegistry.PHANTOM_MEMBRANE.id());
    }

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
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        state = State.GATHER_MATERIALS;
        currentSourceChestIndex = 0;
        currentRecipeIndex = 0;
        currentIngredientIndex = 0;
        hasBrewedThisStep = false;
        pathingFuture = PathingRequestFuture.rejected;
        inventoryActionFuture = RequestFuture.rejected;
        actionDelayTimer.reset();
        interactTimer.reset();
        brewWaitTimer.reset();
    }

    // ========== 状态机主循环 ==========

    private void onTick(ClientBotTick event) {
        if (config.brewingStand.equals(BlockPos.ZERO)) return;

        switch (state) {
            case GATHER_MATERIALS -> handleGatherMaterials();
            case AWAIT_GATHER -> handleAwaitGather();
            case OPEN_BREWING_STAND -> handleOpenBrewingStand();
            case AWAIT_BREWING_STAND -> handleAwaitBrewingStand();
            case LOAD_ITEMS -> handleLoadItems();
            case AWAIT_ITEMS_LOADED -> handleAwaitItemsLoaded();
            case BREWING_WAIT -> handleBrewingWait();
            case COLLECT_POTIONS -> handleCollectPotions();
            case AWAIT_COLLECT -> handleAwaitCollect();
            case NEXT_STEP -> handleNextStep();
            case STORE_RESULT -> handleStoreResult();
            case AWAIT_STORE -> handleAwaitStore();
            case REST -> handleRest();
        }
    }

    // ========== 状态处理方法 ==========

    private void handleGatherMaterials() {
        if (pathingFuture.isCompleted() && inventoryActionFuture.isCompleted()) {
            if (currentSourceChestIndex >= config.sourceChests.size()) {
                // 已检查完所有箱子
                if (hasEnoughMaterials()) {
                    currentSourceChestIndex = 0;
                    info("材料收集完成，开始酿造");
                    setState(State.OPEN_BREWING_STAND);
                } else {
                    warn("材料不足，无法酿造");
                    currentSourceChestIndex = 0;
                    setState(State.REST);
                }
                return;
            }

            var chestPos = config.sourceChests.get(currentSourceChestIndex);
            info("打开原料箱 {} ({}, {}, {})", currentSourceChestIndex, chestPos.x(), chestPos.y(), chestPos.z());
            pathingFuture = BARITONE.rightClickBlock(chestPos.x(), chestPos.y(), chestPos.z());
            pathingFuture.addExecutedListener(f -> interactTimer.reset());
            setState(State.AWAIT_GATHER);
        }
    }

    private void handleAwaitGather() {
        if (pathingFuture.isCompleted()) {
            var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer.getContainerId() != 0) {
                // 箱子已打开，提取材料
                var openContainerId = openContainer.getContainerId();
                List<InventoryAction> actions = Lists.newArrayList();

                // 提取烈焰粉（燃料）
                actions.addAll(extractItems(openContainer, ItemRegistry.BLAZE_POWDER.id(), 1));

                // 提取水瓶（通过 POTION ID 匹配，需要是水瓶）
                // 水瓶的物品ID和药水相同，通过自定义名称或组件区分
                // 这里我们提取所有 POTION 类型的物品（用户需要确保箱子里是水瓶）
                actions.addAll(extractItems(openContainer, ItemRegistry.POTION.id(), 3));

                // 提取当前配方需要的材料
                AutoBrewerConfig.BrewRecipe recipe = getCurrentRecipe();
                if (recipe != null && currentIngredientIndex < recipe.ingredients.size()) {
                    String ingredientName = recipe.ingredients.get(currentIngredientIndex);
                    Integer ingredientId = INGREDIENT_MAP.get(ingredientName);
                    if (ingredientId != null) {
                        actions.addAll(extractItems(openContainer, ingredientId, 3));
                    }
                }

                actions.add(new CloseContainer(openContainerId));
                inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actionDelayTicks(config.actionDelayTick)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());
                setState(State.GATHER_MATERIALS);
                currentSourceChestIndex++;
                actionDelayTimer.reset();
            } else {
                if (interactTimer.tick(40)) {
                    currentSourceChestIndex++;
                    setState(State.GATHER_MATERIALS);
                }
            }
        }
    }

    private void handleOpenBrewingStand() {
        if (pathingFuture.isCompleted() && inventoryActionFuture.isCompleted()) {
            var brewingStand = config.brewingStand;
            info("打开酿造台 ({}, {}, {})", brewingStand.x(), brewingStand.y(), brewingStand.z());
            pathingFuture = BARITONE.rightClickBlock(brewingStand.x(), brewingStand.y(), brewingStand.z());
            pathingFuture.addExecutedListener(f -> interactTimer.reset());
            setState(State.AWAIT_BREWING_STAND);
        }
    }

    private void handleAwaitBrewingStand() {
        if (pathingFuture.isCompleted()) {
            var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer.getContainerId() != 0) {
                info("酿造台已打开，开始加载物品");
                setState(State.LOAD_ITEMS);
            } else {
                if (interactTimer.tick(40)) {
                    warn("打开酿造台失败，重试");
                    setState(State.OPEN_BREWING_STAND);
                }
            }
        }
    }

    private void handleLoadItems() {
        if (actionDelayTimer.tick(config.delayBetweenActions)) {
            var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer.getContainerId() == 0) {
                warn("酿造台容器已关闭");
                setState(State.OPEN_BREWING_STAND);
                return;
            }

            int containerId = openContainer.getContainerId();
            List<InventoryAction> actions = Lists.newArrayList();

            // 步骤1: 放入燃料（烈焰粉）到槽位4
            actions.addAll(placeItemInSlot(openContainer, containerId, ItemRegistry.BLAZE_POWDER.id(), BREWING_SLOT_FUEL));

            // 步骤2: 放入水瓶到槽位0,1,2
            actions.addAll(placeItemInSlot(openContainer, containerId, ItemRegistry.POTION.id(), BREWING_SLOT_POTION_0));
            actions.addAll(placeItemInSlot(openContainer, containerId, ItemRegistry.POTION.id(), BREWING_SLOT_POTION_1));
            actions.addAll(placeItemInSlot(openContainer, containerId, ItemRegistry.POTION.id(), BREWING_SLOT_POTION_2));

            // 步骤3: 放入材料到槽位3
            AutoBrewerConfig.BrewRecipe recipe = getCurrentRecipe();
            if (recipe != null && currentIngredientIndex < recipe.ingredients.size()) {
                String ingredientName = recipe.ingredients.get(currentIngredientIndex);
                Integer ingredientId = INGREDIENT_MAP.get(ingredientName);
                if (ingredientId != null) {
                    actions.addAll(placeItemInSlot(openContainer, containerId, ingredientId, BREWING_SLOT_INGREDIENT));
                }
            }

            if (!actions.isEmpty()) {
                inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actionDelayTicks(config.actionDelayTick)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());
                info("提交 {} 个加载动作", actions.size());
            }

            setState(State.AWAIT_ITEMS_LOADED);
            actionDelayTimer.reset();
        }
    }

    private void handleAwaitItemsLoaded() {
        if (inventoryActionFuture.isCompleted()) {
            info("物品加载完成，关闭酿造台并等待酿造");
            closeCurrentContainer();
            brewWaitTimer.reset();
            setState(State.BREWING_WAIT);
        }
    }

    private void handleBrewingWait() {
        // 方案1: 固定等待时间
        if (brewWaitTimer.tick(config.brewWaitTicks)) {
            info("酿造等待完成（固定时间），收集药水");
            hasBrewedThisStep = true;
            setState(State.COLLECT_POTIONS);
        }
    }

    private void handleCollectPotions() {
        if (actionDelayTimer.tick(config.delayBetweenActions)) {
            // 重新打开酿造台
            var brewingStand = config.brewingStand;
            info("重新打开酿造台收集药水");
            pathingFuture = BARITONE.rightClickBlock(brewingStand.x(), brewingStand.y(), brewingStand.z());
            pathingFuture.addExecutedListener(f -> interactTimer.reset());
            setState(State.AWAIT_COLLECT);
            actionDelayTimer.reset();
        }
    }

    private void handleAwaitCollect() {
        if (pathingFuture.isCompleted()) {
            var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer.getContainerId() != 0) {
                int containerId = openContainer.getContainerId();
                List<InventoryAction> actions = Lists.newArrayList();

                // 从槽位0,1,2取出药水
                actions.addAll(takeItemFromSlot(containerId, BREWING_SLOT_POTION_0));
                actions.addAll(takeItemFromSlot(containerId, BREWING_SLOT_POTION_1));
                actions.addAll(takeItemFromSlot(containerId, BREWING_SLOT_POTION_2));

                actions.add(new CloseContainer(containerId));
                inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actionDelayTicks(config.actionDelayTick)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());
                setState(State.NEXT_STEP);
                actionDelayTimer.reset();
            } else {
                if (interactTimer.tick(40)) {
                    warn("重新打开酿造台失败");
                    setState(State.COLLECT_POTIONS);
                }
            }
        }
    }

    private void handleNextStep() {
        if (inventoryActionFuture.isCompleted()) {
            AutoBrewerConfig.BrewRecipe recipe = getCurrentRecipe();
            if (recipe == null) {
                setState(State.STORE_RESULT);
                return;
            }

            currentIngredientIndex++;
            if (currentIngredientIndex < recipe.ingredients.size()) {
                // 还有下一步材料，重新打开酿造台继续酿造
                info("配方 '{}' 还有下一步 ({}/{}), 继续酿造",
                        recipe.name, currentIngredientIndex + 1, recipe.ingredients.size());
                hasBrewedThisStep = false;
                setState(State.OPEN_BREWING_STAND);
            } else {
                // 当前配方完成
                info("配方 '{}' 酿造完成", recipe.name);
                currentIngredientIndex = 0;
                currentRecipeIndex++;
                if (currentRecipeIndex < config.recipes.size()) {
                    // 还有下一个配方
                    info("开始下一个配方: {}", getCurrentRecipe().name);
                    setState(State.OPEN_BREWING_STAND);
                } else {
                    // 所有配方完成
                    info("所有配方酿造完成，存放成品");
                    setState(State.STORE_RESULT);
                }
            }
        }
    }

    private void handleStoreResult() {
        if (pathingFuture.isCompleted() && inventoryActionFuture.isCompleted()) {
            var resultChest = config.resultChest;
            if (resultChest.equals(BlockPos.ZERO)) {
                warn("未配置成品箱");
                reset();
                return;
            }

            info("打开成品箱 ({}, {}, {})", resultChest.x(), resultChest.y(), resultChest.z());
            pathingFuture = BARITONE.rightClickBlock(resultChest.x(), resultChest.y(), resultChest.z());
            pathingFuture.addExecutedListener(f -> interactTimer.reset());
            setState(State.AWAIT_STORE);
        }
    }

    private void handleAwaitStore() {
        if (pathingFuture.isCompleted()) {
            var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer.getContainerId() != 0) {
                int containerId = openContainer.getContainerId();
                List<InventoryAction> actions = Lists.newArrayList();

                // 存放所有药水
                actions.addAll(InventoryActionMacros.deposit(
                        containerId,
                        item -> item != null && item.getId() == ItemRegistry.POTION.id()
                ));

                actions.add(new CloseContainer(containerId));
                inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actionDelayTicks(config.actionDelayTick)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());
                setState(State.REST);
                actionDelayTimer.reset();
            } else {
                if (interactTimer.tick(40)) {
                    warn("打开成品箱失败，重试");
                    setState(State.STORE_RESULT);
                }
            }
        }
    }

    private void handleRest() {
        if (inventoryActionFuture.isCompleted()) {
            info("酿造循环完成，重置状态");
            reset();
        }
    }

    // ========== 物品操作辅助方法 ==========

    /**
     * 将指定物品从玩家背包移动到酿造台的指定槽位
     * <p>
     * 操作序列:
     * 1. 左键点击玩家背包中的物品（拾取到鼠标）
     * 2. 左键点击酿造台目标槽位（放置）
     * 3. 左键点击玩家背包空位（放下鼠标上的物品）
     */
    private List<InventoryAction> placeItemInSlot(Container openContainer, int containerId, int itemId, int targetSlot) {
        List<InventoryAction> actions = new ArrayList<>();

        // 检查目标槽位是否已有正确的物品
        var existingItem = openContainer.getItemStack(targetSlot);
        if (existingItem != null && existingItem != Container.EMPTY_STACK && existingItem.getId() == itemId) {
            return actions; // 已有正确物品，跳过
        }

        // 如果目标槽位有错误的物品，先取出
        if (existingItem != null && existingItem != Container.EMPTY_STACK) {
            actions.addAll(takeItemFromSlot(containerId, targetSlot));
        }

        // 在玩家背包中查找物品
        int playerSlot = findPlayerItemSlot(itemId);
        if (playerSlot == -1) {
            if (config.debugMode) {
                debug("玩家背包中未找到物品 {}", itemId);
            }
            return actions;
        }

        // 拾取 -> 放置 -> 清空鼠标
        actions.add(new ClickItem(containerId, playerSlot, ClickItemAction.LEFT_CLICK));
        actions.add(new ClickItem(containerId, targetSlot, ClickItemAction.LEFT_CLICK));
        actions.add(new ClickItem(containerId, playerSlot, ClickItemAction.LEFT_CLICK));

        return actions;
    }

    /**
     * 从酿造台指定槽位取出物品到玩家背包
     */
    private List<InventoryAction> takeItemFromSlot(int containerId, int slot) {
        List<InventoryAction> actions = new ArrayList<>();
        // 找到玩家背包中的空位
        int emptySlot = findPlayerEmptySlot();
        if (emptySlot == -1) {
            if (config.debugMode) {
                debug("玩家背包已满，无法取出物品");
            }
            return actions;
        }

        // 拾取槽位物品 -> 放入背包空位 -> 清空鼠标
        actions.add(new ClickItem(containerId, slot, ClickItemAction.LEFT_CLICK));
        actions.add(new ClickItem(containerId, emptySlot, ClickItemAction.LEFT_CLICK));
        actions.add(new ClickItem(containerId, emptySlot, ClickItemAction.LEFT_CLICK));

        return actions;
    }

    /**
     * 从容器中提取指定物品到玩家背包（使用ShiftClick）
     */
    private List<InventoryAction> extractItems(Container openContainer, int itemId, int maxCount) {
        List<InventoryAction> actions = new ArrayList<>();
        int count = 0;
        int containerTopEnd = openContainer.getSize() - 36;

        for (int i = 0; i < containerTopEnd; i++) {
            var item = openContainer.getItemStack(i);
            if (item == null || item == Container.EMPTY_STACK) continue;
            if (item.getId() != itemId) continue;
            actions.add(new ShiftClick(openContainer.getContainerId(), i, ShiftClickItemAction.LEFT_CLICK));
            if (++count >= maxCount) break;
        }
        return actions;
    }

    /**
     * 在玩家背包中查找指定物品的槽位
     * 玩家背包在容器中的槽位范围: [containerSize - 36, containerSize - 1]
     */
    private int findPlayerItemSlot(int itemId) {
        var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        int containerSize = container.getSize();
        int playerInvStart = containerSize - 36;

        for (int i = containerSize - 1; i >= playerInvStart; i--) {
            var item = container.getItemStack(i);
            if (item != null && item != Container.EMPTY_STACK && item.getId() == itemId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 在玩家背包中查找空槽位
     */
    private int findPlayerEmptySlot() {
        var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        int containerSize = container.getSize();
        int playerInvStart = containerSize - 36;

        for (int i = playerInvStart; i < containerSize; i++) {
            var item = container.getItemStack(i);
            if (item == null || item == Container.EMPTY_STACK) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 检查是否有足够的材料进行酿造
     */
    private boolean hasEnoughMaterials() {
        if (config.recipes.isEmpty()) return false;

        AutoBrewerConfig.BrewRecipe recipe = getCurrentRecipe();
        if (recipe == null) return false;

        // 检查是否有水瓶（至少3个）
        int potionCount = countPlayerItem(ItemRegistry.POTION.id());
        if (potionCount < 3) return false;

        // 检查是否有烈焰粉（至少1个）
        int blazePowderCount = countPlayerItem(ItemRegistry.BLAZE_POWDER.id());
        if (blazePowderCount < 1) return false;

        // 检查当前步骤的材料
        if (currentIngredientIndex < recipe.ingredients.size()) {
            String ingredientName = recipe.ingredients.get(currentIngredientIndex);
            Integer ingredientId = INGREDIENT_MAP.get(ingredientName);
            if (ingredientId != null) {
                int ingredientCount = countPlayerItem(ingredientId);
                if (ingredientCount < 1) return false;
            }
        }

        return true;
    }

    /**
     * 计算玩家背包中指定物品的数量
     */
    private int countPlayerItem(int itemId) {
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        int count = 0;
        for (int i = 9; i <= 44; i++) {
            var item = inv.get(i);
            if (item != null && item != Container.EMPTY_STACK && item.getId() == itemId) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * 获取当前配方
     */
    private AutoBrewerConfig.BrewRecipe getCurrentRecipe() {
        if (config.recipes.isEmpty() || currentRecipeIndex >= config.recipes.size()) {
            return null;
        }
        return config.recipes.get(currentRecipeIndex);
    }

    /**
     * 关闭当前容器
     */
    private boolean closeCurrentContainer() {
        try {
            Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer != null && openContainer.getContainerId() != 0) {
                info("关闭容器 ID: " + openContainer.getContainerId());
                List<InventoryAction> actions = new ArrayList<>();
                actions.add(new CloseContainer(openContainer.getContainerId()));
                inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actionDelayTicks(config.actionDelayTick)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());
                return true;
            }
        } catch (Exception e) {
            error("关闭容器失败: " + e.getMessage());
        }
        return false;
    }

    private void setState(State newState) {
        if (config.debugMode) {
            debug("AutoBrewer state: {} -> {}", state, newState);
        }
        this.state = newState;
    }

    // ========== 状态枚举 ==========

    public enum State {
        GATHER_MATERIALS,       // 收集材料
        AWAIT_GATHER,           // 等待材料收集完成
        OPEN_BREWING_STAND,     // 打开酿造台
        AWAIT_BREWING_STAND,    // 等待酿造台打开
        LOAD_ITEMS,             // 加载物品到酿造台
        AWAIT_ITEMS_LOADED,     // 等待物品加载完成
        BREWING_WAIT,           // 等待酿造完成
        COLLECT_POTIONS,        // 收集药水
        AWAIT_COLLECT,          // 等待收集完成
        NEXT_STEP,              // 检查下一步
        STORE_RESULT,           // 存放成品
        AWAIT_STORE,            // 等待存放完成
        REST                    // 休息
    }
}
