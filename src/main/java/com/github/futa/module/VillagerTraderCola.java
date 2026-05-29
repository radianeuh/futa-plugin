//package com.github.futa.module;
//
//import com.alibaba.cola.statemachine.Action;
//import com.alibaba.cola.statemachine.StateMachine;
//import com.alibaba.cola.statemachine.builder.StateMachineBuilder;
//import com.alibaba.cola.statemachine.builder.StateMachineBuilderFactory;
//import com.github.futa.util.EnchantmentUtil;
//import com.github.rfresh2.EventConsumer;
//import com.google.common.cache.Cache;
//import com.google.common.cache.CacheBuilder;
//import com.zenith.cache.data.entity.EntityLiving;
//import com.zenith.cache.data.inventory.Container;
//import com.zenith.event.client.ClientBotTick;
//import com.zenith.feature.inventory.InventoryActionRequest;
//import com.zenith.feature.inventory.actions.*;
//import com.zenith.feature.inventory.util.InventoryActionMacros;
//import com.zenith.feature.inventory.util.InventoryUtil;
//import com.zenith.feature.pathfinder.PathingRequestFuture;
//import com.zenith.mc.item.ItemRegistry;
//import com.zenith.module.api.Module;
//import com.zenith.network.client.ClientSession;
//import com.zenith.network.codec.PacketHandlerCodec;
//import com.zenith.network.codec.PacketHandlerStateCodec;
//import com.zenith.util.RequestFuture;
//import com.zenith.util.math.MathHelper;
//import com.zenith.util.timer.Timers;
//import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
//import it.unimi.dsi.fastutil.ints.IntSet;
//import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
//import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
//import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.VillagerData;
//import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
//import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
//import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundMerchantOffersPacket;
//
//import java.util.*;
//
//import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
//import static com.github.rfresh2.EventConsumer.of;
//import static com.zenith.Globals.CACHE;
//
///**
// * 以 COLA State Machine 重构原来的 switch-case 版状态机。
// * <p>
// * 说明：
// * 1) 尽量不改动业务方法（count、search、提交 InventoryAction 等），把原来的字段搬到 Context 中；
// * 2) 把“每帧 onTick 的巨型分支”改为：当前状态的 TICK 内部迁移（internalTransition）→ 在 Action 中做判断并触发下一事件；
// * 3) 异步 Future（pathing、inventory 请求等）通过监听器回调 fireEvent 进入下一步；
// * 4) 这样 onTick 里只做一次 fireEvent(currentState, TICK)。
// */
//public class VillagerTraderCola extends Module {
//    public static final int PRIORITY = 9000;
//
//    // === FSM ===
//    private StateMachine<TraderState, TraderEvent, TraderContext> fsm;
//    private TraderState currentState = TraderState.RESTOCK_GO_TO_CHEST;
//
//    // === Context（承载原字段） ===
//    private final TraderContext ctx = new TraderContext();
//
//    // === 生命周期 ===
//    @Override
//    public boolean enabledSetting() {
//        return PLUGIN_CONFIG.enabled;
//    }
//
//    public List<EventConsumer<?>> registerEvents() {
//        buildStateMachine();
//        return List.of(
//                of(ClientBotTick.class, this::onTick),
//                of(ClientBotTick.Stopped.class, e -> reset())
//        );
//    }
//
//    @Override
//    public void onDisable() {
//        reset();
//    }
//
//    private void reset() {
//        currentState = TraderState.RESTOCK_GO_TO_CHEST;
//        ctx.interactedVillagersCache.invalidateAll();
//        ctx.offersPacket = null;
//        debug("FSM reset -> {}", currentState);
//    }
//
//    public PacketHandlerCodec registerClientPacketHandlerCodec() {
//        return PacketHandlerCodec.clientBuilder()
//                .setId("villager-trader")
//                .state(ProtocolState.GAME, PacketHandlerStateCodec.clientBuilder()
//                        .inbound(ClientboundMerchantOffersPacket.class, this::onMerchantOffers)
//                        .build())
//                .build();
//    }
//
//    private ClientboundMerchantOffersPacket onMerchantOffers(ClientboundMerchantOffersPacket packet, ClientSession session) {
//        ctx.offersPacket = packet;
//        debug("Offers: {}", packet);
//        return packet;
//    }
//
//    // === onTick → 驱动一次 TICK ===
//    private void onTick(ClientBotTick event) {
//        if (!enabledSetting()) return;
//        fire(TraderEvent.TICK);
//    }
//
//    private void fire(TraderEvent evt) {
//        currentState = fsm.fireEvent(currentState, evt, ctx);
//    }
//
//    // === 构建状态机 ===
//    private void buildStateMachine() {
//        StateMachineBuilder<TraderState, TraderEvent, TraderContext> b = StateMachineBuilderFactory.create();
//
//        // 统一：所有状态在 TICK 时都走各自的 internalTransition（核心重构点）
//        for (TraderState s : TraderState.values()) {
//            b.internalTransition().within(s).on(TraderEvent.TICK).perform(doTick(s));
//        }
//
//        // 事件驱动的外部迁移（异步回调会用到）
//        b.externalTransition().from(TraderState.RESTOCK_PATHING_TO_CHEST).to(TraderState.RESTOCK_WITHDRAWING_FROM_CHEST).on(TraderEvent.CHEST_OPENED).perform(Actions.noop());
//        b.externalTransition().from(TraderState.RESTOCK_WITHDRAWING_FROM_CHEST).to(TraderState.RESTOCK_CRAFT_EMERALD_BLOCKS).on(TraderEvent.WITHDRAW_DONE_BLOCKS_PRESENT).perform(Actions.noop());
//        b.externalTransition().from(TraderState.RESTOCK_WITHDRAWING_FROM_CHEST).to(TraderState.TRADING_INTERACT_WITH_VILLAGER).on(TraderEvent.WITHDRAW_DONE_NO_BLOCKS).perform(Actions.noop());
//
//        b.externalTransition().from(TraderState.RESTOCK_CRAFT_EMERALD_BLOCKS).to(TraderState.RESTOCK_AWAIT_CRAFT_EMERALD_BLOCKS).on(TraderEvent.START_CRAFT).perform(Actions.noop());
//        b.externalTransition().from(TraderState.RESTOCK_AWAIT_CRAFT_EMERALD_BLOCKS).to(TraderState.RESTOCK_CRAFT_EMERALD_BLOCKS).on(TraderEvent.CRAFT_MORE).perform(Actions.noop());
//        b.externalTransition().from(TraderState.RESTOCK_AWAIT_CRAFT_EMERALD_BLOCKS).to(TraderState.TRADING_INTERACT_WITH_VILLAGER).on(TraderEvent.CRAFT_DONE).perform(Actions.noop());
//
//        b.externalTransition().from(TraderState.TRADING_INTERACT_WITH_VILLAGER).to(TraderState.TRADING_AWAIT_INTERACT_WITH_VILLAGER).on(TraderEvent.START_INTERACT).perform(Actions.noop());
//        b.externalTransition().from(TraderState.TRADING_AWAIT_INTERACT_WITH_VILLAGER).to(TraderState.TRADING_TRY_START_PURCHASE).on(TraderEvent.OFFERS_READY).perform(Actions.noop());
//        b.externalTransition().from(TraderState.TRADING_TRY_START_PURCHASE).to(TraderState.TRADING_AWAIT_PURCHASE).on(TraderEvent.START_PURCHASE).perform(Actions.noop());
//        b.externalTransition().from(TraderState.TRADING_AWAIT_PURCHASE).to(TraderState.TRADING_INTERACT_WITH_VILLAGER).on(TraderEvent.PURCHASE_CONTINUE).perform(Actions.noop());
//        b.externalTransition().from(TraderState.TRADING_AWAIT_PURCHASE).to(TraderState.RESTOCK_GO_TO_CHEST).on(TraderEvent.PURCHASE_NEED_RESTOCK).perform(Actions.noop());
//        b.externalTransition().from(TraderState.TRADING_AWAIT_PURCHASE).to(TraderState.STORE_GO_TO_CHEST).on(TraderEvent.PURCHASE_NEED_STORE).perform(Actions.noop());
//
//        b.externalTransition().from(TraderState.STORE_GO_TO_CHEST).to(TraderState.STORE_DEPOSIT).on(TraderEvent.CHEST_REACHED_FOR_STORE).perform(Actions.noop());
//        b.externalTransition().from(TraderState.STORE_DEPOSIT).to(TraderState.STORE_AWAIT_DEPOSIT).on(TraderEvent.START_DEPOSIT).perform(Actions.noop());
//        b.externalTransition().from(TraderState.STORE_AWAIT_DEPOSIT).to(TraderState.RESTOCK_GO_TO_CHEST).on(TraderEvent.DEPOSIT_DONE).perform(Actions.noop());
//
//        b.externalTransition().from(TraderState.WAITING_FOR_VILLAGER_TRADE_RESTOCK).to(TraderState.RESTOCK_GO_TO_CHEST).on(TraderEvent.RESTOCK_TIMER_UP).perform(Actions.noop());
//
//        this.fsm = b.build("villager-trader-fsm");
//    }
//
//    // === 每个状态的 tick 行为 ===
//    private Action<TraderState, TraderEvent, TraderContext> doTick(TraderState s) {
//        return (from, to, event, c) -> {
//            switch (s) {
//                case RESTOCK_GO_TO_CHEST -> tickRestockGoToChest(c);
//                case RESTOCK_PATHING_TO_CHEST -> tickRestockPathingToChest(c);
//                case RESTOCK_WITHDRAWING_FROM_CHEST -> tickRestockWithdrawing(c);
//                case RESTOCK_CRAFT_EMERALD_BLOCKS -> tickRestockCraft(c);
//                case RESTOCK_AWAIT_CRAFT_EMERALD_BLOCKS -> tickAwaitCraft(c);
//                case TRADING_INTERACT_WITH_VILLAGER -> tickTradingInteract(c);
//                case TRADING_AWAIT_INTERACT_WITH_VILLAGER -> tickAwaitInteract(c);
//                case TRADING_TRY_START_PURCHASE -> tickTryStartPurchase(c);
//                case TRADING_AWAIT_PURCHASE -> tickAwaitPurchase(c);
//                case STORE_GO_TO_CHEST -> tickStoreGoToChest(c);
//                case STORE_DEPOSIT -> tickStoreDeposit(c);
//                case STORE_AWAIT_DEPOSIT -> tickStoreAwaitDeposit(c);
//                case WAITING_FOR_VILLAGER_TRADE_RESTOCK -> tickWaitVillagerRestock(c);
//            }
//        };
//    }
//
//    // === 各状态下的逻辑（把原 switch 分支搬过来，必要处触发 fsm 事件） ===
//    private void tickRestockGoToChest(TraderContext c) {
//        int emerald = countItem(ItemRegistry.EMERALD.id());
//        int blocks = countItem(ItemRegistry.EMERALD_BLOCK.id());
//        if (emerald + blocks * 9 < PLUGIN_CONFIG.restockEmeraldCountThreshold) {
//            var p = PLUGIN_CONFIG.restockChest;
//            c.restockPathingFuture = BARITONE.rightClickBlock(p.x(), p.y(), p.z());
//            c.restockPathingFuture.addExecutedListener(f -> c.waitForInteractTimer.reset());
//            currentState = TraderState.RESTOCK_PATHING_TO_CHEST; // 同步状态
//        } else if (blocks > 0) {
//            currentState = TraderState.RESTOCK_CRAFT_EMERALD_BLOCKS;
//        } else {
//            currentState = TraderState.TRADING_INTERACT_WITH_VILLAGER;
//        }
//    }
//
//    private void tickRestockPathingToChest(TraderContext c) {
//        if (!c.restockPathingFuture.isCompleted()) return;
//        var open = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
//        if (open.getContainerId() != 0) {
//            var actions = new ArrayList<InventoryAction>();
//            actions.add(InventoryActionMacros.withdraw(open.getContainerId(),
//                    i -> i.getId() == ItemRegistry.EMERALD.id() || i.getId() == ItemRegistry.EMERALD_BLOCK.id(),
//                    PLUGIN_CONFIG.restockStacks));
//            actions.add(new CloseContainer(open.getContainerId()));
//            c.restockWithdrawFuture = INVENTORY.submit(InventoryActionRequest.builder()
//                    .owner(this).actions(actions).priority(PRIORITY).build());
//            fire(TraderEvent.CHEST_OPENED);
//        } else if (c.waitForInteractTimer.tick(PLUGIN_CONFIG.waitForInteractTimeoutTicks)) {
//            currentState = TraderState.RESTOCK_GO_TO_CHEST;
//        }
//    }
//
//    private void tickRestockWithdrawing(TraderContext c) {
//        if (!c.restockWithdrawFuture.isCompleted()) return;
//        int emerald = countItem(ItemRegistry.EMERALD.id());
//        int blocks = countItem(ItemRegistry.EMERALD_BLOCK.id());
//        if (emerald + blocks * 9 < PLUGIN_CONFIG.restockEmeraldCountThreshold) {
//            warn("We have fewer than {} emeralds after restocking, trying to continue trading anyway", PLUGIN_CONFIG.restockEmeraldCountThreshold);
//        }
//        if (blocks > 0) fire(TraderEvent.WITHDRAW_DONE_BLOCKS_PRESENT);
//        else fire(TraderEvent.WITHDRAW_DONE_NO_BLOCKS);
//    }
//
//    private void tickRestockCraft(TraderContext c) {
//        int blocks = countItem(ItemRegistry.EMERALD_BLOCK.id());
//        if (blocks == 0) {
//            currentState = TraderState.TRADING_INTERACT_WITH_VILLAGER;
//            return;
//        }
//        int empty = countInvEmptySlots();
//        if (empty < 4) {
//            currentState = TraderState.TRADING_INTERACT_WITH_VILLAGER;
//            return;
//        }
//        int slot = InventoryUtil.searchPlayerInventory(i -> i.getId() == ItemRegistry.EMERALD_BLOCK.id());
//        if (slot == -1) {
//            currentState = TraderState.TRADING_INTERACT_WITH_VILLAGER;
//            return;
//        }
//
//        List<InventoryAction> actions = new ArrayList<>();
//        actions.add(new PlaceRecipe(0, "minecraft:emerald", true));
//        actions.add(new ShiftClick(0, ShiftClickItemAction.LEFT_CLICK));
//        actions.add(new CloseContainer(0));
//        c.emeraldBlockCraftFuture = INVENTORY.submit(InventoryActionRequest.builder()
//                .owner(this).actions(actions).priority(PRIORITY).build());
//        fire(TraderEvent.START_CRAFT);
//    }
//
//    private void tickAwaitCraft(TraderContext c) {
//        if (!c.emeraldBlockCraftFuture.isCompleted()) return;
//        int blocks = countItem(ItemRegistry.EMERALD_BLOCK.id());
//        if (blocks > 0) fire(TraderEvent.CRAFT_MORE);
//        else fire(TraderEvent.CRAFT_DONE);
//    }
//
//    private void tickTradingInteract(TraderContext c) {
//        int buyItemSlots = countBuyItemSlotUsages();
//        if (buyItemSlots > PLUGIN_CONFIG.buyItemStoreStacksThreshold) {
//            currentState = TraderState.STORE_GO_TO_CHEST;
//            return;
//        }
//
//        var next = nextVillager();
//        if (next.isEmpty()) {
//            if (c.interactedVillagersCache.asMap().isEmpty()) {
//                warn("No villagers found to trade with, going back to restock chest");
//                currentState = TraderState.RESTOCK_GO_TO_CHEST;
//                return;
//            } else {
//                if (countBuyItem() > 0) {
//                    currentState = TraderState.STORE_GO_TO_CHEST;
//                    return;
//                }
//                currentState = TraderState.WAITING_FOR_VILLAGER_TRADE_RESTOCK;
//                c.waitForRestockTimer.reset();
//                inGameAlert("Waiting for villagers to restock trades");
//                info("Waiting {}s for villagers to restock trades", PLUGIN_CONFIG.villagerTradeRestockWaitSeconds);
//                return;
//            }
//        }
//
//        var villager = next.get();
//        ctx.offersPacket = null;
//        c.interactWithVillagerFuture = BARITONE.rightClickEntity(villager);
//        c.interactWithVillagerFuture.addExecutedListener(f -> c.waitForInteractTimer.reset());
//        c.interactedVillagersCache.put(villager.getEntityId(), true);
//        fire(TraderEvent.START_INTERACT);
//    }
//
//    private void tickAwaitInteract(TraderContext c) {
//        if (!c.interactWithVillagerFuture.isCompleted()) return;
//        if (ctx.offersPacket == null) {
//            if (c.waitForInteractTimer.tick(PLUGIN_CONFIG.waitForInteractTimeoutTicks))
//                currentState = TraderState.TRADING_INTERACT_WITH_VILLAGER;
//            return;
//        }
//        if (ctx.offersPacket.getContainerId() != CACHE.getPlayerCache().getInventoryCache().getOpenContainerId()) {
//            if (c.waitForInteractTimer.tick(PLUGIN_CONFIG.waitForInteractTimeoutTicks))
//                currentState = TraderState.TRADING_INTERACT_WITH_VILLAGER;
//            return;
//        }
//        fire(TraderEvent.OFFERS_READY);
//    }
//
//    private void tickTryStartPurchase(TraderContext c) {
//        IntSet buyItemIds = getBuyItemIds();
//        var trades = ctx.offersPacket.getTrades();
//        List<InventoryAction> actions = new ArrayList<>();
//        for (int i = 0; i < trades.length; i++) {
//            var t = trades[i];
//            if (t.isTradeDisabled()) continue;
//            if (t.getOutput() == null) continue;
//            if (!buyItemIds.contains(t.getOutput().getId())) continue;
//            if (t.getFirstInput().getId() != ItemRegistry.EMERALD.id()) continue;
//            if (t.getSecondInput() != null) continue;
//            if (!matchesDesiredEnchantments(t.getOutput())) continue;
//
//            int inputStack = 64;
//            int baseCost = t.getFirstInput().getAmount();
//            int addnl = Math.max(0, MathHelper.floorI((t.getFirstInput().getAmount() * t.getDemand() * t.getPriceMultiplier())));
//            int cost = MathHelper.clamp(baseCost + addnl + t.getSpecialPrice(), 1, inputStack);
//            if (cost > PLUGIN_CONFIG.maxSpendPerTrade) continue;
//            int available = t.getMaxUses() - t.getNumUses() - 1;
//            if (available <= 0) continue;
//            int maxPerInput = inputStack / cost;
//            int outStack = ItemRegistry.REGISTRY.get(t.getOutput().getId()).stackSize();
//            int maxPerOutput = outStack / t.getOutput().getAmount();
//            int perShift = Math.min(maxPerInput, maxPerOutput);
//            for (int j = 0; j < available; j += perShift) {
//                actions.add(new SelectTrade(ctx.offersPacket.getContainerId(), i));
//                actions.add(new ShiftClick(ctx.offersPacket.getContainerId(), 2, ShiftClickItemAction.LEFT_CLICK));
//            }
//        }
//        actions.add(new CloseContainer(ctx.offersPacket.getContainerId()));
//        c.purchaseFuture = INVENTORY.submit(InventoryActionRequest.builder().owner(this).priority(PRIORITY).actions(actions).build());
//        fire(TraderEvent.START_PURCHASE);
//    }
//
//    private void tickAwaitPurchase(TraderContext c) {
//        if (!c.purchaseFuture.isCompleted()) return;
//        if (countBuyItemSlotUsages() > PLUGIN_CONFIG.buyItemStoreStacksThreshold) {
//            fire(TraderEvent.PURCHASE_NEED_STORE);
//            return;
//        } else if (countItem(ItemRegistry.EMERALD.id()) < PLUGIN_CONFIG.restockEmeraldCountThreshold) {
//            fire(TraderEvent.PURCHASE_NEED_RESTOCK);
//            return;
//        } else {
//            fire(TraderEvent.PURCHASE_CONTINUE);
//        }
//    }
//
//    private void tickStoreGoToChest(TraderContext c) {
//        var p = PLUGIN_CONFIG.storeChest;
//        c.storePathingFuture = BARITONE.rightClickBlock(p.x(), p.y(), p.z());
//        c.storePathingFuture.addExecutedListener(f -> c.waitForInteractTimer.reset());
//        fire(TraderEvent.CHEST_REACHED_FOR_STORE);
//    }
//
//    private void tickStoreDeposit(TraderContext c) {
//        if (!c.storePathingFuture.isCompleted()) return;
//        var open = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
//        if (open.getContainerId() == 0) {
//            if (c.waitForInteractTimer.tick(PLUGIN_CONFIG.waitForInteractTimeoutTicks))
//                currentState = TraderState.STORE_GO_TO_CHEST;
//            return;
//        }
//        var outputIds = getBuyItemIds();
//        var actions = new ArrayList<InventoryAction>();
//        actions.add(InventoryActionMacros.deposit(open.getContainerId(), i -> outputIds.contains(i.getId())));
//        actions.add(new CloseContainer(open.getContainerId()));
//        c.storeDepositFuture = INVENTORY.submit(InventoryActionRequest.builder().owner(this).priority(PRIORITY).actions(actions).build());
//        c.storePathingFuture.addExecutedListener(f -> c.waitForInteractTimer.reset());
//        fire(TraderEvent.START_DEPOSIT);
//    }
//
//    private void tickStoreAwaitDeposit(TraderContext c) {
//        if (!c.storeDepositFuture.isCompleted()) return;
//        int buyCount = countBuyItem();
//        if (buyCount > 0) {
//            if (c.waitForInteractTimer.tick(PLUGIN_CONFIG.waitForInteractTimeoutTicks)) {
//                warn("Unable to fully deposit buy items, trying to continue anyway");
//                currentState = TraderState.RESTOCK_GO_TO_CHEST;
//            }
//            return;
//        }
//        fire(TraderEvent.DEPOSIT_DONE);
//    }
//
//    private void tickWaitVillagerRestock(TraderContext c) {
//        if (c.waitForRestockTimer.tick(20L * PLUGIN_CONFIG.villagerTradeRestockWaitSeconds)) {
//            c.interactedVillagersCache.invalidateAll();
//            fire(TraderEvent.RESTOCK_TIMER_UP);
//        }
//    }
//
//    // ======= 业务工具（基本保持原样） =======
//    private IntSet getBuyItemIds() {
//        IntSet ids = new IntOpenHashSet();
//        for (var iter = PLUGIN_CONFIG.buyItems.iterator(); iter.hasNext(); ) {
//            String name = iter.next();
//            var data = ItemRegistry.REGISTRY.get(name);
//            if (data != null) ids.add(data.id());
//            else {
//                warn("Buy item {} not found in registry, removing", name);
//                iter.remove();
//            }
//        }
//        return ids;
//    }
//
//    private boolean matchesDesiredEnchantments(ItemStack itemStack) {
//        if (!EnchantmentUtil.isEnchantedBook(itemStack)) return true;
//        Map<String, Integer> book = EnchantmentUtil.getEnchantmentMap(itemStack);
//        if (PLUGIN_CONFIG.onlyBuyMaxLevelEnchantments) {
//            for (Map.Entry<String, Integer> e : book.entrySet()) {
//                Integer max = EnchantmentUtil.MAX_LEVEL_MAP.get(e.getKey());
//                if (max != null && e.getValue() < max) return false;
//            }
//        }
//        if (!PLUGIN_CONFIG.onlyBuyDesiredEnchantments || PLUGIN_CONFIG.desiredEnchantments.isEmpty()) return true;
//        for (Map.Entry<String, Integer> d : PLUGIN_CONFIG.desiredEnchantments.entrySet()) {
//            Integer actual = book.get(d.getKey());
//            if (actual == null || actual < d.getValue()) return false;
//        }
//        return true;
//    }
//
//    private Optional<EntityLiving> nextVillager() {
//        return CACHE.getEntityCache().getEntities().values().stream()
//                .filter(e -> e.getEntityType() == EntityType.VILLAGER)
//                .filter(e -> !ctx.interactedVillagersCache.asMap().containsKey(e.getEntityId()))
//                .map(e -> (EntityLiving) e)
//                .filter(e -> PLUGIN_CONFIG.villagerProfessions.contains(getVillagerProfession(e)))
//                .min(Comparator.comparingDouble(e -> e.distanceSqTo(CACHE.getPlayerCache().getThePlayer())));
//    }
//
//    private VillagerProfession getVillagerProfession(EntityLiving villager) {
//        var data = villager.getMetadataValue(18, MetadataTypes.VILLAGER_DATA, VillagerData.class);
//        if (data == null) return VillagerProfession.NONE;
//        return VillagerProfession.from(data.getProfession());
//    }
//
//    private int countItem(int id) {
//        int c = 0;
//        var inv = CACHE.getPlayerCache().getPlayerInventory();
//        for (int i = 9; i <= 44; i++) {
//            var item = inv.get(i);
//            if (item == Container.EMPTY_STACK) continue;
//            if (item.getId() == id) c += item.getAmount();
//        }
//        return c;
//    }
//
//    private int countInvEmptySlots() {
//        int c = 0;
//        var inv = CACHE.getPlayerCache().getPlayerInventory();
//        for (int i = 9; i <= 44; i++) if (inv.get(i) == Container.EMPTY_STACK) c++;
//        return c;
//    }
//
//    private int countBuyItem() {
//        int c = 0;
//        for (int id : getBuyItemIds()) c += countItem(id);
//        return c;
//    }
//
//    private int countSlotUsages(int id) {
//        int c = 0;
//        var inv = CACHE.getPlayerCache().getPlayerInventory();
//        for (int i = 9; i <= 44; i++) {
//            var item = inv.get(i);
//            if (item == Container.EMPTY_STACK) continue;
//            if (item.getId() == id) c++;
//        }
//        return c;
//    }
//
//    private int countBuyItemSlotUsages() {
//        int c = 0;
//        for (int id : getBuyItemIds()) c += countSlotUsages(id);
//        return c;
//    }
//
//    // ======= 定义状态 / 事件 / 上下文 =======
//    public enum TraderState {
//        RESTOCK_GO_TO_CHEST,
//        RESTOCK_PATHING_TO_CHEST,
//        RESTOCK_WITHDRAWING_FROM_CHEST,
//        RESTOCK_CRAFT_EMERALD_BLOCKS,
//        RESTOCK_AWAIT_CRAFT_EMERALD_BLOCKS,
//        TRADING_INTERACT_WITH_VILLAGER,
//        TRADING_AWAIT_INTERACT_WITH_VILLAGER,
//        TRADING_TRY_START_PURCHASE,
//        TRADING_AWAIT_PURCHASE,
//        STORE_GO_TO_CHEST,
//        STORE_DEPOSIT,
//        STORE_AWAIT_DEPOSIT,
//        WAITING_FOR_VILLAGER_TRADE_RESTOCK
//    }
//
//    public enum TraderEvent {
//        TICK,
//        CHEST_OPENED,
//        WITHDRAW_DONE_BLOCKS_PRESENT,
//        WITHDRAW_DONE_NO_BLOCKS,
//        START_CRAFT,
//        CRAFT_MORE,
//        CRAFT_DONE,
//        START_INTERACT,
//        OFFERS_READY,
//        START_PURCHASE,
//        PURCHASE_NEED_STORE,
//        PURCHASE_NEED_RESTOCK,
//        PURCHASE_CONTINUE,
//        CHEST_REACHED_FOR_STORE,
//        START_DEPOSIT,
//        DEPOSIT_DONE,
//        RESTOCK_TIMER_UP
//    }
//
//    public static class TraderContext {
//        Cache<Integer, Boolean> interactedVillagersCache = CacheBuilder.newBuilder().build();
//        PathingRequestFuture restockPathingFuture = PathingRequestFuture.rejected;
//        RequestFuture restockWithdrawFuture = RequestFuture.rejected;
//        RequestFuture emeraldBlockCraftFuture = RequestFuture.rejected;
//        PathingRequestFuture interactWithVillagerFuture = PathingRequestFuture.rejected;
//        ClientboundMerchantOffersPacket offersPacket = null;
//        RequestFuture purchaseFuture = RequestFuture.rejected;
//        PathingRequestFuture storePathingFuture = PathingRequestFuture.rejected;
//        RequestFuture storeDepositFuture = RequestFuture.rejected;
//        Timer waitForRestockTimer = Timers.tickTimer();
//        Timer waitForInteractTimer = Timers.tickTimer();
//    }
//
//    // ======= 原枚举（保留以兼容其它代码） =======
//    public enum VillagerProfession {
//        NONE, ARMORER, BUTCHER, CARTOGRAPHER, CLERIC, FARMER, FISHERMAN, FLETCHER, LEATHERWORKER, LIBRARIAN, MASON, NITWIT, SHEPHERD, TOOLSMITH, WEAPONSMITH;
//        private static final VillagerProfession[] VALUES = values();
//
//        public static VillagerProfession from(int id) {
//            return VALUES[id];
//        }
//    }
//}
