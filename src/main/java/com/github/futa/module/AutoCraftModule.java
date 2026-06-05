package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.futa.dto.RecipeBean;
import com.github.futa.util.RecipeMaterialCounter;
import com.github.rfresh2.EventConsumer;
import com.google.common.collect.Lists;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.actions.PlaceRecipe;
import com.zenith.feature.inventory.actions.ShiftClick;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.pathfinder.BlockStateInterface;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.feature.player.World;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.util.RequestFuture;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

public class AutoCraftModule extends BaseModule {
    public static final int PRIORITY = 9000; // 模块优先级
    private State state = State.GATHER_MATERIALS; // 当前状态机状态
    private int currentRecipeIndex = 0; // 当前配方索引
    private int currentSourceChestIndex = 0; // 当前原料箱子索引
    private PathingRequestFuture pathingFuture = PathingRequestFuture.rejected; // 寻路任务
    private RequestFuture inventoryActionFuture = RequestFuture.rejected; // 物品栏操作任务
    private final Timer actionDelayTimer = Timers.timer(); // 操作延迟计时器
    private final Timer restTimer = Timers.timer(); // 休息计时器
    private final Timer interactTimer = Timers.tickTimer(); // 交互超时计时器
    private final Timer resultChestTimer = Timers.tickTimer(); // 成品箱子操作超时计时器
    private RecipeBean currentRecipe; // 当前正在合成的配方
    private BlockPos currentWorkbench; // 当前使用的工作台位置

    // 容器记忆功能：记住每个容器位置存储的主要物品类型
    private final Map<BlockPos, Set<String>> containerMemory = new ConcurrentHashMap<>(); // 容器位置 -> 物品类型集合
    private List<BlockPos> prioritizedSourceChests = new ArrayList<>(); // 优先级排序的原料箱子列表

    @Override
    public boolean enabledSetting() {
        // 返回模块是否启用的配置状态
        return PLUGIN_CONFIG.autoCraft.enabled;
    }

    public List<EventConsumer<?>> registerEvents() {
        // 注册事件监听器
        return List.of(
                of(ClientBotTick.class, this::onTick), // 客户端Tick事件
                of(ClientBotTick.Stopped.class, e -> reset()) // 客户端停止事件
        );
    }

    @Override
    public void onDisable() {
        // 模块禁用时重置状态
        reset();
    }

    private void reset() {
        // 重置所有状态到初始值
        state = State.GATHER_MATERIALS;
        currentRecipeIndex = 0;
        currentSourceChestIndex = 0;
        currentRecipe = null;
        currentWorkbench = null;
        pathingFuture = PathingRequestFuture.rejected;
        inventoryActionFuture = RequestFuture.rejected;
        actionDelayTimer.reset();
        restTimer.reset();
        interactTimer.reset();
        resultChestTimer.reset();
        // 保留容器记忆，不重置
    }


    /**
     * 记忆容器中存储的物品类型
     */
    private void rememberContainerItems(BlockPos chestPos, List<String> itemTypes) {
        if (chestPos == null || itemTypes == null || itemTypes.isEmpty()) {
            return;
        }

        Set<String> rememberedItems = containerMemory.computeIfAbsent(chestPos, k -> ConcurrentHashMap.newKeySet());
        rememberedItems.addAll(itemTypes);

        debug("记住容器 ({}, {}, {}) 中的物品类型: {}", chestPos.x(), chestPos.y(), chestPos.z(), itemTypes);
    }

    /**
     * 根据需要的物品类型，获取优先访问的容器位置列表
     */
    private List<BlockPos> getPrioritizedSourceChests(List<String> neededItems) {
        List<BlockPos> prioritizedChests = new ArrayList<>();
        List<BlockPos> otherChests = new ArrayList<>();

        for (BlockPos chestPos : PLUGIN_CONFIG.autoCraft.sourceChests) {
            Set<String> rememberedItems = containerMemory.get(chestPos);

            if (rememberedItems != null && neededItems.stream().anyMatch(item ->
                    item.startsWith("#") ?
                            rememberedItems.stream().anyMatch(remembered -> RecipeMaterialCounter.getTagItems(item.replace("#", "")).contains(remembered)) :
                            rememberedItems.contains(item))) {
                prioritizedChests.add(chestPos);
                debug("容器 ({}, {}, {}) 可能包含需要的物品: {}", chestPos.x(), chestPos.y(), chestPos.z(), neededItems);
            } else {
                otherChests.add(chestPos);
            }
        }

        // 优先级容器排在前面
        prioritizedChests.addAll(otherChests);

        if (!prioritizedChests.isEmpty() && !otherChests.isEmpty()) {
            info("容器记忆优化：优先检查 {} 个可能容器的 {} 个容器", prioritizedChests.size() - otherChests.size(), otherChests.size());
        }

        return prioritizedChests;
    }

    private void onTick(ClientBotTick event) {

        // 寻找工作台 放置乱搞
        if (PLUGIN_CONFIG.autoCraft.workbench.equals(BlockPos.ZERO)) {
            return;
        }
        if (PLUGIN_CONFIG.autoCraft.workbench.squaredDistance(CACHE.getPlayerCache().getThePlayer().blockPos()) > 10000) {
            //太远了 跳过
            return;
        }

        // 主状态机逻辑处理
        final RecipeBean enabledRecipe = getEnabledRecipe();
        switch (state) {
            case GATHER_MATERIALS -> {
                // 收集材料状态：等待休息时间结束
                if (restTimer.tick(PLUGIN_CONFIG.autoCraft.restSecend * 1000)) {
                    info("Rest completed, starting material gathering");
                    restTimer.reset();

                    // 计算优先级容器列表
                    List<String> neededItems = new ArrayList<>(enabledRecipe.getIngredients().keySet());
                    prioritizedSourceChests = getPrioritizedSourceChests(neededItems);
                    currentSourceChestIndex = 0;

                    setState(State.OPEN_SOURCE_CHEST);
                }
            }
            case OPEN_SOURCE_CHEST -> {

                if (hasEnoughMaterialsForAllRecipe()) {
                    currentSourceChestIndex = 0;
                    info("Already have enough materials, proceeding to workbench");
                    setState(State.FIND_WORKBENCH); // 有足够材料，寻找工作台
                    return;
                }

                // 寻找原料箱子状态
                if (currentSourceChestIndex >= prioritizedSourceChests.size()) {
                    currentSourceChestIndex = 0;
                    if (hasEnoughMaterialsForAllRecipe()) {
                        info("Found enough materials after checking all chests, proceeding to workbench");
                        setState(State.FIND_WORKBENCH); // 有足够材料，寻找工作台
                    } else {
                        info("Insufficient materials after checking all chests, entering rest");
                        setState(State.REST); // 没有足够材料，进入休息状态
                        restTimer.reset();
                    }
                    return;
                }

                // 获取当前原料箱子位置并开始寻路
                var chestPos = prioritizedSourceChests.get(currentSourceChestIndex);
                int originalIndex = PLUGIN_CONFIG.autoCraft.sourceChests.indexOf(chestPos);
                int blockStateId = BlockStateInterface.getId(chestPos.x(), chestPos.y(), chestPos.z());
                if (isValidContainer(blockStateId)) {
                    info("open source chest {} (original: {}) at ({}, {}, {})", currentSourceChestIndex, originalIndex, chestPos.x(), chestPos.y(), chestPos.z());
                    pathingFuture = BARITONE.rightClickBlock(chestPos.x(), chestPos.y(), chestPos.z());
                    pathingFuture.addExecutedListener(f -> interactTimer.reset());
                    setState(State.AWAIT_SOURCE_CHEST);
                } else {
                    info("Invalid source chest {} (original: {}) at ({}, {}, {})", currentSourceChestIndex, originalIndex, chestPos.x(), chestPos.y(), chestPos.z());
                    currentSourceChestIndex++; // 超时，尝试下一个箱子
                    setState(State.OPEN_SOURCE_CHEST);
                }
            }
            case AWAIT_SOURCE_CHEST -> {
                // 等待原料箱子状态
                if (pathingFuture.isCompleted()) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() != 0) {
                        info("Successfully opened source chest, withdrawing materials");
                        setState(State.WITHDRAW_MATERIALS); // 成功打开箱子，进入提取状态
                    } else {
                        if (interactTimer.tick(20)) {
                            info("Failed to open source chest, trying next chest");
                            currentSourceChestIndex++; // 超时，尝试下一个箱子
                            setState(State.OPEN_SOURCE_CHEST);
                        }
                    }
                }
            }
            case WITHDRAW_MATERIALS -> {
                // 提取材料状态
                var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                List<InventoryAction> actions = Lists.newArrayList();

                // 扫描容器中的物品类型并记忆
                List<String> itemTypesInContainer = new ArrayList<>();


                int containerTopInvEndIndex = openContainer.getSize() - 36;

                for (int i = 0; i < containerTopInvEndIndex; ++i) {

                    if (openContainer.getItemStack(i) != Container.EMPTY_STACK) {
                        String itemName = ItemRegistry.REGISTRY.get(openContainer.getItemStack(i).getId()).name();
                        if (!itemTypesInContainer.contains(itemName)) {
                            itemTypesInContainer.add(itemName);
                        }
                    }
                }

                for (ItemStack item : openContainer.getContents()) {
                    //todo
                    if (item != null && item != Container.EMPTY_STACK) {
                        String itemName = ItemRegistry.REGISTRY.get(item.getId()).name();
                        if (!itemTypesInContainer.contains(itemName)) {
                            itemTypesInContainer.add(itemName);
                        }
                    }
                }

                // 记忆当前容器中的物品类型
                if (!itemTypesInContainer.isEmpty()) {
                    BlockPos currentChestPos = prioritizedSourceChests.get(currentSourceChestIndex);
                    rememberContainerItems(currentChestPos, itemTypesInContainer);
                }

                info("Withdrawing materials for recipe :{}", enabledRecipe.recipeId());
                // 为每个启用的配方检查并提取所需材料
                if (needsMaterials(enabledRecipe)) {
                    for (Map.Entry<String, Integer> entry : enabledRecipe.getIngredients().entrySet()) {
                        int neededAmount = entry.getValue() * PLUGIN_CONFIG.autoCraft.batchSize;
                        int currentAmount = 0;
                        int stackSize = 64;
                        if (entry.getKey().startsWith("#")) {
                            currentAmount = countItemByTag(entry.getKey());
                            stackSize = ItemRegistry.REGISTRY.get(RecipeMaterialCounter.getTagItems(entry.getKey()).get(0)).stackSize();
                        } else {
                            int itemId = RecipeBean.getItemIdByName(entry.getKey());
                            currentAmount = countItem(itemId);
                            stackSize = ItemRegistry.REGISTRY.get(itemId).stackSize();
                        }
                        if (currentAmount < neededAmount) {
                            int withdrawAmount = Math.max(neededAmount - currentAmount, 64);
                            int maxSlotsWithdrawn = withdrawAmount / stackSize + (withdrawAmount % stackSize > 0 ? 1 : 0);

                            actions.addAll(InventoryActionMacros.withdraw(
                                    openContainer.getContainerId(),
                                    i -> {
                                        if (i != null) {
                                            if (entry.getKey().startsWith("#")) {
                                                return RecipeMaterialCounter.getTagItems(entry.getKey()).contains(ItemRegistry.REGISTRY.get(i.getId()).name());
                                            } else {
                                                int itemId = RecipeBean.getItemIdByName(entry.getKey());
                                                return i.getId() == itemId;
                                            }
                                        }
                                        return false;
                                    },
                                    maxSlotsWithdrawn
                            ));

                        }

                    }
                }

                actions.add(new CloseContainer(openContainer.getContainerId()));
                inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actionDelayTicks(PLUGIN_CONFIG.autoCraft.actionDelayTick)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());
                info("Submitting withdraw actions for {} materials", actions.size() - 1);
                setState(State.AWAIT_WITHDRAW);
                actionDelayTimer.reset();
            }
            case AWAIT_WITHDRAW -> {
                // 等待提取完成状态
                if (inventoryActionFuture.isCompleted()) {
                    info("Material withdrawal completed, continuing to next chest");
                    currentSourceChestIndex++; // 继续下一个箱子
                    setState(State.OPEN_SOURCE_CHEST);
                }
            }
            case FIND_WORKBENCH -> {
                // 寻找工作台状态
                if (PLUGIN_CONFIG.autoCraft.workbench.equals(BlockPos.ZERO)) {
                    warn("workbenchPos position not configured");
                    restTimer.reset();
                    setState(State.REST); // 没有找到工作台，进入休息状态

                    return;
                }

                // 找到工作台，开始寻路
                currentWorkbench = PLUGIN_CONFIG.autoCraft.workbench;
                int blockStateId = BlockStateInterface.getId(currentWorkbench.x(), currentWorkbench.y(), currentWorkbench.z());
                if (isValidWorkbenchContainer(blockStateId)) {
                    info("Click workbench at ({}, {}, {})", currentWorkbench.x(), currentWorkbench.y(), currentWorkbench.z());
                    pathingFuture = BARITONE.rightClickBlock(currentWorkbench.x(), currentWorkbench.y(), currentWorkbench.z());
                    pathingFuture.addExecutedListener(f -> interactTimer.reset());
                    setState(State.AWAIT_WORKBENCH); // 找到工作台，进入等待状态
                } else {
                    info("Invalid workbench at ({}, {}, {})", currentWorkbench.x(), currentWorkbench.y(), currentWorkbench.z());
                    setState(State.FIND_WORKBENCH); // 无效的工作台
                }

            }
            case AWAIT_WORKBENCH -> {
                // 移动到工作台状态
                if (pathingFuture.isCompleted()) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() != 0) {
                        info("Successfully opened workbench, starting crafting");
                        setState(State.CRAFT_ITEMS); // 成功打开工作台，进入合成状态
                    } else {
                        if (interactTimer.tick(30)) {
                            info("Failed to open workbench, retrying");
                            setState(State.FIND_WORKBENCH); // 超时，重新寻找工作台
                        }
                    }
                }
            }
            case CRAFT_ITEMS -> {
                // 合成物品状态
                var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                List<InventoryAction> actions = Lists.newArrayList();

                // 寻找可以合成的配方
                if (!needsMaterials(enabledRecipe)) {
                    currentRecipe = enabledRecipe;
                    actions.addAll(craftRecipeActions(enabledRecipe, openContainer.getContainerId()));
                }

                actions.add(new CloseContainer(openContainer.getContainerId()));
                inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actionDelayTicks(PLUGIN_CONFIG.autoCraft.actionDelayTick)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());
                info("Submitting crafting actions for recipe {} ({} actions)", enabledRecipe.recipeId(), actions.size());
                setState(State.AWAIT_CRAFT);
                actionDelayTimer.reset();
            }
            case AWAIT_CRAFT -> {
                // 等待合成完成状态
                if (inventoryActionFuture.isCompleted()) {
                    info("Crafting completed, moving to store results");
                    setState(State.STORE_RESULTS); // 合成完成，进入存储状态
                }
            }
            case STORE_RESULTS -> {
                // 存储结果状态：开始寻路到成品箱子
                BlockPos resultChest = PLUGIN_CONFIG.autoCraft.resultChest;
                int blockStateId = BlockStateInterface.getId(resultChest.x(), resultChest.y(), resultChest.z());
                if (isValidContainer(blockStateId)) {
                    info("Click result chest at ({}, {}, {})",
                            resultChest.x(),
                            resultChest.y(),
                            resultChest.z());
                    resultChestTimer.reset(); // 重置成品箱子操作计时器
                    pathingFuture = BARITONE.rightClickBlock(
                            resultChest.x(),
                            resultChest.y(),
                            resultChest.z()
                    );
                    pathingFuture.addExecutedListener(f -> interactTimer.reset());
                    setState(State.AWAIT_RESULT_CHEST); // 找到箱子，进入等待状态
                } else {
                    info("Invalid result chest at ({}, {}, {})", resultChest.x(), resultChest.y(), resultChest.z());
                }
            }
            case AWAIT_RESULT_CHEST -> {
                if (resultChestTimer.tick(60)) { // 100 ticks timeout
                    info("Result chest operation timeout, closing container and restarting");
                    closeCurrentContainer(); // 关闭当前容器
                    setState(State.STORE_RESULTS); // 重新开始存储结果
                    resultChestTimer.reset();
                    return;
                }
                // 移动到成品箱子状态
                if (pathingFuture.isCompleted()) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    if (openContainer.getContainerId() != 0) {
                        info("Successfully opened result chest, depositing items");
                        setState(State.DEPOSIT_RESULTS); // 成功打开箱子，进入存放状态
                    } else {
                        if (interactTimer.tick(50)) {
                            info("Failed to open result chest, retrying");
                            setState(State.STORE_RESULTS); // 超时，重新尝试
                            interactTimer.reset();
                        }
                    }
                }
            }
            case DEPOSIT_RESULTS -> {
                if (resultChestTimer.tick(60)) { // 100 ticks timeout
                    info("Result chest operation timeout, closing container and restarting");
                    closeCurrentContainer(); // 关闭当前容器
                    setState(State.STORE_RESULTS); // 重新开始存储结果
                    resultChestTimer.reset();
                    return;
                }

                // 存放成品状态
                var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                List<InventoryAction> actions = Lists.newArrayList();

                actions.addAll(InventoryActionMacros.deposit(
                        openContainer.getContainerId(),
                        i -> i != null && i.getId() == currentRecipe.resultItemId()
                ));
                actions.add(new CloseContainer(openContainer.getContainerId()));
                inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actionDelayTicks(PLUGIN_CONFIG.autoCraft.actionDelayTick)
                        .actions(actions)
                        .priority(PRIORITY)
                        .build());
                info("Submitting deposit actions for {} items", currentRecipe != null ? countItem(currentRecipe.resultItemId()) : 0);
                setState(State.AWAIT_DEPOSIT);
                actionDelayTimer.reset();
            }
            case AWAIT_DEPOSIT -> {
                // 等待存放完成状态
                if (inventoryActionFuture.isCompleted()) {
                    currentRecipe = null;
                    currentRecipeIndex = 0;
                    currentSourceChestIndex = 0;
                    if (hasEnoughMaterialsForAllRecipe()) {
                        info("Deposit completed, still have materials for more crafting");
                        setState(State.FIND_WORKBENCH); // 还有材料，继续合成
                    } else {
                        info("Deposit completed, out of materials, restart");
                        setState(State.OPEN_SOURCE_CHEST); // 没有材料了，进入休息状态
                        restTimer.reset();
                    }
                } else if (resultChestTimer.tick(60)) { // 100 ticks timeout
                    info("Result chest operation timeout, closing container and restarting");
                    closeCurrentContainer(); // 关闭当前容器
                    setState(State.STORE_RESULTS); // 重新开始存储结果
                    resultChestTimer.reset();
                    return;
                }
            }
            case REST -> {
                // 休息状态：等待休息时间结束
                if (restTimer.tick(PLUGIN_CONFIG.autoCraft.restSecend * 1000)) {
                    info("Rest completed, restarting cycle");
                    restTimer.reset();
                    setState(State.GATHER_MATERIALS); // 休息结束，重新开始
                }
            }
        }
    }

    private List<InventoryAction> craftRecipeActions(RecipeBean recipe, int containerId) {
        // 生成指定配方的合成动作序列
        List<InventoryAction> actions = Lists.newArrayList();
        if (needsMaterials(recipe)) {
            // 材料不足时停止
            return actions;
        }

        // 执行最终合成
        actions.add(new PlaceRecipe(containerId, recipe.recipeId(), true));
        actions.add(new ShiftClick(containerId, 0, ShiftClickItemAction.LEFT_CLICK));

        return actions;
    }

//    private boolean canCraftRecipe(RecipeBean recipe) {
//        // 检查是否可以合成指定配方
//        // 检查最终合成材料
//        for (var material : recipe.materials()) {
//            if (countItem(material.itemId()) < material.amount()) {
//                return false; // 材料不足
//            }
//        }
//        return true; // 所有材料都足够
//    }

    private boolean needsMaterials(RecipeBean recipe) {
        // 检查是否需要更多材料来满足批量合成需求
        // 检查最终合成材料
        for (Map.Entry<String, Integer> entry : recipe.getIngredients().entrySet()) {
            Integer amount = entry.getValue();
            int neededAmount = amount * PLUGIN_CONFIG.autoCraft.batchSize;

            if (entry.getKey().startsWith("#")) {
                if (countItemByTag(entry.getKey()) < neededAmount) {
                    return true; // 需要更多材料
                }
            } else {
                int itemId = RecipeBean.getItemIdByName(entry.getKey());
                if (countItem(itemId) < neededAmount) {
                    return true; // 需要更多材料
                }
            }
        }

        return false; // 材料足够
    }

    private RecipeBean getEnabledRecipe() {

        RecipeBean recipeBean = RecipeMaterialCounter.analyzeRecipe(PLUGIN_CONFIG.autoCraft.recipe);

        return recipeBean;
    }

    private boolean hasEnoughMaterialsForAllRecipe() {
        // 检查是否有足够材料来合成任何启用的配方
        return !needsMaterials(getEnabledRecipe());
    }

    private Optional<BlockPos> findNearbyWorkbench() {
        // 在指定范围内寻找工作台
        Vector3d playerPos = CACHE.getPlayerCache().getThePlayer().position();
        int searchRadius = PLUGIN_CONFIG.autoCraft.maxDistanceFromWorkbench;

        // 在三维空间中搜索工作台
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    var blockPos = new BlockPos(
                            playerPos.getX() + x,
                            playerPos.getY() + y,
                            playerPos.getZ() + z
                    );
                    var blockState = World.getBlockStateId(blockPos);
                    if (isWorkbench(blockState)) {
                        return Optional.of(blockPos); // 找到工作台
                    }
                }
            }
        }
        return Optional.empty(); // 未找到工作台
    }

    private boolean isWorkbench(int blockStateId) {
        // 检查指定方块ID是否为工作台
        return blockStateId >= BlockRegistry.CRAFTING_TABLE.minStateId() && blockStateId <= BlockRegistry.CRAFTING_TABLE.maxStateId();
    }

    private int countItem(int id) {
        // 计算玩家物品栏中指定物品的数量
        int count = 0;
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        // 遍历主物品栏和快捷栏（索引9-44）
        for (int i = 9; i <= 44; i++) {
            ItemStack item = inv.get(i);
            if (item == Container.EMPTY_STACK) continue;
            if (item.getId() == id) {
                count += item.getAmount();
            }
        }

        return count;
    }

    private int countItemByTag(String tag) {
        String tag2 = tag;
        if (tag.startsWith("#")) {
            tag2 = tag.replace("#", "");
        }
        List<String> items = RecipeMaterialCounter.getTagItems(tag2);


        // 计算玩家物品栏中指定物品的数量
        int count = 0;
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        // 遍历主物品栏和快捷栏（索引9-44）
        for (int i = 9; i <= 44; i++) {
            ItemStack item = inv.get(i);
            if (item == Container.EMPTY_STACK) continue;
            String name = ItemRegistry.REGISTRY.get(item.getId()).name();

            if (items.contains(name)) {
                count += item.getAmount();
            }
        }

        return count;
    }

    /**
     * 验证是否为有效的容器
     */
    private boolean isValidWorkbenchContainer(int blockStateId) {

        // 检查是否为箱子、桶或其他容器方块
        return (blockStateId >= BlockRegistry.CRAFTING_TABLE.minStateId() && blockStateId <= BlockRegistry.CRAFTING_TABLE.maxStateId());
    }

    /**
     * 验证是否为有效的容器
     */
    private boolean isValidContainer(int blockStateId) {

        // 检查是否为箱子、桶或其他容器方块
        return (blockStateId >= BlockRegistry.CHEST.minStateId() && blockStateId <= BlockRegistry.CHEST.maxStateId()) ||
                (blockStateId >= BlockRegistry.BARREL.minStateId() && blockStateId <= BlockRegistry.BARREL.maxStateId()) ||
                (blockStateId >= BlockRegistry.HOPPER.minStateId() && blockStateId <= BlockRegistry.HOPPER.maxStateId()) ||
                (blockStateId >= BlockRegistry.DROPPER.minStateId() && blockStateId <= BlockRegistry.DROPPER.maxStateId()) ||
                (blockStateId >= BlockRegistry.SHULKER_BOX.minStateId() && blockStateId <= BlockRegistry.SHULKER_BOX.maxStateId()) ||
                (blockStateId >= BlockRegistry.WHITE_SHULKER_BOX.minStateId() && blockStateId <= BlockRegistry.BLACK_SHULKER_BOX.maxStateId()) ||
                (blockStateId >= BlockRegistry.DISPENSER.minStateId() && blockStateId <= BlockRegistry.DISPENSER.maxStateId());
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
                inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(actions)
                        .priority(600)
                        .build());

                return true;
            }
        } catch (Exception e) {
            error("关闭容器失败: " + e.getMessage());
        }

        return false;
    }

    private void setState(State newState) {
        // 切换状态并记录日志
        debug("State change: {} -> {}", state, newState);
        this.state = newState;
    }

    public enum State {
        // 自动合成状态机枚举
        GATHER_MATERIALS,        // 收集材料状态
        OPEN_SOURCE_CHEST,      // 寻找原料箱子状态
        AWAIT_SOURCE_CHEST,   // 移动到原料箱子状态
        WITHDRAW_MATERIALS,     // 提取材料状态
        AWAIT_WITHDRAW,         // 等待提取完成状态
        FIND_WORKBENCH,         // 寻找工作台状态
        AWAIT_WORKBENCH,      // 移动到工作台状态
        CRAFT_ITEMS,            // 合成物品状态
        AWAIT_CRAFT,            // 等待合成完成状态
        STORE_RESULTS,          // 存储结果状态
        AWAIT_RESULT_CHEST,   // 移动到成品箱子状态
        DEPOSIT_RESULTS,        // 存放成品状态
        AWAIT_DEPOSIT,          // 等待存放完成状态
        REST                    // 休息状态
    }


}
