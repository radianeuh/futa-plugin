package com.github.futa.module;

import cn.hutool.core.util.StrUtil;
import com.github.futa.BaseModule;
import com.github.futa.config.AutoEnchantConfig;
import com.github.futa.util.EnchantmentUtil;
import com.github.futa.util.RenameItem;
import com.github.rfresh2.EventConsumer;
import com.google.common.collect.Lists;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.actions.ShiftClick;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.feature.player.World;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.impl.KillAura;
import com.zenith.util.RequestFuture;
import com.zenith.util.math.MathHelper;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

public class AutoEnchantModule extends BaseModule {
    public static final int PRIORITY = 800;

    private State state = State.COLLECT_EXPERIENCE;
    private int currentEquipmentChestIndex = 0;
    private int currentBookChestIndex = 0;
    private int currentCachedChestIndex = -1;
    private int currentResultChestIndex = 0;
    private boolean needXp = false;
    private PathingRequestFuture pathingFuture = PathingRequestFuture.rejected;
    private RequestFuture inventoryActionFuture = RequestFuture.rejected;
    private final Timer actionDelayTimer = Timers.tickTimer();
    private final Timer interactTimer = Timers.tickTimer();

    private ItemStack currentItemToEnchant = null;
    private List<ItemStack> currentEnchantBook = new ArrayList<>();
    private BlockPos currentAnvil = null;

    // 附魔进度跟踪
    private Map<String, Integer> currentEquipmentEnchants = new HashMap<>();

    // 附魔书箱子缓存：记录每种附魔书所在的箱子坐标
    private Map<String, Integer> enchantBookChestCache = new ConcurrentHashMap<>();

    // 搜索状态相关
    private boolean hasSearchedAllChests = false;

    // 附魔尝试次数跟踪
    private int currentEnchantAttempts = 0;
    private ItemStack lastEnchantedItem = null;

    // KillAura 状态跟踪
    private boolean killAuraWasEnabled = false;

    // 钻石装备到装备类型的映射
    public static final Map<Integer, EquipmentType> DIAMOND_ITEM_MAP = new ConcurrentHashMap<>();

    public static AutoEnchantConfig config = PLUGIN_CONFIG.autoEnchant;

    // 子组件
    private final InventoryHelper invHelper = new InventoryHelper();
    private final EnchantResultChecker resultChecker = new EnchantResultChecker();
    private final ExperienceCollector xpCollector = new ExperienceCollector();
    private final ChestManager chestManager = new ChestManager();
    private final AnvilHelper anvilHelper = new AnvilHelper();
    private final SwordEnchantHandler swordHandler = new SwordEnchantHandler();

    static {
        DIAMOND_ITEM_MAP.put(ItemRegistry.DIAMOND_SWORD.id(), EquipmentType.SWORD);
        DIAMOND_ITEM_MAP.put(ItemRegistry.DIAMOND_PICKAXE.id(), EquipmentType.PICKAXE);
        DIAMOND_ITEM_MAP.put(ItemRegistry.DIAMOND_HELMET.id(), EquipmentType.HELMET);
        DIAMOND_ITEM_MAP.put(ItemRegistry.DIAMOND_CHESTPLATE.id(), EquipmentType.CHESTPLATE);
        DIAMOND_ITEM_MAP.put(ItemRegistry.DIAMOND_LEGGINGS.id(), EquipmentType.LEGGINGS);
        DIAMOND_ITEM_MAP.put(ItemRegistry.DIAMOND_BOOTS.id(), EquipmentType.BOOTS);
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
        config.init();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        state = State.COLLECT_EXPERIENCE;
        currentEquipmentChestIndex = 0;
        currentBookChestIndex = 0;
        currentResultChestIndex = 0;
        currentItemToEnchant = null;
        currentEnchantBook.clear();
        currentAnvil = null;
        currentEquipmentEnchants.clear();
        xpCollector.reset();
        hasSearchedAllChests = false;
        currentEnchantAttempts = 0;
        lastEnchantedItem = null;
        pathingFuture = PathingRequestFuture.rejected;
        inventoryActionFuture = RequestFuture.rejected;
        actionDelayTimer.reset();
        interactTimer.reset();
        swordHandler.reset();

        // 恢复 KillAura
        if (config.pauseKillAura && !CONFIG.client.extra.killAura.enabled) {
            switchKillAura(true);
        }
    }

    // ========== 状态处理方法 ==========

    private void onTick(ClientBotTick event) {
        switch (state) {
            case COLLECT_EXPERIENCE -> handleCollectExperience();
            case OPEN_EQUIPMENT_CHEST -> handleOpenEquipmentChest();
            case WAITING_EQUIPMENT_CHEST_OPEN -> handleWaitingEquipmentChestOpen();
            case WITHDRAW_EQUIPMENT -> handleWithdrawEquipment();
            case AWAIT_EQUIPMENT_WITHDRAW -> handleAwaitEquipmentWithdraw();
            case OPEN_ENCHANT_BOOK_CHEST -> handleOpenEnchantBookChest();
            case AWAIT_ENCHANT_BOOK_CHEST -> handleAwaitEnchantBookChest();
            case WITHDRAW_ENCHANT_BOOK -> handleWithdrawEnchantBook();
            case AWAIT_ENCHANT_BOOK_WITHDRAW -> handleAwaitEnchantBookWithdraw();
            case OPEN_ANVIL -> handleOpenAnvil();
            case AWAIT_ANVIL -> handleAwaitAnvil();
            case ENCHANT_ITEM -> handleEnchantItem();
            case AWAIT_ENCHANT -> handleAwaitEnchant();
            case STORE_RESULT -> handleStoreResult();
            case MOVE_TO_RESULT_CHEST -> handleMoveToResultChest();
            case DEPOSIT_RESULT -> handleDepositResult();
            case AWAIT_DEPOSIT -> handleAwaitDeposit();
            case REST -> handleRest();
        }
    }

    private void handleCollectExperience() {
        if (pathingFuture.isCompleted() && inventoryActionFuture.isCompleted()) {
            if (xpCollector.hasEnoughExperience()) {
                info("已收集足够的经验，开始处理装备, 等级：" + CACHE.getPlayerCache().getThePlayer().getLevel());
                xpCollector.reset();
                needXp = false;
                setState(State.OPEN_EQUIPMENT_CHEST);
            } else {
                xpCollector.collectExperience();
            }
        }
    }

    private void handleOpenEquipmentChest() {
        // 先检查玩家库存
        for (ItemStack itemStack : invHelper.getPlayerInv()) {
            if (DIAMOND_ITEM_MAP.containsKey(itemStack.getId())) {
                var equipmentType = DIAMOND_ITEM_MAP.get(itemStack.getId());
                if (resultChecker.needsMoreEnchants(itemStack, equipmentType)) {
                    currentItemToEnchant = itemStack;
                    currentEquipmentEnchants = invHelper.getEquipmentEnchantsMaxLevel(itemStack);
                    setState(State.OPEN_ENCHANT_BOOK_CHEST);
                    info("玩家库存中已找到需要附魔的装备：" + equipmentType);
                    return;
                }
            }
        }

        if (currentEquipmentChestIndex >= config.equipmentChests.size()) {
            currentEquipmentChestIndex = 0;
            setState(State.REST);
            return;
        }

        chestManager.openChest(config.equipmentChests, currentEquipmentChestIndex);
        setState(State.WAITING_EQUIPMENT_CHEST_OPEN);
    }

    private void handleWaitingEquipmentChestOpen() {
        if (pathingFuture.isCompleted()) {
            var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer.getContainerId() != 0) {
                setState(State.WITHDRAW_EQUIPMENT);
            } else {
                if (interactTimer.tick(40)) {
                    currentEquipmentChestIndex++;
                    setState(State.OPEN_EQUIPMENT_CHEST);
                }
            }
        }
    }

    private void handleWithdrawEquipment() {
        if (actionDelayTimer.tick(config.delayBetweenActions)) {
            var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            List<InventoryAction> actions = Lists.newArrayList();

            var equipmentOpt = anvilHelper.findEquipmentToEnchant(openContainer);
            if (!equipmentOpt.isEmpty()) {
                actions.addAll(equipmentOpt);
            }

            actions.add(new CloseContainer(openContainer.getContainerId()));
            inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actionDelayTicks(config.actionDelayTick)
                    .actions(actions)
                    .priority(PRIORITY)
                    .build());
            setState(State.AWAIT_EQUIPMENT_WITHDRAW);
            actionDelayTimer.reset();
        }
    }

    private void handleAwaitEquipmentWithdraw() {
        if (pathingFuture.isCompleted() && inventoryActionFuture.isCompleted()) {
            if (getCurrentEquipment() != null) {
                hasSearchedAllChests = false;
                setState(State.OPEN_ENCHANT_BOOK_CHEST);
            } else {
                currentEquipmentChestIndex++;
                setState(State.OPEN_EQUIPMENT_CHEST);
            }
        }
    }

    private void handleOpenEnchantBookChest() {
        if (pathingFuture.isCompleted() && inventoryActionFuture.isCompleted()) {
            if (hasEnoughEnchantBook()) {
                currentBookChestIndex = 0;
                currentCachedChestIndex = -1;
                hasSearchedAllChests = false;
                info("玩家库存已找到所有需要的附魔书，去铁砧");
                setState(State.OPEN_ANVIL);
                return;
            }

            if (currentBookChestIndex >= config.enchantBookChests.size()) {
                if (hasSearchedAllChests) {
                    hasSearchedAllChests = false;
                    info("已搜索完所有箱子但未找到所需附魔书，跳过附魔直接存放装备");
                    setState(State.STORE_RESULT);
                    return;
                } else {
                    hasSearchedAllChests = true;
                    currentBookChestIndex = 0;
                    currentItemToEnchant = null;
                    setState(State.OPEN_EQUIPMENT_CHEST);
                    return;
                }
            }

            // 检查缓存位置
            if (currentCachedChestIndex == -1) {
                EquipmentType equipmentType = DIAMOND_ITEM_MAP.get(getCurrentEquipment().getId());
                if (equipmentType != null) {
                    AutoEnchantConfig.EnchantStrategy strategy = getEnchantStrategy(equipmentType);
                    List<String> neededEnchants = resultChecker.getAllNeededEnchantmentsBook(strategy);
                    Integer cachedChestIndex = chestManager.findCachedChest(neededEnchants);
                    if (cachedChestIndex != null) {
                        currentBookChestIndex = cachedChestIndex;
                        currentCachedChestIndex = cachedChestIndex;
                    }
                }
            }

            chestManager.openChest(config.enchantBookChests, currentBookChestIndex);
            setState(State.AWAIT_ENCHANT_BOOK_CHEST);
        }
    }

    private void handleAwaitEnchantBookChest() {
        if (pathingFuture.isCompleted() && inventoryActionFuture.isCompleted()) {
            var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer.getContainerId() != 0) {
                setState(State.WITHDRAW_ENCHANT_BOOK);
            } else {
                if (interactTimer.tick(40)) {
                    interactTimer.reset();
                    currentBookChestIndex++;
                    setState(State.OPEN_ENCHANT_BOOK_CHEST);
                }
            }
        }
    }

    private void handleWithdrawEnchantBook() {
        if (actionDelayTimer.tick(config.delayBetweenActions)) {
            info("开始提取附魔书");
            var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            List<InventoryAction> actions = Lists.newArrayList();

            var inventoryActions = findMatchingEnchantBook(openContainer);
            if (!inventoryActions.isEmpty()) {
                actions.add(inventoryActions.get(0));
            } else {
                warn("当前箱子未找到匹配的附魔书");
            }

            actions.add(new CloseContainer(openContainer.getContainerId()));
            inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actionDelayTicks(config.actionDelayTick)
                    .actions(actions)
                    .priority(PRIORITY)
                    .build());
            setState(State.AWAIT_ENCHANT_BOOK_WITHDRAW);
            actionDelayTimer.reset();
        }
    }

    private void handleAwaitEnchantBookWithdraw() {
        if (inventoryActionFuture.isCompleted()) {
            info("提取附魔书完成");
            if (!currentEnchantBook.isEmpty()) {
                currentCachedChestIndex = -1;
                currentBookChestIndex = 0;
                setState(State.OPEN_ENCHANT_BOOK_CHEST);
                hasSearchedAllChests = false;
            } else {
                currentBookChestIndex++;
                info("没有找到附魔书，尝试下一个箱子 " + currentBookChestIndex);
                setState(State.OPEN_ENCHANT_BOOK_CHEST);
            }
        }
    }

    private void handleOpenAnvil() {
        if (currentItemToEnchant != null) {
            if (currentItemToEnchant.equals(lastEnchantedItem)) {
                currentEnchantAttempts++;
                info("装备附魔尝试次数: " + currentEnchantAttempts);
                if (currentEnchantAttempts > 10) {
                    warn("装备附魔尝试次数超过10次，进入REST状态");
                    currentEnchantAttempts = 0;
                    setState(State.REST);
                    return;
                }
            } else {
                lastEnchantedItem = currentItemToEnchant;
                currentEnchantAttempts = 1;
            }
        } else {
            lastEnchantedItem = null;
            currentEnchantAttempts = 0;
        }

        var anvilOpt = findNearbyAnvil();
        if (anvilOpt.isEmpty()) {
            warn("在 {} 内找不到铁砧", config.anvilSearchRadius);
            setState(State.REST);
            return;
        }

        if (checkLeggingEnchantments()) {
            setState(State.STORE_RESULT);
            return;
        }

        currentAnvil = anvilOpt.get();
        pathingFuture = BARITONE.rightClickBlock(currentAnvil.x(), currentAnvil.y(), currentAnvil.z());
        pathingFuture.addExecutedListener(f -> interactTimer.reset());
        setState(State.AWAIT_ANVIL);
    }

    private void handleAwaitAnvil() {
        if (pathingFuture.isCompleted()) {
            var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer.getContainerId() != 0) {
                setState(State.ENCHANT_ITEM);
            } else {
                if (interactTimer.tick(config.delayBetweenActions)) {
                    interactTimer.reset();
                    setState(State.OPEN_ANVIL);
                }
            }
        }
    }

    private void handleEnchantItem() {
        var equipmentType = DIAMOND_ITEM_MAP.get(getCurrentEquipment().getId());
        info("正在附魔 " + equipmentType);
        var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        List<InventoryAction> actions = Lists.newArrayList();

        if (!resultChecker.needsMoreEnchants(currentItemToEnchant, equipmentType)) {
            info("装备已经完美附魔 " + equipmentType);
            setState(State.STORE_RESULT);
            return;
        }

        if (!hasEnoughEnchantBook()) {
            warn("附魔书数量不足，需要7本，当前只有" + invHelper.getPlayerInvBook().size() + "本");
            setState(State.COLLECT_EXPERIENCE);
            return;
        }

        if (equipmentType == EquipmentType.SWORD) {
            swordHandler.handle(openContainer, actions);
        } else {
            anvilHelper.handleNormalEnchant(openContainer, actions, currentItemToEnchant, equipmentType);
        }

        if (!actions.isEmpty()) {
            actions.add(new CloseContainer(openContainer.getContainerId()));
            inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actionDelayTicks(config.actionDelayTick)
                    .actions(actions)
                    .priority(PRIORITY)
                    .build());
            setState(State.AWAIT_ENCHANT);
            actionDelayTimer.reset();
        } else {
            warn("无法找到装备或附魔书, 前去取");
            closeCurrentContainer();
            setState(State.OPEN_EQUIPMENT_CHEST);
        }

        if (needXp) {
            setState(State.COLLECT_EXPERIENCE);
        }
    }

    private void handleAwaitEnchant() {
        if (inventoryActionFuture.isCompleted()) {
            List<ItemStack> playerInv = invHelper.getPlayerInv();
            for (ItemStack itemStack : playerInv) {
                if (DIAMOND_ITEM_MAP.containsKey(itemStack.getId())) {
                    var itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
                    String customName = EnchantmentUtil.getCustomName(itemStack);
                    info("正在检查装备 " + itemData.name() + " " + customName + " id:" + itemStack.getId());
                    currentItemToEnchant = itemStack;
                    currentEquipmentEnchants = invHelper.getEquipmentEnchantsMaxLevel(currentItemToEnchant);

                    var equipmentType = DIAMOND_ITEM_MAP.get(itemStack.getId());
                    info("附魔完成" + equipmentType + ",当前附魔:" + EnchantmentUtil.getEnchantmentJsonItemCN(currentItemToEnchant));

                    if (resultChecker.needsMoreEnchants(currentItemToEnchant, equipmentType)) {
                        info("还没有附魔满，继续附魔" + equipmentType);
                        closeCurrentContainer();
                        setState(State.OPEN_ANVIL);
                        return;
                    }
                }
            }
            info("所有附魔完成 " + ",当前附魔:" + EnchantmentUtil.getEnchantmentJsonItemCN(currentItemToEnchant));
            swordHandler.reset();
            closeCurrentContainer();
            setState(State.STORE_RESULT);
        }
    }

    private void handleStoreResult() {
        boolean isFullyEnchanted = false;
        if (currentItemToEnchant != null) {
            EquipmentType equipmentType = DIAMOND_ITEM_MAP.get(currentItemToEnchant.getId());
            isFullyEnchanted = !resultChecker.needsMoreEnchants(currentItemToEnchant, equipmentType);
        }

        if (isFullyEnchanted) {
            if (currentResultChestIndex >= config.resultChests.size()) {
                currentResultChestIndex = 0;
            }
            info("正在打开成品箱子" + (currentResultChestIndex + 1));
            chestManager.openChest(config.resultChests, currentResultChestIndex);
        } else {
            if (config.failChest != null && config.failChest != BlockPos.ZERO) {
                info("正在打开失败箱子");
                var chestPos = config.failChest;
                pathingFuture = BARITONE.rightClickBlock(chestPos.x(), chestPos.y(), chestPos.z());
                pathingFuture.addExecutedListener(f -> interactTimer.reset());
            } else {
                warn("未配置失败箱子，使用普通结果箱子");
                if (currentResultChestIndex >= config.resultChests.size()) {
                    currentResultChestIndex = 0;
                }
                info("正在打开成品箱子" + (currentResultChestIndex + 1));
                chestManager.openChest(config.resultChests, currentResultChestIndex);
            }
        }
        setState(State.MOVE_TO_RESULT_CHEST);
    }

    private void handleMoveToResultChest() {
        if (pathingFuture.isCompleted()) {
            var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer.getContainerId() != 0) {
                setState(State.DEPOSIT_RESULT);
            } else {
                if (interactTimer.tick(config.delayBetweenActions)) {
                    currentResultChestIndex++;
                    setState(State.STORE_RESULT);
                }
            }
        }
    }

    private void handleDepositResult() {
        if (actionDelayTimer.tick(config.delayBetweenActions)) {
            var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            List<InventoryAction> actions = Lists.newArrayList();

            if (currentItemToEnchant != null) {
                EquipmentType equipmentType = DIAMOND_ITEM_MAP.get(currentItemToEnchant.getId());
                boolean isFullyEnchanted = !resultChecker.needsMoreEnchants(currentItemToEnchant, equipmentType);

                if (isFullyEnchanted) {
                    info("装备已完全附魔，存放在成品箱子" + (currentResultChestIndex + 1));
                } else {
                    info("装备未完全附魔，存放在失败箱子");
                }

                actions.addAll(InventoryActionMacros.deposit(
                        openContainer.getContainerId(),
                        i -> i.getId() == currentItemToEnchant.getId()
                ));
            }

            actions.add(new CloseContainer(openContainer.getContainerId()));
            inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actionDelayTicks(config.actionDelayTick)
                    .actions(actions)
                    .priority(PRIORITY)
                    .build());
            setState(State.AWAIT_DEPOSIT);
            actionDelayTimer.reset();
        }
    }

    private void handleAwaitDeposit() {
        if (inventoryActionFuture.isCompleted()) {
            currentItemToEnchant = null;
            currentEnchantBook.clear();
            currentAnvil = null;
            currentEquipmentChestIndex = 0;
            currentBookChestIndex = 0;
            currentEquipmentEnchants.clear();
            xpCollector.reset();
            hasSearchedAllChests = false;
            info("已附魔，已存货");
            setState(State.COLLECT_EXPERIENCE);
        }
    }

    private void handleRest() {
        setState(State.COLLECT_EXPERIENCE);
    }

    // ========== 公共辅助方法 ==========

    private int requiredXpLevel() {
        return CACHE.getPlayerCache().getThePlayer().getLevel() >= 10 ? 10 : 40;
    }

    public static AutoEnchantConfig.EnchantStrategy getEnchantStrategy(EquipmentType type) {
        return switch (type) {
            case SWORD -> config.getEquipmentStrategy(ItemRegistry.DIAMOND_SWORD.name());
            case PICKAXE -> config.getEquipmentStrategy(ItemRegistry.DIAMOND_PICKAXE.name());
            case HELMET -> config.getEquipmentStrategy(ItemRegistry.DIAMOND_HELMET.name());
            case CHESTPLATE -> config.getEquipmentStrategy(ItemRegistry.DIAMOND_CHESTPLATE.name());
            case LEGGINGS -> config.getEquipmentStrategy(ItemRegistry.DIAMOND_LEGGINGS.name());
            case BOOTS -> config.getEquipmentStrategy(ItemRegistry.DIAMOND_BOOTS.name());
        };
    }

    private void setState(State newState) {
        if (config.debugMode) {
            debug("AutoEnchant state change: {} -> {}", state, newState);
            if (!enchantBookChestCache.isEmpty()) {
                debug("当前附魔书缓存：");
                for (Map.Entry<String, Integer> entry : enchantBookChestCache.entrySet()) {
                    debug("  {}: 箱子 {}", EnchantmentUtil.getChinese(entry.getKey()), entry.getValue());
                }
            }
        }

        // KillAura 暂停/恢复逻辑
        if (config.pauseKillAura) {
            if (state == State.COLLECT_EXPERIENCE && newState != State.COLLECT_EXPERIENCE) {
                // 经验足够，开始操作，暂停 KillAura
                switchKillAura(false);
            } else if (state != State.COLLECT_EXPERIENCE && newState == State.COLLECT_EXPERIENCE) {
                // 需要收集经验，恢复 KillAura
                switchKillAura(true);
            }
        }

        this.state = newState;
    }

    /**
     * 切换 KillAura 模块状态
     */
    private void switchKillAura(boolean enable) {
        if (CONFIG.client.extra.killAura.enabled == enable) {
            return;
        }
        CONFIG.client.extra.killAura.enabled = enable;
        MODULE.get(KillAura.class).syncEnabledFromConfig();
        info("KillAura 已" + (enable ? "启用" : "禁用"));
    }

    private boolean hasEnoughEnchantBook() {
        return invHelper.hasEnoughEnchantBook(currentItemToEnchant);
    }

    private ItemStack getCurrentEquipment() {
        return invHelper.getCurrentEquipment();
    }

    private boolean checkLeggingEnchantments() {
        if (DIAMOND_ITEM_MAP.get(currentItemToEnchant.getId()) == EquipmentType.LEGGINGS) {
            Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMapItem(currentItemToEnchant);
            if (enchantmentMap.containsKey("protection")) {
                info("发现不兼容的保护腿甲，包含保护: " + enchantmentMap.get("protection"));
                return true;
            }
        }
        return false;
    }

    private List<InventoryAction> findMatchingEnchantBook(Container openContainer) {
        return anvilHelper.findMatchingEnchantBook(openContainer, currentItemToEnchant, currentEnchantBook, currentBookChestIndex, enchantBookChestCache);
    }

    private Optional<BlockPos> findNearbyAnvil() {
        Vector3d playerPos = CACHE.getPlayerCache().getThePlayer().position();
        BlockPos playerBlockPos = new BlockPos(playerPos.getX(), playerPos.getY(), playerPos.getZ());
        int searchRadius = config.anvilSearchRadius;

        for (int r = 0; r <= searchRadius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.abs(x) != r && Math.abs(z) != r) continue;
                    BlockPos checkPos = playerBlockPos.offset(x, 0, z);
                    var blockState = World.getBlockStateId(checkPos);
                    if (isAnvil(blockState)) {
                        return Optional.of(checkPos);
                    }
                }
            }
        }

        for (int dy = 1; dy <= searchRadius; dy++) {
            for (int r = 0; r <= searchRadius; r++) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (Math.abs(x) != r && Math.abs(z) != r) continue;
                        BlockPos up = playerBlockPos.offset(x, dy, z);
                        var upState = World.getBlockStateId(up);
                        if (isAnvil(upState)) {
                            return Optional.of(up);
                        }
                        BlockPos down = playerBlockPos.offset(x, -dy, z);
                        var downState = World.getBlockStateId(down);
                        if (isAnvil(downState)) {
                            return Optional.of(down);
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private boolean isAnvil(int blockStateId) {
        return (blockStateId >= BlockRegistry.ANVIL.minStateId() && blockStateId <= BlockRegistry.ANVIL.maxStateId()) ||
                (blockStateId >= BlockRegistry.CHIPPED_ANVIL.minStateId() && blockStateId <= BlockRegistry.CHIPPED_ANVIL.maxStateId()) ||
                (blockStateId >= BlockRegistry.DAMAGED_ANVIL.minStateId() && blockStateId <= BlockRegistry.DAMAGED_ANVIL.maxStateId());
    }

    public boolean closeCurrentContainer() {
        try {
            Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            if (openContainer != null && openContainer.getContainerId() != 0) {
                info("正在关闭当前容器 ID: " + openContainer.getContainerId());
                List<InventoryAction> actions = new ArrayList<>();
                actions.add(new CloseContainer(openContainer.getContainerId()));
                inventoryActionFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actionDelayTicks(config.actionDelayTick)
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

    // ========== 内部类：背包查询 ==========

    private class InventoryHelper {

        public List<ItemStack> getPlayerInv() {
            var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            List<ItemStack> inv = new ArrayList<>();
            final int containerTopInvEndIndex = container.getSize() - 36;
            for (int i = container.getSize() - 1; i >= containerTopInvEndIndex; i--) {
                ItemStack itemStack = container.getItemStack(i);
                if (itemStack == Container.EMPTY_STACK) continue;
                inv.add(itemStack);
            }
            return inv;
        }

        public List<String> getPlayerInvBook() {
            List<ItemStack> list = getPlayerInv();
            ArrayList<String> strings = new ArrayList<>();
            for (ItemStack itemStack : list) {
                if (!EnchantmentUtil.isEnchantedBook(itemStack)) continue;
                Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                for (Map.Entry<String, Integer> entry : enchantmentMap.entrySet()) {
                    if (EnchantmentUtil.isMaxLevel(entry.getKey(), entry.getValue())) {
                        strings.add(entry.getKey());
                    }
                }
            }
            return strings;
        }

        public ItemStack getCurrentEquipment() {
            for (ItemStack itemStack : getPlayerInv()) {
                EquipmentType equipmentType = DIAMOND_ITEM_MAP.get(itemStack.getId());
                if (equipmentType != null) {
                    return itemStack;
                }
            }
            return null;
        }

        public Map<String, Integer> getEquipmentEnchantsMaxLevel(ItemStack item) {
            Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMapItem(item);
            Map<String, Integer> map = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : enchantmentMap.entrySet()) {
                if (EnchantmentUtil.isMaxLevel(entry.getKey(), entry.getValue())) {
                    map.put(entry.getKey(), entry.getValue());
                }
            }
            return map;
        }

        public boolean hasEnoughEnchantBook(ItemStack currentItemToEnchant) {
            List<ItemStack> playerInv = getPlayerInv();
            Set<String> enchantments = new HashSet<>();
            ItemStack item = null;

            for (ItemStack itemStack : playerInv) {
                EquipmentType equipmentType = DIAMOND_ITEM_MAP.get(itemStack.getId());
                if (equipmentType != null) {
                    item = itemStack;
                    Map<String, Integer> equipmentEnchants = getEquipmentEnchantsMaxLevel(itemStack);
                    for (String key : equipmentEnchants.keySet()) {
                        if (getEnchantStrategy(equipmentType).enchantments.contains(key)) {
                            enchantments.add(key);
                        }
                    }
                    break;
                }
            }

            if (item == null) return false;

            for (ItemStack itemStack : playerInv) {
                if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                    Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                    for (String key : enchantmentMap.keySet()) {
                        if (getEnchantStrategy(DIAMOND_ITEM_MAP.get(item.getId())).enchantments.contains(key)) {
                            enchantments.add(key);
                        }
                    }
                }
            }

            return enchantments.size() >= getEnchantStrategy(DIAMOND_ITEM_MAP.get(item.getId())).enchantments.size();
        }
    }

    // ========== 内部类：附魔结果判断 ==========

    private class EnchantResultChecker {

        public boolean needsMoreEnchants(ItemStack equipment, EquipmentType type) {
            AutoEnchantConfig.EnchantStrategy strategy = getEnchantStrategy(type);
            if (!strategy.enabled || strategy.enchantments.isEmpty()) {
                return false;
            }

            Map<String, Integer> currentEnchants = invHelper.getEquipmentEnchantsMaxLevel(equipment);
            for (String desiredEnchant : strategy.enchantments) {
                if (!currentEnchants.containsKey(desiredEnchant)) {
                    return true;
                }
                Integer currentLevel = currentEnchants.get(desiredEnchant);
                Integer maxLevel = EnchantmentUtil.getMaxLevel(desiredEnchant);
                if (maxLevel != null && currentLevel < maxLevel) {
                    return true;
                }
            }
            return false;
        }

        public String getNextNeededEnchantment() {
            AutoEnchantConfig.EnchantStrategy strategy = getEnchantStrategy(DIAMOND_ITEM_MAP.get(currentItemToEnchant.getId()));
            if (!strategy.enabled || strategy.enchantments.isEmpty()) {
                return null;
            }

            Map<String, Integer> currentEnchants = invHelper.getEquipmentEnchantsMaxLevel(currentItemToEnchant);
            for (String desiredEnchant : strategy.enchantments) {
                if (!currentEnchants.containsKey(desiredEnchant)) {
                    return desiredEnchant;
                }
                Integer currentLevel = currentEnchants.get(desiredEnchant);
                Integer maxLevel = EnchantmentUtil.getMaxLevel(desiredEnchant);
                if (maxLevel != null && currentLevel < maxLevel) {
                    return desiredEnchant;
                }
            }
            return "";
        }

        public List<String> getAllNeededEnchantmentsBook(AutoEnchantConfig.EnchantStrategy strategy) {
            List<String> neededEnchants = new ArrayList<>();
            if (!strategy.enabled || strategy.enchantments.isEmpty()) {
                return neededEnchants;
            }

            List<String> playerInvBook = invHelper.getPlayerInvBook();
            Map<String, Integer> equipmentEnchants = invHelper.getEquipmentEnchantsMaxLevel(currentItemToEnchant);

            for (String desiredEnchant : strategy.enchantments) {
                if (playerInvBook.contains(desiredEnchant)) continue;
                if (equipmentEnchants.containsKey(desiredEnchant)) continue;
                neededEnchants.add(desiredEnchant);
            }
            return neededEnchants;
        }
    }

    // ========== 内部类：经验收集 ==========

    private class ExperienceCollector {
        private int recordedLevel = -1;

        public boolean hasEnoughExperience() {
            return CACHE.getPlayerCache().getThePlayer().getLevel() >= 10;
        }

        public void collectExperience() {
            if (config.xpFarmPos == BlockPos.ZERO) return;

            var pos = config.xpFarmPos;
            var player = CACHE.getPlayerCache().getThePlayer();
            int currentLevel = player.getLevel();
            double distance = MathHelper.distance3d(pos.x(), pos.y(), pos.z(), player.getX(), player.getY(), player.getZ());

            if (distance < 1.4) {
                if (recordedLevel == -1) {
                    recordedLevel = currentLevel;
                    info("开始收集经验，当前等级：" + currentLevel + "，目标等级：" + 10);
                } else if (currentLevel != recordedLevel) {
                    info("等级变化：" + recordedLevel + " -> " + currentLevel);
                    recordedLevel = currentLevel;
                }
                return;
            }

            if (pathingFuture == null || pathingFuture.isDone()) {
                info("正在前往 xpfarm，距离：" + distance + "，当前等级：" + currentLevel);
                pathingFuture = BARITONE.pathTo(pos.x(), pos.y(), pos.z());
            }
        }

        public void reset() {
            recordedLevel = -1;
        }
    }

    // ========== 内部类：箱子操作 ==========

    private class ChestManager {
        private final Map<String, Integer> bookCache = new ConcurrentHashMap<>();

        public void openChest(List<BlockPos> chests, int index) {
            var chestPos = chests.get(index);
            pathingFuture = BARITONE.rightClickBlock(chestPos.x(), chestPos.y(), chestPos.z());
            pathingFuture.addExecutedListener(f -> interactTimer.reset());
        }

        public Integer findCachedChest(List<String> neededEnchants) {
            for (String neededEnchant : neededEnchants) {
                if (bookCache.containsKey(neededEnchant)) {
                    int cachedIndex = bookCache.get(neededEnchant);
                    if (cachedIndex >= 0 && cachedIndex < config.enchantBookChests.size()) {
                        info("使用缓存位置：附魔书 " + EnchantmentUtil.getChinese(neededEnchant) + " 在箱子 " + cachedIndex);
                        return cachedIndex;
                    }
                }
            }
            return null;
        }

        public void updateCache(String enchant, int chestIndex) {
            bookCache.put(enchant, chestIndex);
        }

        public void clearCache() {
            bookCache.clear();
        }
    }

    // ========== 内部类：铁砧操作辅助 ==========

    private class AnvilHelper {

        /**
         * 检查铁砧 input 槽 0 是否已有装备
         */
        public boolean isEquipmentInAnvilSlot0(Container openContainer) {
            var item = openContainer.getItemStack(0);
            return item != null && item != Container.EMPTY_STACK
                    && DIAMOND_ITEM_MAP.containsKey(item.getId());
        }

        /**
         * 获取铁砧 input 槽 0 的物品
         */
        public ItemStack getAnvilSlot0Item(Container openContainer) {
            var item = openContainer.getItemStack(0);
            if (item != null && item != Container.EMPTY_STACK) {
                return item;
            }
            return null;
        }

        /**
         * 从箱子中寻找待附魔的装备
         */
        public List<InventoryAction> findEquipmentToEnchant(Container openContainer) {
            return InventoryActionMacros.withdraw(
                    openContainer.getContainerId(),
                    itemStack -> {
                        if (itemStack == null) return false;
                        EquipmentType type = DIAMOND_ITEM_MAP.get(itemStack.getId());
                        if (type == null) return false;
                        if (!resultChecker.needsMoreEnchants(itemStack, type)) {
                            info("装备" + type + "已达到目标附魔，跳过");
                            return false;
                        }
                        info("提取:" + type);
                        currentItemToEnchant = itemStack;
                        info("包含附魔:" + EnchantmentUtil.getEnchantmentJsonItemCN(currentItemToEnchant));
                        currentEquipmentEnchants = invHelper.getEquipmentEnchantsMaxLevel(itemStack);
                        return true;
                    }, 1
            );
        }

        /**
         * 放置装备到铁砧第一个槽
         */
        public List<InventoryAction> depositEquipment(Container openContainer, ItemStack[] equipmentHolder) {
            return InventoryActionMacros.deposit(
                    openContainer.getContainerId(),
                    itemStack -> {
                        if (itemStack == null) return false;
                        EquipmentType type = DIAMOND_ITEM_MAP.get(itemStack.getId());
                        if (type == null) return false;
                        if (!resultChecker.needsMoreEnchants(itemStack, type)) {
                            info("装备" + type + "已达到目标附魔，跳过");
                            return false;
                        }
                        equipmentHolder[0] = itemStack;
                        info("准备附魔:" + type + ",当前附魔:" + EnchantmentUtil.getEnchantmentJsonItemCN(itemStack));
                        return true;
                    }, 1
            );
        }

        /**
         * 重命名装备（使用配置随机名）
         */
        public void addRenameAction(List<InventoryAction> actions, Container openContainer, ItemStack item) {
            String name = ItemRegistry.REGISTRY.get(item.getId()).name();
            String customName = EnchantmentUtil.getCustomName(item);
            if (StrUtil.isEmpty(customName)) {
                customName = config.getRandomName(name);
            }
            actions.add(new RenameItem(openContainer.getContainerId(), customName));
            info("装备重命名：" + name);
        }

        /**
         * 检查经验并设置 needXp 标志
         * @return true 表示经验不足
         */
        public boolean checkAndSetXp(int requiredLevel) {
            if (CACHE.getPlayerCache().getThePlayer().getLevel() < requiredLevel) {
                info("需要" + requiredLevel + "级经验, 当前等级:" + CACHE.getPlayerCache().getThePlayer().getLevel());
                needXp = true;
                return true;
            }
            return false;
        }

        /**
         * 找到匹配的附魔书
         */
        public List<InventoryAction> findMatchingEnchantBook(Container openContainer, ItemStack currentItem, List<ItemStack> enchantBookList, int bookChestIndex, Map<String, Integer> cache) {
            List<InventoryAction> withdrawActions = Lists.newArrayList();
            if (getCurrentEquipment() == null) return withdrawActions;

            EquipmentType equipmentType = DIAMOND_ITEM_MAP.get(getCurrentEquipment().getId());
            if (equipmentType == null) return withdrawActions;

            AutoEnchantConfig.EnchantStrategy strategy = getEnchantStrategy(equipmentType);
            if (!strategy.enabled || strategy.enchantments.isEmpty()) return withdrawActions;

            enchantBookList.clear();

            List<String> neededEnchants = resultChecker.getAllNeededEnchantmentsBook(strategy);
            if (neededEnchants.isEmpty()) {
                info("装备" + equipmentType + "已达到目标附魔配置");
                return withdrawActions;
            }

            Map<String, ItemStack> foundBooks = new HashMap<>();
            withdrawActions = InventoryActionMacros.withdraw(
                    openContainer.getContainerId(),
                    itemStack -> {
                        if (!EnchantmentUtil.isEnchantedBook(itemStack)) return false;
                        Map<String, Integer> enchantmentMap = EnchantmentUtil.getBookEnchantmentMapMaxLevel(itemStack);
                        for (String neededEnchant : neededEnchants) {
                            if (enchantmentMap.containsKey(neededEnchant) && !foundBooks.containsKey(neededEnchant)) {
                                info("提取附魔书:" + EnchantmentUtil.getChinese(neededEnchant) + enchantmentMap.get(neededEnchant));
                                foundBooks.put(neededEnchant, itemStack);
                                cache.put(neededEnchant, bookChestIndex);
                                return true;
                            }
                        }
                        return false;
                    }, 1
            );

            enchantBookList.addAll(foundBooks.values());
            return withdrawActions;
        }

        /**
         * 普通附魔处理
         */
        public void handleNormalEnchant(Container openContainer, List<InventoryAction> actions, ItemStack equipment, EquipmentType type) {
            info("普通附魔处理");

            if (!resultChecker.needsMoreEnchants(equipment, type)) {
                return;
            }

            // 检查铁砧 input 槽 0 是否已有装备
            boolean alreadyInAnvil = anvilHelper.isEquipmentInAnvilSlot0(openContainer);
            List<InventoryAction> equipActions;
            if (alreadyInAnvil) {
                info("装备已在铁砧 input 槽 0，跳过 deposit");
                equipActions = Lists.newArrayList(); // 空列表，跳过 deposit
            } else {
                ItemStack[] equipmentHolder = new ItemStack[]{equipment};
                equipActions = depositEquipment(openContainer, equipmentHolder);
                if (equipActions.isEmpty()) return;
            }

            String nextNeededEnchantment = resultChecker.getNextNeededEnchantment();
            int costForItem = EnchantmentUtil.calculateAnvilCostForItem(equipment, nextNeededEnchantment) + 1;
            if (checkAndSetXp(costForItem)) return;

            addRenameAction(actions, openContainer, equipment);

            List<InventoryAction> bookActions = InventoryActionMacros.deposit(
                    openContainer.getContainerId(),
                    itemStack -> {
                        boolean b = itemStack != null && itemStack.getId() == ItemRegistry.ENCHANTED_BOOK.id();
                        if (b && !nextNeededEnchantment.isEmpty()) {
                            Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                            if (enchantmentMap.containsKey(nextNeededEnchantment)) {
                                info("使用附魔书: " + EnchantmentUtil.getChinese(nextNeededEnchantment));
                                return true;
                            }
                        }
                        return false;
                    }, 1
            );

            if (!bookActions.isEmpty()) {
                if (!equipActions.isEmpty()) {
                    actions.addAll(equipActions);
                }
                actions.addAll(bookActions);
                actions.add(new ShiftClick(openContainer.getContainerId(), 2, ShiftClickItemAction.LEFT_CLICK));
            }
        }
    }

    // ========== 内部类：剑的特殊附魔处理 ==========

    private class SwordEnchantHandler {
        private int stage = 0;
        private boolean active = false;

        public void reset() {
            stage = 0;
            active = false;
        }

        /**
         * 根据剑的状态确定当前阶段
         */
        public int determineStage() {
            ItemStack sword = getCurrentEquipment();
            if (sword == null) return stage;

            var equipmentType = DIAMOND_ITEM_MAP.get(sword.getId());
            if (equipmentType != EquipmentType.SWORD) return stage;

            int repairCost = EnchantmentUtil.getItemRepairCost(sword);
            Map<String, Integer> swordEnchants = invHelper.getEquipmentEnchantsMaxLevel(sword);
            List<ItemStack> playerInv = invHelper.getPlayerInv();

            if (repairCost == 0 && swordEnchants.isEmpty()) {
                info("检测到剑的RepairCost为0，启动特殊附魔处理");
                return 1;
            }

            if (swordEnchants.containsKey("sweeping_edge")) stage = 2;
            if (swordEnchants.containsKey("sharpness")) stage = 4;
            if (swordEnchants.containsKey("unbreaking")) stage = 6;
            if (swordEnchants.containsKey("mending")) stage = 8;

            for (ItemStack itemStack : playerInv) {
                if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                    Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                    if (enchantmentMap.size() == 2) {
                        stage++;
                        break;
                    }
                }
            }
            return stage;
        }

        /**
         * 剑的主入口
         */
        public void handle(Container openContainer, List<InventoryAction> actions) {
            int currentStage = determineStage();
            active = currentStage > 0 && currentStage < 8;

            if (!active) return;

            switch (currentStage) {
                case 1 -> handleFirstBook(openContainer, actions);
                case 2, 4, 6 -> handleMergeStage(openContainer, actions, currentStage);
                case 3 -> handleEnchantMergedBook(openContainer, actions, 1);
                case 5 -> handleEnchantMergedBook(openContainer, actions, 3);
                case 7 -> handleEnchantMergedBook(openContainer, actions, 5);
                default -> {}
            }
            info("剑的附魔处理阶段：" + currentStage);
        }

        /**
         * 阶段1：附魔第一本书
         */
        private void handleFirstBook(Container openContainer, List<InventoryAction> actions) {
            info("附魔第一本书");

            boolean alreadyInAnvil = anvilHelper.isEquipmentInAnvilSlot0(openContainer);
            List<InventoryAction> equipActions;
            if (alreadyInAnvil) {
                info("装备已在铁砧 input 槽 0，跳过 deposit");
                equipActions = Lists.newArrayList();
                ItemStack[] swordHolder = new ItemStack[]{anvilHelper.getAnvilSlot0Item(openContainer)};
                currentItemToEnchant = swordHolder[0];
            } else {
                ItemStack[] swordHolder = new ItemStack[]{null};
                equipActions = anvilHelper.depositEquipment(openContainer, swordHolder);
                if (equipActions.isEmpty()) return;
                currentItemToEnchant = swordHolder[0];
            }

            anvilHelper.addRenameAction(actions, openContainer, currentItemToEnchant);

            AtomicReference<ItemStack> book = new AtomicReference<>();
            List<InventoryAction> bookActions = InventoryActionMacros.deposit(
                    openContainer.getContainerId(),
                    itemStack -> {
                        if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                            if (EnchantmentUtil.getEnchantmentMap(itemStack).containsKey(
                                    config.enchant.get(ItemRegistry.DIAMOND_SWORD.name()).enchantments.get(0))) {
                                info("使用第一本附魔书");
                                book.set(itemStack);
                                return true;
                            }
                        }
                        return false;
                    }, 1
            );

            int costForItem = EnchantmentUtil.calculateAnvilCostForItem(currentItemToEnchant, book.get()) + 1;
            if (anvilHelper.checkAndSetXp(costForItem)) return;

            if (!bookActions.isEmpty()) {
                if (!equipActions.isEmpty()) {
                    actions.addAll(equipActions);
                }
                actions.addAll(bookActions);
                actions.add(new ShiftClick(openContainer.getContainerId(), 2, ShiftClickItemAction.LEFT_CLICK));
            }
        }

        /**
         * 合并两本附魔书（阶段2/4/6）
         */
        private void handleMergeStage(Container openContainer, List<InventoryAction> actions, int currentStage) {
            info("正在合并附魔书，阶段：" + currentStage);

            int bookIndex1 = currentStage - 1; // 索引1/3/5
            int bookIndex2 = currentStage;     // 索引2/4/6

            AtomicReference<ItemStack> book1 = new AtomicReference<>();
            AtomicReference<ItemStack> book2 = new AtomicReference<>();

            List<InventoryAction> click1 = InventoryActionMacros.deposit(
                    openContainer.getContainerId(),
                    itemStack -> {
                        if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                            Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                            String key = config.enchant.get(ItemRegistry.DIAMOND_SWORD.name()).enchantments.get(bookIndex1);
                            if (enchantmentMap.containsKey(key) && enchantmentMap.size() == 1) {
                                info("放入 " + EnchantmentUtil.getChinese(key) + " 附魔书");
                                book1.set(itemStack);
                                return true;
                            }
                        }
                        return false;
                    }, 1
            );

            List<InventoryAction> click2 = InventoryActionMacros.deposit(
                    openContainer.getContainerId(),
                    itemStack -> {
                        if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                            Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                            String key = config.enchant.get(ItemRegistry.DIAMOND_SWORD.name()).enchantments.get(bookIndex2);
                            if (enchantmentMap.containsKey(key) && enchantmentMap.size() == 1) {
                                info("放入 " + EnchantmentUtil.getChinese(key) + " 附魔书");
                                book2.set(itemStack);
                                return true;
                            }
                        }
                        return false;
                    }, 1
            );

            int costForItem = EnchantmentUtil.calculateAnvilCostForItem(book1.get(), book2.get());
            if (anvilHelper.checkAndSetXp(costForItem)) return;

            if (!click1.isEmpty() && !click2.isEmpty()) {
                actions.addAll(click1);
                actions.addAll(click2);
                actions.add(new ShiftClick(openContainer.getContainerId(), 2, ShiftClickItemAction.LEFT_CLICK));
            }
        }

        /**
         * 附魔合并后的书（阶段3/5/7）— 消除 enchantMergedBook1/2/3 重复
         * @param bookConfigIndex 配置中的附魔书索引 (1/3/5)
         */
        private void handleEnchantMergedBook(Container openContainer, List<InventoryAction> actions, int bookConfigIndex) {
            info("附魔合并后的书（索引" + bookConfigIndex + "）");

            boolean alreadyInAnvil = anvilHelper.isEquipmentInAnvilSlot0(openContainer);
            List<InventoryAction> equipActions;
            if (alreadyInAnvil) {
                info("装备已在铁砧 input 槽 0，跳过 deposit");
                equipActions = Lists.newArrayList();
                ItemStack[] swordHolder = new ItemStack[]{anvilHelper.getAnvilSlot0Item(openContainer)};
                currentItemToEnchant = swordHolder[0];
            } else {
                ItemStack[] swordHolder = new ItemStack[]{null};
                equipActions = anvilHelper.depositEquipment(openContainer, swordHolder);
                if (equipActions.isEmpty()) return;
                currentItemToEnchant = swordHolder[0];
            }

            anvilHelper.addRenameAction(actions, openContainer, currentItemToEnchant);

            AtomicReference<ItemStack> book = new AtomicReference<>();
            List<InventoryAction> bookActions = InventoryActionMacros.deposit(
                    openContainer.getContainerId(),
                    itemStack -> {
                        if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                            Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                            if (enchantmentMap.containsKey(config.enchant.get(ItemRegistry.DIAMOND_SWORD.name()).enchantments.get(bookConfigIndex))) {
                                if (enchantmentMap.size() == 2) {
                                    info("放入合并附魔书（索引" + bookConfigIndex + "）");
                                    book.set(itemStack);
                                    return true;
                                }
                            }
                        }
                        return false;
                    }, 1
            );

            int costForItem = EnchantmentUtil.calculateAnvilCostForItem(currentItemToEnchant, book.get());
            if (anvilHelper.checkAndSetXp(costForItem)) return;

            if (!bookActions.isEmpty()) {
                if (!equipActions.isEmpty()) {
                    actions.addAll(equipActions);
                }
                actions.addAll(bookActions);
                actions.add(new ShiftClick(openContainer.getContainerId(), 2, ShiftClickItemAction.LEFT_CLICK));
            }
        }
    }

    // ========== 枚举 ==========

    public enum State {
        COLLECT_EXPERIENCE,        // 收集经验
        OPEN_EQUIPMENT_CHEST,      // 寻找装备箱子
        WAITING_EQUIPMENT_CHEST_OPEN,   // 移动到装备箱子
        WITHDRAW_EQUIPMENT,        // 取出装备
        AWAIT_EQUIPMENT_WITHDRAW,  // 等待装备取出完成
        OPEN_ENCHANT_BOOK_CHEST,   // 寻找附魔书箱子
        AWAIT_ENCHANT_BOOK_CHEST, // 移动到附魔书箱子
        WITHDRAW_ENCHANT_BOOK,     // 取出附魔书
        AWAIT_ENCHANT_BOOK_WITHDRAW, // 等待附魔书取出完成
        OPEN_ANVIL,               // 寻找铁砧
        AWAIT_ANVIL,            // 移动到铁砧
        ENCHANT_ITEM,             // 附魔物品
        AWAIT_ENCHANT,            // 等待附魔完成
        STORE_RESULT,             // 存储结果
        MOVE_TO_RESULT_CHEST,     // 移动到成品箱子
        DEPOSIT_RESULT,           // 存放成品
        AWAIT_DEPOSIT,            // 等待存放完成
        REST                      // 休息
    }

    public enum EquipmentType {
        SWORD("剑"),
        PICKAXE("镐"),
        HELMET("头盔"),
        CHESTPLATE("胸甲"),
        LEGGINGS("护腿"),
        BOOTS("鞋子");

        private final String displayName;

        EquipmentType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String toString() {
            return displayName;
        }
    }
}
