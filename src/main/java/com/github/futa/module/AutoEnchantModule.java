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
    private int requiredXpLevel = 10;
    private boolean needXp = false;
    private PathingRequestFuture pathingFuture = PathingRequestFuture.rejected;
    private RequestFuture inventoryActionFuture = RequestFuture.rejected;
    private final Timer actionDelayTimer = Timers.tickTimer();
    private final Timer interactTimer = Timers.tickTimer();

    private ItemStack currentItemToEnchant = null;
    private List<ItemStack> currentEnchantBook = new ArrayList<>();
    private BlockPos currentAnvil = null;

    // 剑的特殊处理相关
    private boolean isSwordSpecialProcessing = false;
    private int swordEnchantStage = 0; // 0: 初始状态, 1: 附魔第一本书, 2: 合并2-3本书, 3: 附魔合并后的书, 4: 合并4-5本书, 5: 附魔合并后的书, 6: 合并6-7本书, 7: 附魔合并后的书

    // 附魔进度跟踪
    private Map<String, Integer> currentEquipmentEnchants = new HashMap<>(); // 装备当前已有的附魔

    // 附魔书箱子缓存：记录每种附魔书所在的箱子坐标
    private Map<String, Integer> enchantBookChestCache = new ConcurrentHashMap<>(); // 附魔类型 -> 箱子索引

    // 经验农场相关
    private int lastRecordedLevel = -1; // 上次记录的等级

    // 搜索状态相关
    private boolean hasSearchedAllChests = false; // 是否已搜索完所有箱子

    // 附魔尝试次数跟踪
    private int currentEnchantAttempts = 0; // 当前装备的附魔尝试次数
    private ItemStack lastEnchantedItem = null; // 上次尝试附魔的装备

    // 钻石装备到装备类型的映射
    public static final Map<Integer, EquipmentType> DIAMOND_ITEM_MAP = new ConcurrentHashMap<>();

    public static AutoEnchantConfig config = PLUGIN_CONFIG.autoEnchant;

    static {
        // 只映射钻石装备
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
        lastRecordedLevel = -1;
        hasSearchedAllChests = false;
        currentEnchantAttempts = 0;
        lastEnchantedItem = null;
        pathingFuture = PathingRequestFuture.rejected;
        inventoryActionFuture = RequestFuture.rejected;
        actionDelayTimer.reset();
        interactTimer.reset();

        // 重置剑的特殊处理相关变量
        isSwordSpecialProcessing = false;
        swordEnchantStage = 0;
    }

    private void onTick(ClientBotTick event) {
        switch (state) {
            case COLLECT_EXPERIENCE -> {
                if (pathingFuture.isCompleted() && inventoryActionFuture.isCompleted()) {

                    if (hasEnoughExperience()) {
                        info("已收集足够的经验，开始处理装备, 等级：" + CACHE.getPlayerCache().getThePlayer().getLevel());
                        // 重置等级记录，为下次收集做准备
                        lastRecordedLevel = -1;
                        needXp = false;
                        setState(State.OPEN_EQUIPMENT_CHEST);
                    } else {
                        collectExperience();
                    }
                }
            }
            case OPEN_EQUIPMENT_CHEST -> {
                List<ItemStack> playerInv = getPlayerInv();
                for (ItemStack itemStack : playerInv) {
                    if (DIAMOND_ITEM_MAP.containsKey(itemStack.getId())) {
                        var equipmentType = DIAMOND_ITEM_MAP.get(itemStack.getId());
                        var equipmentEnchants = getEquipmentEnchantsMaxLevel(itemStack);
                        if (needsMoreEnchants(itemStack, equipmentType)) {
                            currentItemToEnchant = itemStack;
                            currentEquipmentEnchants = equipmentEnchants;
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

                openChestAtIndex(config.equipmentChests, currentEquipmentChestIndex);

                setState(State.WAITING_EQUIPMENT_CHEST_OPEN);
            }
            case WAITING_EQUIPMENT_CHEST_OPEN -> {
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
            case WITHDRAW_EQUIPMENT -> {
                if (actionDelayTimer.tick(config.delayBetweenActions)) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    List<InventoryAction> actions = Lists.newArrayList();

                    // 寻找待附魔的装备
                    var equipmentOpt = findEquipmentToEnchant(openContainer);
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
            case AWAIT_EQUIPMENT_WITHDRAW -> {
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
            case OPEN_ENCHANT_BOOK_CHEST -> {
                if (pathingFuture.isCompleted() && inventoryActionFuture.isCompleted()) {
//                        List<String> allNeededEnchantments = getAllNeededEnchantmentsBook(getEnchantStrategy(DIAMOND_ITEM_MAP.get(currentItemToEnchant.getId())));
                    if (hasEnoughEnchantBook()) {
                        currentBookChestIndex = 0;
                        currentCachedChestIndex = -1;
                        hasSearchedAllChests = false;
                        info("玩家库存已找到所有需要的附魔书，去铁砧");
                        setState(State.OPEN_ANVIL);
                        return;
                    }

                    if (currentBookChestIndex >= config.enchantBookChests.size()) {
                        //所有箱子都找过了
                        if (hasSearchedAllChests) {
                            hasSearchedAllChests = false;
                            // 已经搜索过所有箱子且没有找到，跳过附魔书收集
                            info("已搜索完所有箱子但未找到所需附魔书，跳过附魔直接存放装备");
                            setState(State.STORE_RESULT);
                            return;
                        } else {
                            // 第一次搜索完所有箱子，设置标记
                            hasSearchedAllChests = true;
                            currentBookChestIndex = 0;
                            currentItemToEnchant = null;
                            setState(State.OPEN_EQUIPMENT_CHEST);
                            return;
                        }
                    }

                    if (currentCachedChestIndex == -1) {
                        // 获取当前需要的附魔书列表
                        EquipmentType equipmentType = DIAMOND_ITEM_MAP.get(getCurrentEquipment().getId());
                        if (equipmentType != null) {
                            AutoEnchantConfig.EnchantStrategy strategy = getEnchantStrategy(equipmentType);
                            List<String> neededEnchants = getAllNeededEnchantmentsBook(strategy);

                            // 检查是否有缓存的位置
                            Integer cachedChestIndex = null;
                            for (String neededEnchant : neededEnchants) {
                                if (enchantBookChestCache.containsKey(neededEnchant)) {
                                    cachedChestIndex = enchantBookChestCache.get(neededEnchant);
                                    // 检查缓存的箱子索引是否有效
                                    if (cachedChestIndex >= 0 && cachedChestIndex < config.enchantBookChests.size()) {
                                        info("使用缓存位置：附魔书 " + EnchantmentUtil.getChinese(neededEnchant) + " 在箱子 " + cachedChestIndex);
                                        currentBookChestIndex = cachedChestIndex;
                                        currentCachedChestIndex = cachedChestIndex;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    //开箱
                    openChestAtIndex(config.enchantBookChests, currentBookChestIndex);
                    setState(State.AWAIT_ENCHANT_BOOK_CHEST);
                }
            }
            case AWAIT_ENCHANT_BOOK_CHEST -> {
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
            case WITHDRAW_ENCHANT_BOOK -> {
                if (actionDelayTimer.tick(config.delayBetweenActions)) {
                    info("开始提取附魔书");

                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    List<InventoryAction> actions = Lists.newArrayList();

                    // 寻找匹配的附魔书
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
            case AWAIT_ENCHANT_BOOK_WITHDRAW -> {
                if (inventoryActionFuture.isCompleted()) {


                    info("提取附魔书完成");
                    if (!currentEnchantBook.isEmpty()) {
                        currentCachedChestIndex = -1;
                        currentBookChestIndex = 0;
                        setState(State.OPEN_ENCHANT_BOOK_CHEST);
                        hasSearchedAllChests = false;
                    } else {
                        // 没有找到附魔书，尝试下一个箱子
                        currentBookChestIndex++;
                        info("没有找到附魔书，尝试下一个箱子 " + currentBookChestIndex);
                        setState(State.OPEN_ENCHANT_BOOK_CHEST);
                    }
                }
            }
            case OPEN_ANVIL -> {
                // 检测是否同一件装备进入OPEN_ANVIL超过10次
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
                        // 新装备，重置计数器
                        lastEnchantedItem = currentItemToEnchant;
                        currentEnchantAttempts = 1;
                    }
                } else {
                    // 没有装备，重置计数器
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
            case AWAIT_ANVIL -> {
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
            case ENCHANT_ITEM -> {
                var equipmentType = DIAMOND_ITEM_MAP.get(getCurrentEquipment().getId());
                info("正在附魔 " + equipmentType);
                var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                List<InventoryAction> actions = Lists.newArrayList();

                if (!needsMoreEnchants(currentItemToEnchant, equipmentType)) {
                    info("装备已经完美附魔 " + equipmentType);
                    setState(State.STORE_RESULT);
                    return;
                }

                // 检查是否有足够的附魔书（需要7本）
                if (!hasEnoughEnchantBook()) {
                    warn("附魔书数量不足，需要7本，当前只有" + getPlayerInvBook().size() + "本");
                    setState(State.COLLECT_EXPERIENCE);
                    return;
                }

                // 根据当前状态决定操作
                if (equipmentType == EquipmentType.SWORD) {
                    // 剑的特殊处理逻辑
                    handleSwordSpecialEnchant(openContainer, actions);
                } else {
                    // 普通附魔逻辑
                    handleNormalEnchant(openContainer, actions);
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
            case AWAIT_ENCHANT -> {
                if (inventoryActionFuture.isCompleted()) {

                    // 更新当前装备的附魔状态
                    List<ItemStack> playerInv = getPlayerInv();
                    for (ItemStack itemStack : playerInv) {

                        if (DIAMOND_ITEM_MAP.containsKey(itemStack.getId())) {
                            var itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
                            String customName = EnchantmentUtil.getCustomName(itemStack);
                            info("正在检查装备 " + itemData.name() + " " + customName + " id:" + itemStack.getId());
                            currentItemToEnchant = itemStack;
                            currentEquipmentEnchants = getEquipmentEnchantsMaxLevel(currentItemToEnchant);

                            var equipmentType = DIAMOND_ITEM_MAP.get(itemStack.getId());
                            info("附魔完成" + equipmentType + ",当前附魔:" + EnchantmentUtil.getEnchantmentJsonItemCN(currentItemToEnchant));


                            // 检查是否还有附魔书需要使用
                            if (needsMoreEnchants(currentItemToEnchant, equipmentType)) {
                                // 还有附魔书需要使用，继续附魔
                                info("还没有附魔满，继续附魔" + equipmentType);
                                closeCurrentContainer();
                                setState(State.OPEN_ANVIL);
                                return;
                            }
                        }
                    }
                    // 所有附魔完成
                    info("所有附魔完成 " + ",当前附魔:" + EnchantmentUtil.getEnchantmentJsonItemCN(currentItemToEnchant));
                    isSwordSpecialProcessing = false;
                    closeCurrentContainer();
                    setState(State.STORE_RESULT);
                }
            }
            case STORE_RESULT -> {
                // 根据装备是否完全附魔选择不同的箱子
                // 检查装备是否完全附魔
                boolean isFullyEnchanted = false;
                if (currentItemToEnchant != null) {
                    EquipmentType equipmentType = DIAMOND_ITEM_MAP.get(currentItemToEnchant.getId());
                    isFullyEnchanted = !needsMoreEnchants(currentItemToEnchant, equipmentType);
                }

                if (isFullyEnchanted) {
                    // 装备完全附魔，使用普通结果箱子
                    if (currentResultChestIndex >= config.resultChests.size()) {
                        currentResultChestIndex = 0;
                    }
                    info("正在打开成品箱子" + (currentResultChestIndex + 1));
                    openChestAtIndex(config.resultChests, currentResultChestIndex);
                } else {
                    // 装备未完全附魔，使用失败箱子
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
                        openChestAtIndex(config.resultChests, currentResultChestIndex);
                    }
                }

                setState(State.MOVE_TO_RESULT_CHEST);
            }
            case MOVE_TO_RESULT_CHEST -> {
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
            case DEPOSIT_RESULT -> {
                if (actionDelayTimer.tick(config.delayBetweenActions)) {
                    var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
                    List<InventoryAction> actions = Lists.newArrayList();

                    // 存放附魔后的装备
                    if (currentItemToEnchant != null) {
                        // 检查装备是否完全附魔
                        EquipmentType equipmentType = DIAMOND_ITEM_MAP.get(currentItemToEnchant.getId());
                        boolean isFullyEnchanted = !needsMoreEnchants(currentItemToEnchant, equipmentType);

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
            case AWAIT_DEPOSIT -> {
                if (inventoryActionFuture.isCompleted()) {
                    currentItemToEnchant = null;
                    currentEnchantBook.clear();
                    currentAnvil = null;
                    currentEquipmentChestIndex = 0;
                    currentBookChestIndex = 0;
                    currentEquipmentEnchants.clear();
                    hasSearchedAllChests = false;
                    requiredXpLevel = 10;
                    info("已附魔，已存货");
                    // 不清空缓存，保留附魔书位置信息以便下次使用
                    setState(State.COLLECT_EXPERIENCE);
                }
            }
            case REST -> {
                requiredXpLevel = 40;
                setState(State.COLLECT_EXPERIENCE);
            }
        }
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

    /**
     * 确保所有书都有
     *
     * @return
     */
    public boolean hasEnoughEnchantBook() {
        List<ItemStack> playerInv = getPlayerInv();
        Set<String> enchantments = new HashSet<>();
        ItemStack item = null;
        for (ItemStack itemStack : playerInv) {

            EquipmentType equipmentType = DIAMOND_ITEM_MAP.get(itemStack.getId());
            if (equipmentType != null) {
                item = itemStack;
                Map<String, Integer> equipmentEnchants = getEquipmentEnchantsMaxLevel(itemStack);
                Set<String> keySet = equipmentEnchants.keySet();
                for (String key : keySet) {
                    if (getEnchantStrategy(equipmentType).enchantments.contains(key)) {
                        enchantments.add(key);
                    }
                }
                break;
            }

        }
        if (item == null) {
            return false;
        }
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

    public ItemStack getCurrentEquipment() {
        List<ItemStack> playerInv = getPlayerInv();
        for (ItemStack itemStack : playerInv) {
            EquipmentType equipmentType = DIAMOND_ITEM_MAP.get(itemStack.getId());
            if (equipmentType != null) {
                return itemStack;
            }
        }
        return null;
    }

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
            if (!EnchantmentUtil.isEnchantedBook(itemStack)) {
                continue;
            }

            Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);

            for (Map.Entry<String, Integer> entry : enchantmentMap.entrySet()) {
                if (EnchantmentUtil.isMaxLevel(entry.getKey(), entry.getValue())) {
                    strings.add(entry.getKey());
                }
            }
        }

        return strings;
    }

    private boolean hasEnoughExperience() {
        return CACHE.getPlayerCache().getThePlayer().getLevel() >= requiredXpLevel;
    }

    private void collectExperience() {
        if (config.xpFarmPos != BlockPos.ZERO) {
            var pos = config.xpFarmPos;
            var player = CACHE.getPlayerCache().getThePlayer();
            int currentLevel = player.getLevel();


            // 计算玩家与目标位置的距离

            double distance = MathHelper.distance3d(pos.x(), pos.y(), pos.z(), player.getX(), player.getY(), player.getZ());
            // 如果距离小，认为已经到达，不需要寻路
            if (distance < 1.4) {

                // 检查等级变化并打印
                if (lastRecordedLevel == -1) {
                    // 首次记录等级
                    lastRecordedLevel = currentLevel;
                    info("开始收集经验，当前等级：" + currentLevel + "，目标等级：" + requiredXpLevel);
                } else if (currentLevel != lastRecordedLevel) {
                    // 等级发生变化
                    info("等级变化：" + lastRecordedLevel + " -> " + currentLevel);
                    lastRecordedLevel = currentLevel;
                }

                return;
            }

            // 距离较远，需要寻路
            if (pathingFuture == null || pathingFuture.isDone()) {
                info("正在前往 xpfarm，距离：" + distance + "，当前等级：" + currentLevel);
                pathingFuture = BARITONE.pathTo(pos.x(), pos.y(), pos.z());
            }
        }
    }

    private List<InventoryAction> findEquipmentToEnchant(Container openContainer) {

        List<InventoryAction> withdrawActions = InventoryActionMacros.withdraw(
                openContainer.getContainerId(),
                itemStack -> {
                    if (itemStack == null) return false;

                    EquipmentType type = DIAMOND_ITEM_MAP.get(itemStack.getId());
                    if (type == null) {
                        return false;
                    }

                    // 检查是否还需要附魔
                    if (!needsMoreEnchants(itemStack, type)) {
                        info("装备" + type + "已达到目标附魔，跳过");
                        return false;
                    }

                    info("提取:" + type);
                    currentItemToEnchant = itemStack;

                    info("包含附魔:" + EnchantmentUtil.getEnchantmentJsonItemCN(currentItemToEnchant));

                    currentEquipmentEnchants = getEquipmentEnchantsMaxLevel(itemStack); // 记录当前附魔

                    return true;
                }, 1
        );

        return withdrawActions;
    }


    private List<InventoryAction> findMatchingEnchantBook(Container openContainer) {
        List<InventoryAction> withdrawActions = Lists.newArrayList();
        if (getCurrentEquipment() == null) {
            return withdrawActions;
        }

        EquipmentType equipmentType = DIAMOND_ITEM_MAP.get(getCurrentEquipment().getId());
        if (equipmentType == null) return withdrawActions;

        AutoEnchantConfig.EnchantStrategy strategy = getEnchantStrategy(equipmentType);
        if (!strategy.enabled || strategy.enchantments.isEmpty()) return withdrawActions;

        currentEnchantBook.clear();

        // 获取所有需要的附魔
        List<String> neededEnchants = getAllNeededEnchantmentsBook(strategy);
        if (neededEnchants.isEmpty()) {
            info("装备" + equipmentType + "已达到目标附魔配置");
            return withdrawActions;
        }

        // 收集所有需要的附魔书
        Map<String, ItemStack> foundBooks = new HashMap<>();

        withdrawActions = InventoryActionMacros.withdraw(
                openContainer.getContainerId(),
                itemStack -> {
                    if (!EnchantmentUtil.isEnchantedBook(itemStack)) {
                        return false;
                    }

                    Map<String, Integer> enchantmentMap = EnchantmentUtil.getBookEnchantmentMapMaxLevel(itemStack);
                    // 检查这本书是否包含任何需要的附魔
                    for (String neededEnchant : neededEnchants) {
                        if (enchantmentMap.containsKey(neededEnchant)
                                && !foundBooks.containsKey(neededEnchant)
                        ) {
                            info("提取附魔书:" + EnchantmentUtil.getChinese(neededEnchant) + enchantmentMap.get(neededEnchant));
                            foundBooks.put(neededEnchant, itemStack);
                            // 更新缓存：记录这个附魔书在当前箱子
                            enchantBookChestCache.put(neededEnchant, currentBookChestIndex);
                            return true;
                        }
                    }
                    return false;
                }, 1
        );

        // 将找到的附魔书添加到当前附魔书列表
        currentEnchantBook.addAll(foundBooks.values());

        return withdrawActions;
    }


    private boolean needsMoreEnchants(ItemStack equipment, EquipmentType type) {
        AutoEnchantConfig.EnchantStrategy strategy = getEnchantStrategy(type);
        if (!strategy.enabled || strategy.enchantments.isEmpty()) {
            return false;
        }

        Map<String, Integer> currentEnchants = getEquipmentEnchantsMaxLevel(equipment);

        // 检查是否还有未达到目标的附魔
        for (String desiredEnchant : strategy.enchantments) {
            if (!currentEnchants.containsKey(desiredEnchant)) {
                return true; // 缺少这个附魔
            }
            Integer currentLevel = currentEnchants.get(desiredEnchant);
            Integer maxLevel = EnchantmentUtil.getMaxLevel(desiredEnchant);
            if (maxLevel != null && currentLevel < maxLevel) {
                return true; // 附魔等级不够
            }
        }

        return false; // 所有附魔都已达到目标
    }

    private String getNextNeededEnchantment() {
        AutoEnchantConfig.EnchantStrategy strategy = getEnchantStrategy(DIAMOND_ITEM_MAP.get(currentItemToEnchant.getId()));
        if (!strategy.enabled || strategy.enchantments.isEmpty()) {
            return null;
        }

        Map<String, Integer> currentEnchants = getEquipmentEnchantsMaxLevel(currentItemToEnchant);

        // 按照配置顺序查找下一个需要的附魔
        for (String desiredEnchant : strategy.enchantments) {
            if (!currentEnchants.containsKey(desiredEnchant)) {
                return desiredEnchant; // 缺少这个附魔
            }
            Integer currentLevel = currentEnchants.get(desiredEnchant);
            Integer maxLevel = EnchantmentUtil.getMaxLevel(desiredEnchant);
            if (maxLevel != null && currentLevel < maxLevel) {
                return desiredEnchant; // 附魔等级不够
            }
        }

        return ""; // 所有附魔都已达到目标
    }

    private List<String> getAllNeededEnchantmentsBook(AutoEnchantConfig.EnchantStrategy strategy) {
        List<String> neededEnchants = new ArrayList<>();
        if (!strategy.enabled || strategy.enchantments.isEmpty()) {
            return neededEnchants;
        }

        List<String> playerInvBook = getPlayerInvBook();

        Map<String, Integer> equipmentEnchants = getEquipmentEnchantsMaxLevel(currentItemToEnchant);
        // 按照配置顺序查找所有需要的附魔
        for (String desiredEnchant : strategy.enchantments) {
            if (playerInvBook.contains(desiredEnchant)) {
                //不缺
                continue;
            }
            if (equipmentEnchants.containsKey(desiredEnchant)) {
                // 不缺少这个附魔
                continue;
            }

            neededEnchants.add(desiredEnchant); // 附魔等级不够
        }

        return neededEnchants;
    }


    private Optional<BlockPos> findNearbyAnvil() {
        Vector3d playerPos = CACHE.getPlayerCache().getThePlayer().position();
        BlockPos playerBlockPos = new BlockPos(playerPos.getX(), playerPos.getY(), playerPos.getZ());
        int searchRadius = config.anvilSearchRadius;

        // Step 1: 先在XZ平面找（Y固定）
        for (int r = 0; r <= searchRadius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.abs(x) != r && Math.abs(z) != r) continue; // 环边界
                    BlockPos checkPos = playerBlockPos.offset(x, 0, z);
                    var blockState = World.getBlockStateId(checkPos);
                    if (isAnvil(blockState)) {
                        return Optional.of(checkPos);
                    }
                }
            }
        }

        // Step 2: 如果XZ平面没找到，再扩展Y方向
        for (int dy = 1; dy <= searchRadius; dy++) {
            for (int r = 0; r <= searchRadius; r++) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (Math.abs(x) != r && Math.abs(z) != r) continue; // 环边界

                        // 上一层
                        BlockPos up = playerBlockPos.offset(x, dy, z);
                        var upState = World.getBlockStateId(up);
                        if (isAnvil(upState)) {
                            return Optional.of(up);
                        }

                        // 下一层
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

    private void handleSwordSpecialEnchant(Container openContainer, List<InventoryAction> actions) {

        switch (getSwordStage()) {
            case 1:
                // 第一阶段：附魔第一本书
                enchantFirstBook(openContainer, actions);
                break;
            case 2:
                // 第二阶段：附魔第二本书
                stage(openContainer, actions);
                break;
            case 3:
                // 第三阶段：附魔合并后的书1
                enchantMergedBook1(openContainer, actions);
                break;

            case 4:
                stage(openContainer, actions);
                break;
            case 5:
                // 第五阶段：附魔合并后的书2
                enchantMergedBook2(openContainer, actions);
                break;

            case 6:
                stage(openContainer, actions);
                break;
            case 7:
                // 第七阶段：附魔合并后的书3
                enchantMergedBook3(openContainer, actions);
                break;
            default:
                break;
        }
        info("剑的附魔处理阶段：" + swordEnchantStage);

    }

    private void stage(Container openContainer, List<InventoryAction> actions) {
        info("正在合并附魔书，阶段：" + getSwordStage());

        // 根据当前阶段合并不同的书
        if (getSwordStage() == 2) {
            // 合并第2本和第3本书
            actions.addAll(mergeTwoBooks(openContainer, 1, 2)); // 索引1和2对应第2本和第3本书
        } else if (getSwordStage() == 4) {
            // 合并第4本和第5本书
            actions.addAll(mergeTwoBooks(openContainer, 3, 4)); // 索引3和4对应第4本和第5本书
        } else if (getSwordStage() == 6) {
            // 合并第6本和第7本书
            actions.addAll(mergeTwoBooks(openContainer, 5, 6)); // 索引5和6对应第6本和第7本书
        }

    }

    public int getSwordStage() {
        ItemStack sword = getCurrentEquipment();
        var equipmentType = DIAMOND_ITEM_MAP.get(sword.getId());
        if (equipmentType == EquipmentType.SWORD) {
            int repairCost = EnchantmentUtil.getItemRepairCost(sword);
            Map<String, Integer> swordEnchants = getEquipmentEnchantsMaxLevel(sword);
            List<ItemStack> playerInv = getPlayerInv();
            if (repairCost == 0 && swordEnchants.size() == 0) {
                // 剑的RepairCost为0，启动特殊处理
                info("检测到剑的RepairCost为0，启动特殊附魔处理");
                return 1;
            }

            if (swordEnchants.containsKey("sweeping_edge")) {
                swordEnchantStage = 2;
            }

            if (swordEnchants.containsKey("sharpness")) {
                swordEnchantStage = 4;
            }

            if (swordEnchants.containsKey("unbreaking")) {
                swordEnchantStage = 6;
            }

            if (swordEnchants.containsKey("mending")) {
                swordEnchantStage = 8;
            }
            for (ItemStack itemStack : playerInv) {
                if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                    Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                    if (enchantmentMap.size() == 2) {
                        swordEnchantStage++;
                        break;
                    }
                }
            }
        }
        return swordEnchantStage;
    }

    private void handleNormalEnchant(Container openContainer, List<InventoryAction> actions) {
        info("普通附魔处理");

        // 检查是否还有附魔书需要使用
        if (!needsMoreEnchants(currentItemToEnchant, DIAMOND_ITEM_MAP.get(currentItemToEnchant.getId()))) {
            // 所有附魔书都已使用完毕
            return;
        }
        // 在铁砧中附魔：装备放第一个槽，当前附魔书放第二个槽
        List<InventoryAction> click1 = InventoryActionMacros.deposit(
                openContainer.getContainerId(),
                itemStack -> {
                    if (itemStack == null) return false;

                    EquipmentType type = DIAMOND_ITEM_MAP.get(itemStack.getId());
                    if (type == null) {
                        //物品不是钻石物品，跳过
                        return false;
                    }

                    // 检查是否还需要附魔
                    if (!needsMoreEnchants(itemStack, type)) {
                        info("装备" + type + "已达到目标附魔，跳过");
                        return false;
                    }
                    currentItemToEnchant = itemStack;
                    info("准备附魔:" + type + ",当前附魔:" + EnchantmentUtil.getEnchantmentJsonItemCN(currentItemToEnchant));

                    return true;
                }, 1
        );


        String nextNeededEnchantment = getNextNeededEnchantment();
        int costForItem = EnchantmentUtil.calculateAnvilCostForItem(currentItemToEnchant, nextNeededEnchantment) + 1;

        String name = ItemRegistry.REGISTRY.get(currentItemToEnchant.getId()).name();
        String customName = EnchantmentUtil.getCustomName(currentItemToEnchant);
        if (StrUtil.isEmpty(customName)) {
            customName = config.getRandomName(name);
        }
        actions.add(new RenameItem(openContainer.getContainerId(), customName));
        info("装备重命名：" + name);

        if (CACHE.getPlayerCache().getThePlayer().getLevel() < costForItem) {
            warn("经验不足，无法附魔 " + EnchantmentUtil.getChinese(nextNeededEnchantment));
            info("需要" + costForItem + "级经验, 当前等级:" + CACHE.getPlayerCache().getThePlayer().getLevel());
            requiredXpLevel = costForItem;
            needXp = true;
            return;
        }

        // 使用当前索引的附魔书
        List<InventoryAction> click2 = InventoryActionMacros.deposit(
                openContainer.getContainerId(),
                itemStack -> {
                    boolean b = itemStack != null && itemStack.getId() == ItemRegistry.ENCHANTED_BOOK.id();
                    if (b) {
                        if (!nextNeededEnchantment.isEmpty()) {
                            Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                            if (enchantmentMap.containsKey(nextNeededEnchantment)) {
                                info("使用附魔书: " + EnchantmentUtil.getChinese(nextNeededEnchantment));
                                return true;
                            }
                        }
                    }
                    return false;
                }, 1
        );

        if (!click1.isEmpty() && !click2.isEmpty()) {
            actions.addAll(click1);
            actions.addAll(click2);
            actions.add(new ShiftClick(openContainer.getContainerId(), 2, ShiftClickItemAction.LEFT_CLICK));
        }
    }

    private void enchantFirstBook(Container openContainer, List<InventoryAction> actions) {
        info("附魔第一本书");


        // 放入剑
        List<InventoryAction> click1 = InventoryActionMacros.deposit(
                openContainer.getContainerId(),
                itemStack -> {
                    if (itemStack == null) return false;
                    EquipmentType type = DIAMOND_ITEM_MAP.get(itemStack.getId());
                    if (type == EquipmentType.SWORD) {
                        currentItemToEnchant = itemStack;
                        return true;
                    }
                    return false;
                }, 1
        );


        String name = ItemRegistry.REGISTRY.get(currentItemToEnchant.getId()).name();
        String customName = EnchantmentUtil.getCustomName(currentItemToEnchant);
        if (StrUtil.isEmpty(customName)) {
            customName = config.getRandomName(name);
        }
        actions.add(new RenameItem(openContainer.getContainerId(), customName));
        info("装备重命名：" + name);

        AtomicReference<ItemStack> book = new AtomicReference<>();
        // 放入第一本附魔书
        List<InventoryAction> click2 = InventoryActionMacros.deposit(
                openContainer.getContainerId(),
                itemStack -> {
                    if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                        if (EnchantmentUtil.getEnchantmentMap(itemStack).containsKey(config.enchant.get(ItemRegistry.DIAMOND_SWORD.name()).enchantments.get(0))) {
                            info("使用第一本附魔书");
                            book.set(itemStack);
                            return true;
                        }
                    }
                    return false;
                }, 1
        );


        int costForItem = EnchantmentUtil.calculateAnvilCostForItem(currentItemToEnchant, book.get()) + 1;
        if (CACHE.getPlayerCache().getThePlayer().getLevel() < costForItem) {
            info("需要" + costForItem + "级经验, 当前等级:" + CACHE.getPlayerCache().getThePlayer().getLevel());
            requiredXpLevel = costForItem;
            needXp = true;
            return;
        }

        if (!click1.isEmpty() && !click2.isEmpty()) {
            actions.addAll(click1);
            actions.addAll(click2);
            actions.add(new ShiftClick(openContainer.getContainerId(), 2, ShiftClickItemAction.LEFT_CLICK));
        }
    }

    private void enchantMergedBook1(Container openContainer, List<InventoryAction> actions) {
        info("附魔合并后的书1");

        // 放入剑
        List<InventoryAction> click1 = InventoryActionMacros.deposit(
                openContainer.getContainerId(),
                itemStack -> {
                    if (itemStack == null) return false;
                    EquipmentType type = DIAMOND_ITEM_MAP.get(itemStack.getId());
                    if (type == EquipmentType.SWORD) {
                        currentItemToEnchant = itemStack;
                        return true;
                    }
                    return false;
                }, 1
        );
        String name = ItemRegistry.REGISTRY.get(currentItemToEnchant.getId()).name();
        String customName = EnchantmentUtil.getCustomName(currentItemToEnchant);
        if (StrUtil.isEmpty(customName)) {
            customName = config.getRandomName(name);
        }
        actions.add(new RenameItem(openContainer.getContainerId(), customName));
        info("装备重命名：" + name);

        AtomicReference<ItemStack> book = new AtomicReference<>();

        // 放入合并后的书1
        List<InventoryAction> click2 = InventoryActionMacros.deposit(
                openContainer.getContainerId(),
                itemStack -> {

                    if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                        Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                        if (enchantmentMap.containsKey(config.enchant.get(ItemRegistry.DIAMOND_SWORD.name()).enchantments.get(1))) {
                            if (enchantmentMap.size() == 2) {
                                info("放入 抢夺+锋利 附魔书");
                                book.set(itemStack);
                                return true;
                            }
                        }
                    }

                    return false;
                }, 1
        );


        int costForItem = EnchantmentUtil.calculateAnvilCostForItem(currentItemToEnchant, book.get());
        if (CACHE.getPlayerCache().getThePlayer().getLevel() < costForItem) {
            info("需要" + costForItem + "级经验, 当前等级:" + CACHE.getPlayerCache().getThePlayer().getLevel());
            requiredXpLevel = costForItem;
            needXp = true;
            return;
        }

        if (!click1.isEmpty() && !click2.isEmpty()) {
            actions.addAll(click1);
            actions.addAll(click2);
            actions.add(new ShiftClick(openContainer.getContainerId(), 2, ShiftClickItemAction.LEFT_CLICK));
        }
    }

    private void enchantMergedBook2(Container openContainer, List<InventoryAction> actions) {
        info("附魔合并后的书2");

        // 放入剑
        List<InventoryAction> click1 = InventoryActionMacros.deposit(
                openContainer.getContainerId(),
                itemStack -> {
                    if (itemStack == null) return false;
                    EquipmentType type = DIAMOND_ITEM_MAP.get(itemStack.getId());
                    if (type == EquipmentType.SWORD) {
                        currentItemToEnchant = itemStack;
                        return true;
                    }
                    return false;
                }, 1
        );
        AtomicReference<ItemStack> book = new AtomicReference<>();

        // 放入合并后的书2
        List<InventoryAction> click2 = InventoryActionMacros.deposit(
                openContainer.getContainerId(),
                itemStack -> {

                    if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                        Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                        if (enchantmentMap.containsKey(config.enchant.get(ItemRegistry.DIAMOND_SWORD.name()).enchantments.get(3))) {
                            if (enchantmentMap.size() == 2) {
                                info("放入 火焰+耐久 附魔书");
                                book.set(itemStack);
                                return true;
                            }
                        }
                    }
                    return false;
                }, 1
        );
        String name = ItemRegistry.REGISTRY.get(currentItemToEnchant.getId()).name();
        String customName = EnchantmentUtil.getCustomName(currentItemToEnchant);
        if (StrUtil.isEmpty(customName)) {
            customName = config.getRandomName(name);
        }
        actions.add(new RenameItem(openContainer.getContainerId(), customName));
        info("装备重命名：" + name);

        int costForItem = EnchantmentUtil.calculateAnvilCostForItem(currentItemToEnchant, book.get());
        if (CACHE.getPlayerCache().getThePlayer().getLevel() < costForItem) {
            info("需要" + costForItem + "级经验, 当前等级:" + CACHE.getPlayerCache().getThePlayer().getLevel());
            requiredXpLevel = costForItem;
            needXp = true;
            return;
        }

        if (!click1.isEmpty() && !click2.isEmpty()) {
            actions.addAll(click1);
            actions.addAll(click2);
            actions.add(new ShiftClick(openContainer.getContainerId(), 2, ShiftClickItemAction.LEFT_CLICK));
        }
    }

    private void enchantMergedBook3(Container openContainer, List<InventoryAction> actions) {
        info("附魔合并后的书3");

        // 放入剑
        List<InventoryAction> click1 = InventoryActionMacros.deposit(
                openContainer.getContainerId(),
                itemStack -> {
                    if (itemStack == null) return false;
                    EquipmentType type = DIAMOND_ITEM_MAP.get(itemStack.getId());
                    if (type == EquipmentType.SWORD) {
                        currentItemToEnchant = itemStack;
                        return true;
                    }
                    return false;
                }, 1
        );

        AtomicReference<ItemStack> book = new AtomicReference<>();

        // 放入合并后的书3
        List<InventoryAction> click2 = InventoryActionMacros.deposit(
                openContainer.getContainerId(),
                itemStack -> {

                    if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                        Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                        if (enchantmentMap.containsKey(config.enchant.get(ItemRegistry.DIAMOND_SWORD.name()).enchantments.get(5))) {
                            if (enchantmentMap.size() == 2) {
                                info("放入 击退+精修 附魔书");
                                book.set(itemStack);
                                return true;
                            }
                        }
                    }
                    return false;
                }, 1
        );
        String name = ItemRegistry.REGISTRY.get(currentItemToEnchant.getId()).name();
        String customName = EnchantmentUtil.getCustomName(currentItemToEnchant);
        if (StrUtil.isEmpty(customName)) {
            customName = config.getRandomName(name);
        }
        actions.add(new RenameItem(openContainer.getContainerId(), customName));
        info("装备重命名：" + name);

        int costForItem = EnchantmentUtil.calculateAnvilCostForItem(currentItemToEnchant, book.get());
        if (CACHE.getPlayerCache().getThePlayer().getLevel() < costForItem) {
            info("需要" + costForItem + "级经验, 当前等级:" + CACHE.getPlayerCache().getThePlayer().getLevel());
            requiredXpLevel = costForItem;
            needXp = true;
            return;
        }

        if (!click1.isEmpty() && !click2.isEmpty()) {
            actions.addAll(click1);
            actions.addAll(click2);
            actions.add(new ShiftClick(openContainer.getContainerId(), 2, ShiftClickItemAction.LEFT_CLICK));
        }
    }

    private List<InventoryAction> mergeTwoBooks(Container openContainer, int bookIndex1, int bookIndex2) {
        List<InventoryAction> actions = Lists.newArrayList();

        AtomicReference<ItemStack> book = new AtomicReference<>();
        AtomicReference<ItemStack> book2 = new AtomicReference<>();

        // 放入第一本书
        List<InventoryAction> click1 = InventoryActionMacros.deposit(
                openContainer.getContainerId(),
                itemStack -> {

                    if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                        Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                        String key = config.enchant.get(ItemRegistry.DIAMOND_SWORD.name()).enchantments.get(bookIndex1);
                        if (enchantmentMap.containsKey(key)) {
                            if (enchantmentMap.size() == 1) {
                                info("放入 " + EnchantmentUtil.getChinese(key) + " 附魔书");
                                book.set(itemStack);
                                return true;
                            }
                        }
                    }
                    return false;
                }, 1
        );

        // 放入第二本书
        List<InventoryAction> click2 = InventoryActionMacros.deposit(
                openContainer.getContainerId(),
                itemStack -> {

                    if (EnchantmentUtil.isEnchantedBook(itemStack)) {
                        Map<String, Integer> enchantmentMap = EnchantmentUtil.getEnchantmentMap(itemStack);
                        String key = config.enchant.get(ItemRegistry.DIAMOND_SWORD.name()).enchantments.get(bookIndex2);
                        if (enchantmentMap.containsKey(key)) {
                            if (enchantmentMap.size() == 1) {
                                info("放入 " + EnchantmentUtil.getChinese(key) + " 附魔书");
                                book2.set(itemStack);
                                return true;
                            }
                        }
                    }
                    return false;
                }, 1
        );


        int costForItem = EnchantmentUtil.calculateAnvilCostForItem(book.get(), book2.get());
        if (CACHE.getPlayerCache().getThePlayer().getLevel() < costForItem) {
            info("需要" + costForItem + "级经验, 当前等级:" + CACHE.getPlayerCache().getThePlayer().getLevel());
            requiredXpLevel = costForItem;
            needXp = true;
            return actions;
        }

        if (!click1.isEmpty() && !click2.isEmpty()) {
            actions.addAll(click1);
            actions.addAll(click2);
            actions.add(new ShiftClick(openContainer.getContainerId(), 2, ShiftClickItemAction.LEFT_CLICK));
        }

        return actions;
    }

    private void openChestAtIndex(List<BlockPos> chests, int index) {
        var chestPos = chests.get(index);
        pathingFuture = BARITONE.rightClickBlock(chestPos.x(), chestPos.y(), chestPos.z());
        pathingFuture.addExecutedListener(f -> interactTimer.reset());
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
            // 显示缓存状态
            if (!enchantBookChestCache.isEmpty()) {
                debug("当前附魔书缓存：");
                for (Map.Entry<String, Integer> entry : enchantBookChestCache.entrySet()) {
                    debug("  {}: 箱子 {}", EnchantmentUtil.getChinese(entry.getKey()), entry.getValue());
                }
            }
        }
        this.state = newState;
    }

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
