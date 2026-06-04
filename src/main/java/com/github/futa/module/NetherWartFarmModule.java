package com.github.futa.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.Globals;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.*;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.pathfinder.BlockStateInterface;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.feature.player.Input;
import com.zenith.feature.player.InputRequest;
import com.zenith.feature.player.RotationHelper;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.impl.AbstractInventoryModule;
import com.zenith.util.RequestFuture;
import com.zenith.util.math.MathHelper;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

public class NetherWartFarmModule extends AbstractInventoryModule {
    public static final int PRIORITY = 8000;
    private static final int MIN_SEEDS_STACKS = 2; // 最少保留2组种子
    private static final int SEEDS_PER_STACK = 64; // 每组种子数量

    private State state = State.SEARCH_SOUL_SAND;
    private BlockPos currentTarget;
    private BlockPos currentJumpTarget;
    private PathingRequestFuture pathingFuture = PathingRequestFuture.rejected;
    private RequestFuture inventoryActionFuture = RequestFuture.rejected;
    private final Timer actionDelayTimer = Timers.timer();
    private final Timer restTimer = Timers.tickTimer();
    private final Timer interactTimer = Timers.tickTimer();
    //    private final List<BlockPos> processedBlocks = new ArrayList<>();
    private int delay = 0;

    public NetherWartFarmModule() {
        super(HandRestriction.MAIN_HAND, 4);
    }

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.netherWartFarm.enabled;
    }

    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::onTick),
                of(ClientBotTick.Stopped.class, e -> reset())
        );
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        state = State.SEARCH_SOUL_SAND;
        currentTarget = null;
        pathingFuture = PathingRequestFuture.rejected;
        inventoryActionFuture = RequestFuture.rejected;
        actionDelayTimer.reset();
        restTimer.reset();
        interactTimer.reset();
    }

    private void onTick(ClientBotTick event) {

        switch (state) {
            case SEARCH_SOUL_SAND -> searchForSoulSand();
            case MOVE_TO_TARGET -> moveToTarget();
            case AWAIT_TARGET -> waitToTarget();
            case INTERACT_WITH_BLOCK -> interactWithBlock();
            case AWAIT_INTERACT_WITH_BLOCK -> waitInteractWithBlock();
            case OPEN_CHEST -> open();
            case WAIT_CHEST_OPEN -> waitChestOpen();
            case SUBMIT_DEPOSIT -> submitDeposit();
            case WAIT_DEPOSIT_COMPLETE -> waitDepositComplete();
            case REST -> rest();
        }
    }

    private void searchForSoulSand() {
        Vector3d playerPos = CACHE.getPlayerCache().getThePlayer().position();
        int radius = PLUGIN_CONFIG.netherWartFarm.searchRadius;

        // 分别收集空地和成熟地狱疣的位置
        List<BlockPos> emptySpots = new ArrayList<>(); // 空地（需要种植）
        List<BlockPos> matureSpots = new ArrayList<>(); // 成熟地狱疣（需要收割）

        int playerY = (int) Math.floor(playerPos.getY());
        for (int yOffset = 0; yOffset >= -1; yOffset--) { // y 和 y-1 层
            int y = playerY + yOffset;

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos blockPos = new BlockPos(
                            (int) Math.floor(playerPos.getX() + x),
                            y,
                            (int) Math.floor(playerPos.getZ() + z)
                    );

                    if (isSoulSand(blockPos)) {
                        // 检查是否有其他玩家在附近（如果启用了avoidOther）
                        if (PLUGIN_CONFIG.netherWartFarm.avoidOther && hasOtherPlayersNearby(blockPos)) {
                            if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                                info("Skipping soul sand at ({}, {}, {}) due to nearby players",
                                        blockPos.x(), blockPos.y(), blockPos.z());
                            }
                            continue;
                        }

                        BlockPos abovePos = new BlockPos(blockPos.x(), blockPos.y() + 1, blockPos.z());
                        int aboveBlockState = BlockStateInterface.getId(abovePos.x(), abovePos.y(), abovePos.z());

                        // 分别收集空地和成熟地狱疣
                        if (aboveBlockState == 0 && hasNetherWartSeeds()) {
                            emptySpots.add(blockPos);
                        } else if (isNetherWartMature(aboveBlockState)) {
                            matureSpots.add(blockPos);
                        }
                    }
                }
            }
        }

        // 根据模式决定目标选择顺序
        if (PLUGIN_CONFIG.netherWartFarm.preferredPlanting) {
            // 优先种植模式：先处理空地，没有空地才处理成熟地狱疣
            if (!emptySpots.isEmpty()) {
                // 按距离排序空地，选择最近的
                emptySpots.sort(Comparator.comparingDouble(pos -> {
                    double dx = pos.x() - playerPos.getX();
                    double dy = pos.y() - playerPos.getY();
                    double dz = pos.z() - playerPos.getZ();
                    return dx * dx + dy * dy + dz * dz;
                }));
                currentTarget = emptySpots.get(0);

                if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                    info("Preferred planting: found {} empty spots, targeting nearest at ({}, {}, {})",
                            emptySpots.size(), currentTarget.x(), currentTarget.y(), currentTarget.z());
                }
            } else if (!matureSpots.isEmpty()) {
                // 没有空地时才收割成熟地狱疣
                matureSpots.sort(Comparator.comparingDouble(pos -> {
                    double dx = pos.x() - playerPos.getX();
                    double dy = pos.y() - playerPos.getY();
                    double dz = pos.z() - playerPos.getZ();
                    return dx * dx + dy * dy + dz * dz;
                }));
                currentTarget = matureSpots.get(0);

                if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                    info("Preferred planting: no empty spots, harvesting nearest mature at ({}, {}, {})",
                            currentTarget.x(), currentTarget.y(), currentTarget.z());
                }
            } else {
                // 没有任何可处理的目标
                if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                    info("Preferred planting: no valid targets found");
                }
                setState(State.REST);
                restTimer.reset();
                return;
            }
        } else {
            // 默认模式：混合处理，按距离排序所有目标
            List<BlockPos> allTargets = new ArrayList<>();
            allTargets.addAll(emptySpots);
            allTargets.addAll(matureSpots);

            if (!allTargets.isEmpty()) {
                allTargets.sort(Comparator.comparingDouble(pos -> {
                    double dx = pos.x() - playerPos.getX();
                    double dy = pos.y() - playerPos.getY();
                    double dz = pos.z() - playerPos.getZ();
                    return dx * dx + dy * dy + dz * dz;
                }));
                currentTarget = allTargets.get(0);

                if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                    String targetType = emptySpots.contains(currentTarget) ? "empty spot" : "mature nether wart";
                    info("Default mode: targeting nearest {} at ({}, {}, {})",
                            targetType, currentTarget.x(), currentTarget.y(), currentTarget.z());
                }
            } else {
                if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                    info("Default mode: no valid targets found");
                }
                setState(State.REST);
                restTimer.reset();
                return;
            }
        }

        if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
            String targetType = PLUGIN_CONFIG.netherWartFarm.preferredPlanting &&
                    !emptySpots.isEmpty() ? "empty spot" :
                    (PLUGIN_CONFIG.netherWartFarm.preferredPlanting ? "mature nether wart" :
                            (emptySpots.contains(currentTarget) ? "empty spot" : "mature nether wart"));
            info("Targeting {} at ({}, {}, {}), distance: {}",
                    targetType, currentTarget.x(), currentTarget.y(), currentTarget.z(),
                    MathHelper.manhattanDistance3d(playerPos.getX(), playerPos.getY(), playerPos.getZ(), currentTarget.x(), currentTarget.y(), currentTarget.z()));
        }

        setState(State.MOVE_TO_TARGET);
    }

    private void moveToTarget() {
        if (currentTarget == null) {
            setState(State.SEARCH_SOUL_SAND);
            return;
        }


        info("move to " + currentTarget.toString());

        pathingFuture = BARITONE.pathTo(currentTarget.x(), currentTarget.y() + 1, currentTarget.z());

        setState(State.AWAIT_TARGET);
    }

    private void waitToTarget() {
        var player = CACHE.getPlayerCache().getThePlayer();

//        double distance = MathHelper.distance3d(currentTarget.x(), currentTarget.y(), currentTarget.z(), player.getX(), player.getY(), player.getZ());
//        if (distance <= 2 && isAirTarget()) {
//            BARITONE.stop();
//            setState(State.INTERACT_WITH_BLOCK);
//            return;
//        }
        if (hasOtherPlayersNearby(currentTarget)) {
            BARITONE.stop();
            setState(State.SEARCH_SOUL_SAND);
            return;
        }

        if (pathingFuture.isCompleted()) {
            info("move done, at " + currentTarget.toString());
            setState(State.INTERACT_WITH_BLOCK);
        } else if (interactTimer.tick(60)) {
            info("move timeout, retry to " + currentTarget.toString());
            setState(State.MOVE_TO_TARGET);
        } else {
//            jump();
        }
    }

    private void interactWithBlock() {
        if (currentTarget == null) {
            setState(State.SEARCH_SOUL_SAND);
            return;
        }

        if (shouldStoreItems()) {
            // 检查是否需要存储物品
            setState(State.OPEN_CHEST);
            return;
        }

        BlockPos abovePos = new BlockPos(currentTarget.x(), currentTarget.y() + 1, currentTarget.z());
        int aboveBlockState = BlockStateInterface.getId(abovePos.x(), abovePos.y(), abovePos.z());

        if (aboveBlockState == BlockRegistry.AIR.id()) {
            // 空地，检查是否需要种植地狱疣
            if (hasNetherWartSeeds() && switchToSeed()) {
                if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                    info("Planting nether wart at ({}, {}, {})", abovePos.x(), abovePos.y(), abovePos.z());
                }
                pathingFuture = BARITONE.rightClickBlock(abovePos.x(), abovePos.y() - 1, abovePos.z());
                setState(State.AWAIT_INTERACT_WITH_BLOCK);
            } else {
                if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                    info("Not enough nether wart seeds available (need to keep {} stacks)", MIN_SEEDS_STACKS);
                }
                setState(State.SEARCH_SOUL_SAND);
            }
        } else if (isNetherWartMature(aboveBlockState)) {

            if (switchToHoe() || interactTimer.tick(5)) {
                // 检查地狱疣是否成熟
                if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                    info("Harvesting mature nether wart at ({}, {}, {})", abovePos.x(), abovePos.y(), abovePos.z());
                }
                pathingFuture = BARITONE.breakBlock(abovePos.x(), abovePos.y(), abovePos.z(), false);
//                if (processedBlocks.contains(currentTarget)) {
//                    processedBlocks.remove(currentTarget);
//                }
                setState(State.AWAIT_INTERACT_WITH_BLOCK);

            }

        } else {
            setState(State.SEARCH_SOUL_SAND);
        }

    }

    private void waitInteractWithBlock() {
        if (pathingFuture.isCompleted()) {
            info("Interact with block done");

            // 检查刚才的操作是收割还是种植
            BlockPos abovePos = new BlockPos(currentTarget.x(), currentTarget.y() + 1, currentTarget.z());
            int aboveBlockState = BlockStateInterface.getId(abovePos.x(), abovePos.y(), abovePos.z());

            // 如果刚才是收割操作（现在是空气），并且有种子，则进行补种
            if (aboveBlockState == BlockRegistry.AIR.id() && hasNetherWartSeeds()) {
                if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                    info("Replanting nether wart after harvest at ({}, {}, {})", abovePos.x(), abovePos.y(), abovePos.z());
                }
                // 重置交互计时器，重新进行交互（这次是种植）
                setState(State.INTERACT_WITH_BLOCK);
            } else {
                // 操作完成，继续搜索下一个目标
                setState(State.SEARCH_SOUL_SAND);
            }
        } else if (interactTimer.tick(60)) {
            info("Interact with block timeout, retry");

            setState(State.INTERACT_WITH_BLOCK);
        }
    }

    private boolean isAirTarget() {
        // 检查刚才的操作是收割还是种植
        BlockPos abovePos = new BlockPos(currentTarget.x(), currentTarget.y() + 1, currentTarget.z());
        int aboveBlockState = BlockStateInterface.getId(abovePos.x(), abovePos.y(), abovePos.z());
        return aboveBlockState == BlockRegistry.AIR.id();
    }


    private void open() {
        if (PLUGIN_CONFIG.netherWartFarm.storageChest.equals(BlockPos.ZERO)) {
            if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                info("No storage chest configured, skipping storage");
            }
            setState(State.SEARCH_SOUL_SAND);
            return;
        }

        BlockPos chestPos = PLUGIN_CONFIG.netherWartFarm.storageChest;
        int blockStateId = BlockStateInterface.getId(chestPos.x(), chestPos.y(), chestPos.z());

        if (!isValidContainer(blockStateId)) {
            if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                info("Invalid container at chest position, skipping storage");
            }
            setState(State.SEARCH_SOUL_SAND);
            return;
        }

        if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
            info("Clicking chest at ({}, {}, {})", chestPos.x(), chestPos.y(), chestPos.z());
        }

        pathingFuture = BARITONE.rightClickBlock(chestPos.x(), chestPos.y(), chestPos.z());
        setState(State.WAIT_CHEST_OPEN);


    }

    private void waitChestOpen() {
        var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();

        if (openContainer.getContainerId() != 0) {
            if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                info("Chest opened successfully, container ID: {}", openContainer.getContainerId());
            }
            setState(State.SUBMIT_DEPOSIT);
        } else {
            // 等待箱子打开，如果超时则重试或放弃
            if (interactTimer.tick(60)) { // 3秒超时
                if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                    info("Chest open timeout, retrying click");
                }
                setState(State.OPEN_CHEST);
            }
        }
    }

    private void submitDeposit() {
        var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();

        if (openContainer.getContainerId() == 0) {
            if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                info("Container closed unexpectedly, returning to search");
            }
            setState(State.SEARCH_SOUL_SAND);
            return;
        }

        // 创建存储动作
        List<InventoryAction> actions = createDepositActions(openContainer.getContainerId());
        actions.add(new CloseContainer(openContainer.getContainerId()));

        if (actions.size() > 1) { // 如果有存储动作（除了关闭动作）
            if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                info("Submitting deposit actions, total actions: {}", actions.size());
            }

            inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actionDelayTicks(1)
                    .actions(actions)
                    .priority(PRIORITY)
                    .build());


            setState(State.WAIT_DEPOSIT_COMPLETE);
        } else {
            // 没有需要存储的物品，直接关闭容器
            if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                info("No items to deposit, closing container");
            }

            List<InventoryAction> closeActions = new ArrayList<>();
            closeActions.add(new CloseContainer(openContainer.getContainerId()));

            inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actionDelayTicks(1)
                    .actions(closeActions)
                    .priority(PRIORITY)
                    .build());


            setState(State.WAIT_DEPOSIT_COMPLETE);
        }
    }

    private void waitDepositComplete() {
        if (inventoryActionFuture.isCompleted()) {
            if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                info("Deposit completed, returning to search");
            }
            setState(State.SEARCH_SOUL_SAND);
        } else if (interactTimer.tick(60)) { // 3秒超时
            if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                info("Deposit timeout ");
            }
            setState(State.SEARCH_SOUL_SAND);
        }
        // 否则继续等待
    }

    private void rest() {
        if (restTimer.tick(PLUGIN_CONFIG.netherWartFarm.restDuration)) {
            setState(State.SEARCH_SOUL_SAND);
            restTimer.reset();
        }
    }

    // 检查是否有地狱疣种子
    private boolean hasNetherWartSeeds() {
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 9; i <= 44; i++) {
            var item = inv.get(i);
            if (item != null && item != Container.EMPTY_STACK && item.getId() == ItemRegistry.NETHER_WART.id()) {
                return true;
            }
        }
        return false;
    }

    // 检查是否有足够的地狱疣种子（保留至少2组）
    private boolean hasEnoughNetherWartSeeds() {
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        int totalSeeds = 0;

        for (int i = 9; i <= 44; i++) {
            var item = inv.get(i);
            if (item != null && item != Container.EMPTY_STACK && item.getId() == ItemRegistry.NETHER_WART.id()) {
                totalSeeds += item.getAmount();
            }
        }

        return totalSeeds > MIN_SEEDS_STACKS * SEEDS_PER_STACK;
    }

    // 检查是否应该存储物品
    private boolean shouldStoreItems() {
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        int emptySlots = 0;
        int totalSeeds = 0;

        // 计算空位和种子总数
        for (int i = 9; i <= 44; i++) {
            var item = inv.get(i);
            if (item == null || item == Container.EMPTY_STACK) {
                emptySlots++;
            } else if (item.getId() == ItemRegistry.NETHER_WART.id()) {
                totalSeeds += item.getAmount();
            }
        }

        // 当空位小于等于1且有足够种子保留时存货
        boolean inventoryFull = emptySlots <= 1;
        boolean hasEnoughSeeds = totalSeeds > MIN_SEEDS_STACKS * SEEDS_PER_STACK;

        if (PLUGIN_CONFIG.netherWartFarm.debugMode && inventoryFull) {
            info("Inventory nearly full (empty slots: {}), seeds: {}, should store: {}",
                    emptySlots, totalSeeds, hasEnoughSeeds);
        }

        return inventoryFull && hasEnoughSeeds;
    }

    // 创建存储动作，只存储超出保留数量的地狱疣
    private List<InventoryAction> createDepositActions(int containerId) {
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        int currentSeeds = 0;

        // 计算当前种子总数
        for (int i = 9; i <= 44; i++) {
            var item = inv.get(i);
            if (item != Container.EMPTY_STACK && item.getId() == ItemRegistry.NETHER_WART.id()) {
                currentSeeds++;
            }
        }

        int seedsToDeposit = Math.max(0, currentSeeds - MIN_SEEDS_STACKS);
        List<InventoryAction> actions = new ArrayList<>();

        if (seedsToDeposit > 0) {

            actions = InventoryActionMacros.deposit(
                    containerId,
                    item -> item != null && item.getId() == ItemRegistry.NETHER_WART.id(),
                    seedsToDeposit
            );
        }

        return actions;
    }

    public boolean switchToSeed() {
        if (isItemOnHand(ItemRegistry.NETHER_WART.id())) {
            return true;
        }
        delay = doInventoryActions();
        final boolean shouldStartUsing = getHand() != null && delay == 0;
        return shouldStartUsing;
    }

    public boolean switchToHoe() {
        if (isItemOnHand(ItemRegistry.DIAMOND_HOE.id())) {
            return true;
        }

        // 检查背包中是否有锄头
        if (!hasHoeInInventory()) {
            if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                info("No hoe found in inventory, proceeding without it");
            }
            return true;
        }

        // 有锄头但不在手上，执行切换
        if (inventoryActionFuture.isCompleted()) {
            switchToItem(ItemRegistry.DIAMOND_HOE.id(), 1);
        }
        return false;
    }

    private boolean hasHoeInInventory() {
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 9; i <= 44; i++) {
            var item = inv.get(i);
            if (item != null && item != Container.EMPTY_STACK && isHoe(item.getId())) {
                return true;
            }
        }
        return false;
    }

    public boolean isItemOnHand(int itemId) {
        ItemStack mainHandStack = Globals.CACHE.getPlayerCache().getEquipment(EquipmentSlot.MAIN_HAND);
        boolean mainHandEquipped = Objects.nonNull(mainHandStack) && itemId == (mainHandStack.getId());
        return mainHandEquipped;
    }

    public boolean switchToItem(int id, int slot) {
        List<ItemStack> inventory = Globals.CACHE.getPlayerCache().getPlayerInventory();

        for (int i = 44; i >= 9; --i) {
            ItemStack itemStack = inventory.get(i);
            if (Objects.nonNull(itemStack) && id == (itemStack.getId())) {
                List<InventoryAction> actions = new ArrayList();
                if (Globals.CACHE.getPlayerCache().getInventoryCache().getMouseStack() != Container.EMPTY_STACK) {
                    actions.add(new DropMouseStack(ClickItemAction.LEFT_CLICK));
                }

                MoveToHotbarAction actionSlot = MoveToHotbarAction.from(slot);
                actions.add(new MoveToHotbarSlot(i, actionSlot));
                if (actionSlot != MoveToHotbarAction.OFF_HAND) {
                    actions.add(new SetHeldItem(slot));
                }

                inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(actions)
                        .priority(600)
                        .build());
                return true;
            }
        }

        return false;
    }

    private void equipHoeOrHand() {
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        int hotbarSlot = -1;

        // 寻找锄头
        for (int i = 36; i <= 44; i++) {
            var item = inv.get(i);
            if (item != null && item != Container.EMPTY_STACK && isHoe(item.getId())) {
                hotbarSlot = i - 36;
                break;
            }
        }

        if (hotbarSlot != -1 && hotbarSlot != CACHE.getPlayerCache().getHeldItemSlot()) {
            List<InventoryAction> actions = new ArrayList<>();
            actions.add(new SetHeldItem(hotbarSlot));

            inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actionDelayTicks(1)
                    .actions(actions)
                    .priority(PRIORITY)
                    .build());
        }
    }

    private boolean isHoe(int itemId) {
        return itemId == ItemRegistry.WOODEN_HOE.id() ||
                itemId == ItemRegistry.STONE_HOE.id() ||
                itemId == ItemRegistry.IRON_HOE.id() ||
                itemId == ItemRegistry.GOLDEN_HOE.id() ||
                itemId == ItemRegistry.DIAMOND_HOE.id() ||
                itemId == ItemRegistry.NETHERITE_HOE.id();
    }

    private boolean isSoulSand(BlockPos blockPos) {
        int blockStateId = BlockStateInterface.getId(blockPos.x(), blockPos.y(), blockPos.z());
        return blockStateId >= BlockRegistry.SOUL_SAND.minStateId() &&
                blockStateId <= BlockRegistry.SOUL_SAND.maxStateId();
    }

    private boolean isNetherWart(int blockStateId) {
        return blockStateId >= BlockRegistry.NETHER_WART.minStateId() &&
                blockStateId <= BlockRegistry.NETHER_WART.maxStateId();
    }

    private boolean isNetherWartMature(int blockStateId) {
        // 地狱疣有3个生长阶段，3为成熟
        return blockStateId == BlockRegistry.NETHER_WART.maxStateId();
    }

    private boolean isValidContainer(int blockStateId) {
        return (blockStateId >= BlockRegistry.CHEST.minStateId() && blockStateId <= BlockRegistry.CHEST.maxStateId()) ||
                (blockStateId >= BlockRegistry.BARREL.minStateId() && blockStateId <= BlockRegistry.BARREL.maxStateId()) ||
                (blockStateId >= BlockRegistry.SHULKER_BOX.minStateId() && blockStateId <= BlockRegistry.SHULKER_BOX.maxStateId());
    }

    // 检查指定位置附近是否有其他玩家
    private boolean hasOtherPlayersNearby(BlockPos blockPos) {
        var playerList = CACHE.getEntityCache().getPlayers().values();
        var currentPlayer = CACHE.getPlayerCache().getThePlayer();

        if (playerList == null || currentPlayer == null) {
            return false;
        }

        double checkRadius = 4.0; // 检查半径

        for (var player : playerList) {
            // 跳过自己
            if (player.getEntityId() == currentPlayer.getEntityId()) {
                continue;
            }

            // 计算与目标位置的距离
            double distance = MathHelper.manhattanDistance3d(player.getX(), player.getY(), player.getZ(),
                    blockPos.x(), blockPos.y(), blockPos.z());

            if (distance <= checkRadius) {
                if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
                    info("Found nearby player at distance {} from ({}, {}, {})",
                            distance, blockPos.x(), blockPos.y(), blockPos.z());
                }
                return true;
            }
        }

        return false;
    }


    private void jump() {
        if (currentJumpTarget != null && currentJumpTarget.equals(currentTarget)) {
            return;
        }

        BlockPos target = currentTarget;
        if (target != null) {
            var rotation = RotationHelper.rotationTo(target.x(), target.y() + 1, target.z());
            INPUTS.submit(InputRequest.builder()
                    .owner(this)
                    .input(Input.builder().pressingForward(true).jumping(true).build())
                    .yaw(rotation.getX())
                    .pitch(rotation.getY())
                    .priority(1000)
                    .build());
            currentJumpTarget = target;
        }
    }

    private void setState(State newState) {
        if (PLUGIN_CONFIG.netherWartFarm.debugMode) {
            debug("State change: {} -> {}", state, newState);
        }
        interactTimer.reset();
        this.state = newState;
    }

    @Override
    public boolean itemPredicate(ItemStack itemStack) {
        //itemStack.getId() == ItemRegistry.NETHER_WART.id()
        return itemStack != null && ItemRegistry.NETHER_WART.id() == (itemStack.getId());
    }

    @Override
    public int getPriority() {
        return 1200;
    }

    public enum State {
        SEARCH_SOUL_SAND,       // 搜索灵魂沙
        MOVE_TO_TARGET,         // 移动到目标
        AWAIT_TARGET,         // 移动到目标
        INTERACT_WITH_BLOCK,    // 与方块交互
        AWAIT_INTERACT_WITH_BLOCK,    // 与方块交互
        OPEN_CHEST,          // 打开箱子
        WAIT_CHEST_OPEN,        // 等待箱子打开
        SUBMIT_DEPOSIT,         // 提交存货动作
        WAIT_DEPOSIT_COMPLETE,  // 等待存货完成
        REST                    // 休息
    }
}
