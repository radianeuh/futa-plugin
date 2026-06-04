package com.github.futa.module;

import com.github.futa.util.InvUtil;
import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.pathfinder.BlockStateInterface;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.feature.player.Input;
import com.zenith.feature.player.InputRequest;
import com.zenith.feature.player.InputRequestFuture;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.impl.AbstractInventoryModule;
import com.zenith.module.impl.KillAura;
import com.zenith.util.RequestFuture;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

public class AutoWitherModule extends AbstractInventoryModule {
    public static final int PRIORITY = 10;

    private State state = State.WAITING;
    private BlockPos currentTarget;
    private PathingRequestFuture pathingFuture = PathingRequestFuture.rejected;
    private final Timer actionDelayTimer = Timers.tickTimer();
    private final Timer checkIntervalTimer = Timers.tickTimer();
    private final Timer placeWitherTimer = Timers.tickTimer();
    private final Timer interactTimer = Timers.tickTimer();
    private int delay = 0;
    public static int currentIndex = 0;
    public static int currentRound = 0;
    private boolean isUsing = false;
    private RequestFuture inventoryActionFuture = RequestFuture.rejected;

    public AutoWitherModule() {
        super(HandRestriction.MAIN_HAND, 3);
        // 构造函数
    }

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.autoWither.enabled;
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
        state = State.WAITING;
        currentTarget = null;
        pathingFuture = PathingRequestFuture.rejected;
        actionDelayTimer.reset();
        checkIntervalTimer.reset();
        placeWitherTimer.reset();
        // 重置轮数计数器
        currentRound = 0;
        currentIndex = 0;
    }

    private void onTick(ClientBotTick event) {
        if (AutoLoginModule.isIn3cSpawn()) {
            return;
        }

        switch (state) {
            case WAITING -> checkForWithers();
            case OPEN_CHEST -> openChest();
            case WAIT_CHEST_OPEN -> waitChestOpen();
            case WITHDRAW_ITEMS -> withdrawItems();
            case WAIT_WITHDRAW -> waitWithdraw();
            case PLACE_SOUL_SAND -> placeSoulSand();
            case AWAIT_PLACE -> awaitPlace();
            case AWAIT_WITHER -> awaitWither();
            case COOLDOWN -> cooldown();
        }
    }


    private void checkForWithers() {
        switchKillAura(true);

        if (!checkIntervalTimer.tick(PLUGIN_CONFIG.autoWither.checkInterval)) {
            return;
        }

        // 计算当前存在的凋零数量
        long witherCount = countWithers();

        if (PLUGIN_CONFIG.autoWither.debugMode) {
            info("当前凋零数量: {}, 当前轮数: {}/{}", witherCount, currentRound, PLUGIN_CONFIG.autoWither.maxWithers);
        }

        if (witherCount > 0) {
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("凋零没死完，等待");
            }
            return;
        }

        // 检查是否达到最大轮数
        if (currentRound >= PLUGIN_CONFIG.autoWither.maxWithers) {
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("已达到最大轮数 {}，强制等待", PLUGIN_CONFIG.autoWither.maxWithers);
            }
            return;
        }

        // 检查灵魂沙数量
        int soulSandCount = countSoulSand();
        if (PLUGIN_CONFIG.autoWither.debugMode) {
            info("当前灵魂沙数量: {}/{}", soulSandCount, PLUGIN_CONFIG.autoWither.minSoulSand);
        }

        if (soulSandCount < PLUGIN_CONFIG.autoWither.minSoulSand) {
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("灵魂沙不足，需要从箱子获取");
            }
            // 检查是否配置了箱子
            if (PLUGIN_CONFIG.autoWither.soulSandChest.equals(BlockPos.ZERO)) {
                if (PLUGIN_CONFIG.autoWither.debugMode) {
                    info("未配置灵魂沙箱子，跳过取物");
                }
                return;
            }
            state = State.OPEN_CHEST;
            return;
        }

        // 检查是否有有效的放置位置
        if (PLUGIN_CONFIG.autoWither.witherPositions.isEmpty()) {
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("没有配置的放置位置");
            }
            return;
        }

        // 获取下一个位置
        currentTarget = getNextPosition(currentIndex);
        if (currentTarget == null) {
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("没有有效的放置位置");
            }
            return;
        }

        // 检查目标位置是否适合放置凋灵
        if (!isLocationValid(currentTarget)) {
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("位置 {} 不适合放置凋灵", currentTarget);
            }

            return;
        }

        // 重置计时器，确保在放置之间有足够的间隔
        checkIntervalTimer.reset();
        state = State.PLACE_SOUL_SAND;
    }

    private long countWithers() {
        return CACHE.getEntityCache().getEntities().values().stream()
                .filter(entity -> entity.getEntityType() == EntityType.WITHER)
                .count();
    }

    private int countSoulSand() {
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        int count = 0;
        for (int i = 0; i < inv.size(); i++) {
            var item = inv.get(i);
            if (item != null && item.getId() == ItemRegistry.SOUL_SAND.id()) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private BlockPos getNextPosition(int index) {
        if (PLUGIN_CONFIG.autoWither.witherPositions.isEmpty()) {
            return null;
        }

        return PLUGIN_CONFIG.autoWither.witherPositions.get(index);
    }

    private boolean isLocationValid(BlockPos pos) {
        // 检查是否可以放置灵魂沙（是空气方块）
        int id = BlockStateInterface.getId(pos.x(), pos.y(), pos.z());
        return id == BlockRegistry.AIR.id() || isSoulSand(id);
    }

    private void placeSoulSand() {
        if (currentTarget == null) {
            state = State.WAITING;
            return;
        }

        switchKillAura(false);

        // 检查是否有灵魂沙
        if (!hasSoulSand()) {
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("没有灵魂沙");
            }
            state = State.WAITING;
            return;
        }

        // 切换到灵魂沙
        if (!switchToSoulSand()) {
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("无法切换到灵魂沙");
            }
            state = State.WAITING;
            return;
        }

        // 检查目标位置是否适合放置凋灵
        if (!isLocationValid(currentTarget)) {
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("位置 {} 不适合放置凋灵", currentTarget);
            }

            return;
        }
        if (isSoulSand(BlockStateInterface.getId(currentTarget.x(), currentTarget.y(), currentTarget.z()))) {
            info("位置 {} 已经是灵魂沙", currentTarget);
            placeWitherTimer.reset();
            state = State.AWAIT_PLACE;
            return;
        }


        // 放置灵魂沙（底部）
        if (PLUGIN_CONFIG.autoWither.debugMode) {
            info("放置{}灵魂沙在 {}", currentIndex, currentTarget);
        }

        pathingFuture = BARITONE.placeBlock(currentTarget.x(), currentTarget.y(), currentTarget.z(), ItemRegistry.SOUL_SAND);
        placeWitherTimer.reset();
        state = State.AWAIT_PLACE;
    }

    private void awaitPlace() {
        if (pathingFuture.isCompleted() && isSoulSand(currentTarget)) {

            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("成功放置灵魂沙");
            }

            // 先更新索引，再检查是否完成一轮
            nextPlace();

            // 如果索引从最大值回到0，说明完成了一轮
            if (currentIndex == 0) {
                info("放完一只，当前数量：{}，当前轮数：{}/{}", countWithers(), currentRound, PLUGIN_CONFIG.autoWither.maxWithers);
                // 增加轮数计数器
                currentRound++;

                if (countWithers() >= PLUGIN_CONFIG.autoWither.maxWithers) {
                    // 进入等待杀凋状态
                    info("进入等待杀凋状态");
                    state = State.WAITING;
                    placeWitherTimer.reset();
                    return;
                } else if (currentRound >= PLUGIN_CONFIG.autoWither.maxWithers) {
                    // 达到最大轮数，强制等待
                    info("已达到最大轮数 {}，强制等待", PLUGIN_CONFIG.autoWither.maxWithers);
                    placeWitherTimer.reset();
                    currentRound = 0;

                    state = State.WAITING;
                    return;
                } else {
                    // 进入等待凋灵出生状态
                    placeWitherTimer.reset();
                    state = State.AWAIT_WITHER;
                    return;
                }
            }

            // 未完成一轮，继续放置下一个
            placeWitherTimer.reset();
            state = State.PLACE_SOUL_SAND;

        } else if (placeWitherTimer.tick(40)) { // 2秒超时
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("放置超时, 重试");
            }
            placeWitherTimer.reset();
            state = State.COOLDOWN;
        }
    }

    private void awaitWither() {
        for (BlockPos position : PLUGIN_CONFIG.autoWither.witherPositions) {
            if (isSoulSand(position)) {
                return;
            }
        }
        // 等待一段时间让凋灵生成
        if (PLUGIN_CONFIG.autoWither.debugMode) {
            info("凋灵出生完成，当前数量：{}，当前轮数：{}/{}", countWithers(), currentRound, PLUGIN_CONFIG.autoWither.maxWithers);
        }

        // 确保检查间隔计时器也重置，这样可以立即开始下一次检查
        checkIntervalTimer.reset();
        state = State.COOLDOWN;
    }

    private void cooldown() {
        if (placeWitherTimer.tick(PLUGIN_CONFIG.autoWither.actionDelay)) { // 使用配置的延迟时间
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("冷却完成，准备下一次放置");
            }
            // 确保检查间隔计时器也重置，这样可以立即开始下一次检查
            checkIntervalTimer.reset();
            state = State.PLACE_SOUL_SAND;
        }
    }

    void nextPlace() {

        // 更新索引到下一个位置
        currentIndex = (currentIndex + 1);
        if (currentIndex >= PLUGIN_CONFIG.autoWither.witherPositions.size()) {
            currentIndex = 0;
        }

        // 获取下一个位置
        currentTarget = getNextPosition(currentIndex);
    }

    private boolean hasSoulSand() {

        return InvUtil.hasItem(ItemRegistry.SOUL_SAND);
    }

    private boolean switchToSoulSand() {
        if (isItemOnHand(ItemRegistry.SOUL_SAND.id())) {
            return true;
        }

        // 查找背包中的灵魂沙并切换到主手
        List<ItemStack> inventory = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 44; i >= 9; --i) {
            ItemStack itemStack = inventory.get(i);
            if (Objects.nonNull(itemStack) && ItemRegistry.SOUL_SAND.id() == (itemStack.getId())) {
                return switchToItem(ItemRegistry.SOUL_SAND.id(), 1);
            }
        }
        return false;
    }

    public boolean isItemOnHand(int itemId) {
        ItemStack mainHandStack = CACHE.getPlayerCache().getEquipment(EquipmentSlot.MAIN_HAND);
        boolean mainHandEquipped = Objects.nonNull(mainHandStack) && itemId == (mainHandStack.getId());
        return mainHandEquipped;
    }

    public boolean switchToItem(int id, int slot) {
        List<ItemStack> inventory = CACHE.getPlayerCache().getPlayerInventory();

        for (int i = 44; i >= 9; --i) {
            ItemStack itemStack = inventory.get(i);
            if (Objects.nonNull(itemStack) && id == (itemStack.getId())) {
                // 这里需要实现库存操作，简化版本
                return true;
            }
        }
        return false;
    }

    private boolean isSoulSand(int blockStateId) {
        return blockStateId >= BlockRegistry.SOUL_SAND.minStateId()
                && blockStateId <= BlockRegistry.SOUL_SAND.maxStateId();
    }

    private boolean isSoulSand(BlockPos blockPos) {
        return isSoulSand(BlockStateInterface.getId(blockPos));
    }

    public boolean switchTo() {
        delay = doInventoryActions();
        final boolean shouldStart = getHand() != null && delay == 0;
        isUsing = getHand() != null || delay != 0;
        return shouldStart;
    }


    private void openChest() {
        BlockPos chestPos = PLUGIN_CONFIG.autoWither.soulSandChest;
        int blockStateId = BlockStateInterface.getId(PLUGIN_CONFIG.autoWither.soulSandChest);

        if (!isValidContainer(blockStateId)) {
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("Invalid soul sand chest at ({}, {}, {})", chestPos.x(), chestPos.y(), chestPos.z());
            }
            state = State.WAITING;
            return;
        }

        if (PLUGIN_CONFIG.autoWither.debugMode) {
            info("Opening soul sand chest at ({}, {}, {})", chestPos.x(), chestPos.y(), chestPos.z());
        }

        pathingFuture = BARITONE.rightClickBlock(chestPos.x(), chestPos.y(), chestPos.z());
        interactTimer.reset();
        state = State.WAIT_CHEST_OPEN;
    }

    private void waitChestOpen() {
        if (pathingFuture.isCompleted()) {

            var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();

            if (openContainer.getContainerId() != 0) {
                if (PLUGIN_CONFIG.autoWither.debugMode) {
                    info("Chest opened successfully, container ID: {}", openContainer.getContainerId());
                }
                state = State.WITHDRAW_ITEMS;
            } else if (interactTimer.tick(60)) { // 3秒超时
                if (PLUGIN_CONFIG.autoWither.debugMode) {
                    info("Chest open timeout, retrying");
                }
                state = State.OPEN_CHEST;
            }
        }
    }

    private void withdrawItems() {
        var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();

        if (openContainer.getContainerId() == 0) {
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("Container closed unexpectedly, returning to wait");
            }
            state = State.WAITING;
            return;
        }

        // 计算需要获取的灵魂沙数量
        int currentCount = countSoulSand();
        int neededCount = PLUGIN_CONFIG.autoWither.minSoulSand - currentCount;
        if (neededCount <= 0) {
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("Already have enough soul sand, closing container");
            }
            List<InventoryAction> closeActions = new ArrayList<>();
            closeActions.add(new CloseContainer(openContainer.getContainerId()));

            inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actionDelayTicks(1)
                    .actions(closeActions)
                    .priority(PRIORITY)
                    .build());

            state = State.WAIT_WITHDRAW;
            return;
        }

        // 创建取物动作
        List<InventoryAction> actions = InventoryActionMacros.withdraw(
                openContainer.getContainerId(),
                item -> item != null && item.getId() == ItemRegistry.SOUL_SAND.id()
        );
        actions.add(new CloseContainer(openContainer.getContainerId()));

        if (PLUGIN_CONFIG.autoWither.debugMode) {
            info("Withdrawing {} soul sand", neededCount);
        }

        inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                .owner(this)
                .actionDelayTicks(3)
                .actions(actions)
                .priority(PRIORITY)
                .build());

        state = State.WAIT_WITHDRAW;
    }

    private void waitWithdraw() {
        if (inventoryActionFuture.isCompleted()) {
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("Withdraw completed, returning to wait");
            }
            state = State.WAITING;
        } else if (interactTimer.tick(60)) { // 3秒超时
            if (PLUGIN_CONFIG.autoWither.debugMode) {
                info("Withdraw timeout");
            }
            state = State.WAITING;
        }
    }

    private boolean isValidContainer(int blockStateId) {
        return (blockStateId >= BlockRegistry.CHEST.minStateId() && blockStateId <= BlockRegistry.CHEST.maxStateId()) ||
                (blockStateId >= BlockRegistry.BARREL.minStateId() && blockStateId <= BlockRegistry.BARREL.maxStateId()) ||
                (blockStateId >= BlockRegistry.SHULKER_BOX.minStateId() && blockStateId <= BlockRegistry.SHULKER_BOX.maxStateId());
    }

    @Override
    public boolean itemPredicate(ItemStack itemStack) {
        return itemStack != Container.EMPTY_STACK && itemStack.getId() == ItemRegistry.SOUL_SAND.id();

    }

    @Override
    public int getPriority() {
        return 10;
    }

    public void switchKillAura(boolean enable) {
        if (CONFIG.client.extra.killAura.enabled == enable) {
            return;
        }

        CONFIG.client.extra.killAura.enabled = enable;
        MODULE.get(KillAura.class).syncEnabledFromConfig();
    }

    public void switchSneaking(boolean enable) {

        InputRequestFuture future = INPUTS.submit(InputRequest.builder().priority(1000)
                .owner(this)
                .input(Input.builder().sneaking(enable).build())
                .build());
    }


    public enum State {
        WAITING,           // 等待检查凋零数量和灵魂沙数量
        OPEN_CHEST,        // 打开灵魂沙箱子
        WAIT_CHEST_OPEN,   // 等待箱子打开
        WITHDRAW_ITEMS,    // 从箱子取物品
        WAIT_WITHDRAW,     // 等待取物完成
        PLACE_SOUL_SAND,   // 放置灵魂沙
        AWAIT_PLACE,       // 等待放置完成
        AWAIT_WITHER,       // 等待放置完成
        COOLDOWN           // 冷却时间
    }
}
