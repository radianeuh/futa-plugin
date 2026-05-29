package com.github.futa.module;

import com.github.futa.FutaPlugin;
import com.github.futa.config.AutoVaultOpenerConfig;
import com.github.futa.dto.Ticket;
import com.github.futa.util.NodeClient;
import com.github.rfresh2.EventConsumer;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.zenith.Globals;
import com.zenith.Proxy;
import com.zenith.cache.data.chunk.ChunkCache;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.chat.SystemChatEvent;
import com.zenith.event.client.ClientBotTick;
import com.zenith.event.client.ClientConnectEvent;
import com.zenith.event.client.ClientDisconnectEvent;
import com.zenith.event.client.ClientOnlineEvent;
import com.zenith.event.module.ServerPlayerInVisualRangeEvent;
import com.zenith.event.module.ServerPlayerLeftVisualRangeEvent;
import com.zenith.event.module.ServerPlayerLogoutInVisualRangeEvent;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.*;
import com.zenith.feature.pathfinder.BlockStateInterface;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.feature.pathfinder.goals.GoalBlock;
import com.zenith.feature.player.InputRequest;
import com.zenith.feature.player.World;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.mc.block.properties.VaultState;
import com.zenith.mc.block.properties.api.BlockStateProperties;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.impl.AbstractInventoryModule;
import com.zenith.network.client.Authenticator;
import com.zenith.util.ChatUtil;
import com.zenith.util.RequestFuture;
import com.zenith.util.math.MathHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.DropItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;
import static com.zenith.util.config.Config.Authentication.AccountType.OFFLINE;

public class AutoVaultOpenerModule extends AbstractInventoryModule {

    private final AutoVaultOpenerConfig config = FutaPlugin.PLUGIN_CONFIG.autoVaultOpen;

    ScheduledExecutorService TIMEOUT_EXECUTOR = Executors.newScheduledThreadPool(4, new ThreadFactoryBuilder()
            .setNameFormat("TIMEOUT Scheduled Executor - #%d")
            .setDaemon(true)
            .setUncaughtExceptionHandler((thread, e) -> DEFAULT_LOG.error("Uncaught exception in scheduled executor thread {}", thread, e))
            .build());

    static NodeClient nodeClient = new NodeClient("http://ticket.mcb.com:9000");

    static {
        nodeClient.log = FutaPlugin.log;
    }

    // 状态管理
    private volatile boolean isRunning = false;
    private volatile ScheduledFuture<?> nextActionFuture;
    private volatile ScheduledFuture<?> accountTimeoutChecker;
    private volatile ScheduledFuture<?> ticketPollingFuture;

    private PathingRequestFuture clickFuture = PathingRequestFuture.rejected;
    private RequestFuture withdrawFuture = RequestFuture.rejected;

    private int delay = 0;
    private boolean isUsingKey = false;

    // Ticket相关状态
    private volatile String currentTicketId = null;
    private volatile boolean waitingForTicket = false;

    //===================status===================

    private volatile State currentState = State.IDLE;

    private volatile Instant lastShulkerSearchTime = Instant.EPOCH;
    private volatile Instant lastActionTime = Instant.EPOCH;
    private volatile Instant accountStartTime = Instant.EPOCH;

    private volatile int currentVaultIndex = 0;

    private volatile boolean reachedTargetPosition = false;

    private volatile int whisperCount = 0;
    private volatile int retryCount = 0;
    private volatile int checkVaultRetryTime = 0;
    private volatile int switchKeyRetryCount = 0;
    AutoVaultOpenerConfig.VaultInfo vaultCurrent = new AutoVaultOpenerConfig.VaultInfo();
    // 使用 ConcurrentHashMap 的 keySet 来存储玩家名，线程安全
    private final ConcurrentHashMap<String, Boolean> playersInRange = new ConcurrentHashMap<>();
    //===================status===================

    // 记录失败账号和原因
    private static class FailedAccount {
        String username;
        String reason;

        FailedAccount(String username, String reason) {
            this.username = username;
            this.reason = reason;
        }
    }

    private final List<FailedAccount> failedAccounts = new ArrayList<>();
    private static final String FAILED_ACCOUNTS_FILE = "failed_accounts.txt";

    // 当前状态
    public enum State {
        IDLE,
        CONNECTING,
        WAITING_FOR_LOGIN,
        WAITING_FOR_INGAME,
        GOTO_LOCATION,
        WAITING_FOR_CHEST_OPEN,
        WAITING_FOR_CHEST_CLOSE,
        GETTING_KEY,
        TAKING_KEY,
        SEARCHING_VAULT,
        OPENING_VAULT,
        WAITING_VAULT,
        DISCONNECTING,
        WAITING_BETWEEN_ACCOUNTS,
        CHECKING_KEY_CONTAINER,
        WAITING_FOR_KEY_CONTAINER_OPEN
    }


    public AutoVaultOpenerModule() {
        super(HandRestriction.MAIN_HAND, 0);
    }

    @Override
    public boolean itemPredicate(ItemStack itemStack) {
        return itemStack != null && itemStack.getId() == ItemRegistry.OMINOUS_TRIAL_KEY.id();
    }

    @Override
    public int getPriority() {
        return 600;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientConnectEvent.class, this::onClientConnect),
                of(ClientOnlineEvent.class, this::onClientOnline),
                of(ClientDisconnectEvent.class, this::onClientDisconnect),
                of(ServerPlayerInVisualRangeEvent.class, this::handleNewPlayerInVisualRangeEvent),
                of(ServerPlayerLeftVisualRangeEvent.class, this::handlePlayerLeftVisualRangeEvent),
                of(ServerPlayerLogoutInVisualRangeEvent.class, this::handlePlayerLogoutInVisualRangeEvent),
                of(ClientBotTick.class, this::onClientTick),
                of(SystemChatEvent.class, this::onSystemChat)
        );
    }

    @Override
    public boolean enabledSetting() {
        return config.enabled;
    }

    @Override
    public void onEnable() {
        info("自动开宝库模块已启用");

        if (config.accounts.isEmpty()) {
            warn("没有配置账号，请先在配置文件中添加账号信息");
            return;
        }
        FutaPlugin.PLUGIN_CONFIG.autoReboot = false;
        CONFIG.client.extra.killAura.enabled = false;

        if (config.reverseCycle) {
            config.reverseCycle = false;
            config.accounts = config.accounts.reversed();
        }

        // 显示当前进度信息
        if (config.currentAccountIndex >= config.accounts.size()) {
            info("所有账号已完成");
            if (config.loopAccounts || config.justLogin) {
                info("配置为循环，将重新开始");
                config.currentAccountIndex = 0;
            } else {
                info("配置为结束，将停止自动开宝库流程");
                stopVaultOpening();
                config.currentAccountIndex = 0;
                return;
            }
        } else {
            info("当前进度：第 {}/{} 个账号", config.currentAccountIndex + 1, config.accounts.size());
        }
        currentVaultIndex = 0;
        reachedTargetPosition = false;
        startVaultOpening();
    }

    @Override
    public void onDisable() {
        info("自动开宝库模块已禁用");
        stopVaultOpening();
        // 确保停止账号超时检查器
        stopAccountTimeoutChecker();
    }

    public void handleNewPlayerInVisualRangeEvent(ServerPlayerInVisualRangeEvent event) {
        String name = event.playerEntry().getName();
        playersInRange.put(name, true);
    }

    public void handlePlayerLeftVisualRangeEvent(ServerPlayerLeftVisualRangeEvent event) {
        String name = event.playerEntry().getName();
        playersInRange.remove(name);
    }

    public void handlePlayerLogoutInVisualRangeEvent(ServerPlayerLogoutInVisualRangeEvent event) {
        String name = event.playerEntry().getName();
        playersInRange.remove(name);
    }

    // 提供查询接口
    public Set<String> getPlayersInRange() {
        return playersInRange.keySet();
    }

    private void startVaultOpening() {
        if (isRunning) return;
        isRunning = true;
        retryCount = 0;
        info("开始自动开宝库流程，从第 {} 个账号开始", config.currentAccountIndex + 1);
        processNextAccount();
    }

    private void stopVaultOpening() {
        isRunning = false;
        if (nextActionFuture != null) {
            nextActionFuture.cancel(false);
            nextActionFuture = null;
        }

        // 停止账号超时检查器
        stopAccountTimeoutChecker();

        // 停止ticket轮询
        stopTicketPolling();

        currentState = State.IDLE;


        writeFailedAccountsToFile();
        printFailedAccountsSummary();

    }

    private void processNextAccount() {
        if (!isRunning) return;

        // 停止之前的ticket轮询任务
        stopTicketPolling();

        // 开始获取ticket
        waitingForTicket = true;
        startTicketPolling();
    }

    private void startTicketPolling() {
        if (ticketPollingFuture != null && !ticketPollingFuture.isCancelled()) {
            return;
        }

        ticketPollingFuture = EXECUTOR.scheduleAtFixedRate(() -> {
            if (!isRunning || !waitingForTicket) return;

            Ticket ticket = nodeClient.requestTicket();
            if (ticket != null) {
                // 成功获取ticket，停止轮询
                waitingForTicket = false;
                currentTicketId = ticket.getTicketId();

                // 根据ticket的number-1决定使用哪个账号
                int accountIndex = ticket.getNumber() - 1;
                if (accountIndex < 0 || accountIndex >= config.accounts.size()) {
                    warn("Ticket number {} 超出账号范围，使用第一个账号", ticket.getNumber());
                    accountIndex = 0;
                }

                config.currentAccountIndex = accountIndex;
                config.justLogin = ticket.isJustLogin();
                info("获取到ticket: {}, 号码: {}, 使用账号索引: {}", ticket.getTicketId(), ticket.getNumber(), accountIndex);

                // 停止ticket轮询
                stopTicketPolling();

                // 开始处理账号
                processAccount(accountIndex);


            } else {
                info("等待获取ticket...");
            }
        }, 0, 5, TimeUnit.SECONDS);

        info("开始轮询获取ticket，间隔5秒");
    }

    private void stopTicketPolling() {
        if (ticketPollingFuture != null && !ticketPollingFuture.isCancelled()) {
            ticketPollingFuture.cancel(false);
            ticketPollingFuture = null;
        }
    }

    private void processAccount(int accountIndex) {
        if (!isRunning) return;

        if (accountIndex < 0 || accountIndex >= config.accounts.size()) {
            error("账号索引 {} 无效", accountIndex);
            return;
        }

        config.currentAccountIndex = accountIndex;

        writeFailedAccountsToFile();

        currentVaultIndex = 0;
        switchKeyRetryCount = 0; // 重置切换钥匙重试计数器

        // 启动独立的账号超时检查器
        startAccountTimeoutChecker();

        AutoVaultOpenerConfig.AccountInfo account = config.accounts.get(accountIndex);
        info("处理账号: {} (第 {}/{} 个)", account.username, accountIndex + 1, config.accounts.size());

        // 设置认证信息
        CONFIG.authentication.username = account.username;
        CONFIG.authentication.password = account.password;

        Globals.CONFIG.server.verifyUsers = false;
        CONFIG.authentication.accountType = OFFLINE;

        Proxy.getInstance().cancelLogin();
        Authenticator.INSTANCE.clearAuthCache();

        currentState = State.CONNECTING;
        Proxy.getInstance().connect();
    }

    private void onClientConnect(ClientConnectEvent event) {
        if (!isRunning) return;
        info("客户端连接中...");
        // 重置超时计时器，适配NodeClient异步连接
        lastActionTime = Instant.now();
        accountStartTime = Instant.now();
        currentState = State.WAITING_FOR_LOGIN;
    }

    private void onClientOnline(ClientOnlineEvent event) {
        if (!isRunning) return;
        info("客户端已上线，准备发送登录命令");
        currentState = State.WAITING_FOR_LOGIN;

    }

    private void onClientDisconnect(ClientDisconnectEvent event) {
        if (!isRunning) return;
        info("客户端断开连接");

        if (currentState == State.DISCONNECTING) {
            // 正常断开，确认ticket
            if (currentTicketId != null) {
                boolean confirmed = nodeClient.confirmTicket(currentTicketId, config.justLogin);
                if (confirmed) {
                    info("Ticket {} 已确认", currentTicketId);
                } else {
                    warn("Ticket {} 确认失败", currentTicketId);
                }
                currentTicketId = null;
            }

            // 处理下一个账号（重新获取ticket）
            scheduleAction(this::processNextAccount, config.waitBetweenAccounts);

        } else {
            // 意外断开，重试
            handleError("意外断开连接");
        }
    }

    private void onClientTick(ClientBotTick event) {
        if (!isRunning) return;

        // 注意：账号超时检查现在由独立的定时器处理，不依赖于游戏循环

        // 检查是否卡住 - 跳过连接过程中的超时检查
        if (lastActionTime != Instant.EPOCH &&
                Duration.between(lastActionTime, Instant.now()).getSeconds() > config.timeoutSeconds) {
            warn("操作超时");
//            handleError("操作超时");


            if (currentVaultIndex + 1 < config.vaults.size()) {
                info("宝库1操作超时，开下一个");
                currentVaultIndex++;
                // 重置超时计时器，适配NodeClient连接切换
                lastActionTime = Instant.now();
                reachedTargetPosition = false;
                currentState = State.WAITING_FOR_INGAME;
                gotoLocation();
            } else {
                info("宝库2操作超时，断开连接");
                sendChatMessageNear("宝库操作超时，断开连接");

                stopAndNext();
            }
        }

        if (currentState == State.WAITING_FOR_INGAME) {
            if (!AutoLoginModule.isIn3cSpawn(CACHE.getPlayerCache().getThePlayer().getX(), CACHE.getPlayerCache().getThePlayer().getZ())) {
                gotoLocation();
            }
        }
        if (currentState == State.WAITING_FOR_CHEST_OPEN) {
            checkContainerOpen();
        }
        if (currentState == State.WAITING_FOR_CHEST_CLOSE) {
            checkContainerClose();
        }
        if (currentState == State.WAITING_FOR_KEY_CONTAINER_OPEN) {
            // 钥匙容器打开状态的处理在 countKeysInContainer 方法中
        }
    }

    private void onSystemChat(SystemChatEvent event) {
        if (!isRunning) return;

        String message = event.message();

        if (Strings.isNullOrEmpty(message)) {
            return;
        }

        //你必须输入验证码才能登入: /captcha csbvh
        if (message.contains("/captcha")) {
            info("bot输入验证码");

            sendChatMessage("/captcha " + extractCaptcha(message));
        }

        if (message.contains("/login")) {
            info("bot登录中");
            AutoVaultOpenerConfig.AccountInfo account = config.accounts.get(config.currentAccountIndex);
            sendChatMessage(config.loginCommand + " " + account.password);
        }


        // 检查登录是否成功 Logged-in due to Session Reconnection.
        if (message.contains("Logged-in") || message.contains("成功登录")) {
            info("bot登录成功");
//            currentState = State.WAITING_FOR_INGAME;
        }

        // 检查登录是否成功 Logged-in due to Session Reconnection.
        if (message.contains("Connecting to the server")) {
            info("bot 进入游戏服中");
            whisperCount = 0;
            playersInRange.clear();

            currentState = State.WAITING_FOR_INGAME;
        }


        // 检查是否被踢出
        if (message.contains("kicked") || message.contains("disconnected")) {
            warn("bot被服务器踢出");
            handleError("被服务器踢出");
        }
    }


    private void gotoLocation() {
        if (!isRunning) return;

        AutoVaultOpenerConfig.VaultInfo vault = config.vaults.get(currentVaultIndex);

        // 限流：确保每次执行间隔不低于1秒
        if (Duration.between(lastShulkerSearchTime, Instant.now()).toMillis() < 1000) {
            if (!reachedTargetPosition) {
                return;
            }
        }
        lastShulkerSearchTime = Instant.now();

//        CACHE.getPlayerCache().distanceSqToSelf()
        // 判断当前位置与目标点XZ距离
        int playerX = (int) CACHE.getPlayerCache().getX();
        int playerY = (int) CACHE.getPlayerCache().getY();
        int playerZ = (int) CACHE.getPlayerCache().getZ();
        double distance = MathHelper.manhattanDistance3d(
                playerX,
                playerY,
                playerZ,
                vault.vaultX,
                vault.vaultY,
                vault.vaultZ
        );

        if (distance >= config.pathingRange) {
            info("距离目标坐标过远（{}，玩家：{}，{}，{}，目标：{}，{}，{}），不执行寻路和后续操作", distance,
                    playerX, playerY, playerZ,
                    vault.vaultX, vault.vaultY, vault.vaultZ);

            return;
        }

        currentState = State.GOTO_LOCATION;

        //todo check key count
        dropAll();

        if (config.justLogin) {


            // 正常断开，处理下一个账号
            boolean wasLastAccount = isLastAccount();
            if (wasLastAccount) {
                // 在重新开始前检查钥匙数量（如果配置了钥匙容器位置）
                openKeyContainer();
            } else {

                info("仅登录模式，断开中 ");
                scheduleAction(this::stopAndNext, 2); // 递归调用，进入按按钮逻辑
            }
            return;
        }

        if (!reachedTargetPosition) {
            GoalBlock targetGoal = new GoalBlock(vault.vaultX, vault.vaultY, vault.vaultZ);

            info("前往目标坐标: {}, {}", vault.vaultX, vault.vaultZ);
            BARITONE.pathTo(targetGoal).addExecutedListener(f -> {
//                info("已到达目标坐标: {}, {}，开始搜索白桦木按钮", vault.vaultX, vault.vaultZ);
                info("已到达目标坐标: {}, {}，查找宝库", vault.vaultX, vault.vaultZ);
                reachedTargetPosition = true;
                scheduleAction(this::gotoLocation, 2); // 递归调用，进入按按钮逻辑
            });
            return;
        }

        if (config.justPathing) {
            info("仅寻路模式，断开中 ");

            scheduleAction(this::stopAndNext, 2); // 递归调用，进入按按钮逻辑
            return;
        }

        searchForVault();

    }

    /**
     * 丢掉 hotbar 中的所有物品
     */
    private void dropAllHotbarItems() {
        if (!isRunning) return;
        if (!config.allowDropHotbar) return;

        Container playerInventory = CACHE.getPlayerCache().getInventoryCache().getPlayerInventory();
        int size = playerInventory.getSize();
        List<InventoryAction> actions = new ArrayList<>();

        // drop all hotbar
        for (int slot = 36; slot < 45; slot++) {
            ItemStack item = playerInventory.getItemStack(slot);
            if (item != null && item != Container.EMPTY_STACK) {
                actions.add(new DropItem(playerInventory.getContainerId(), slot, DropItemAction.DROP_SELECTED_STACK));
            }
        }

        if (!actions.isEmpty()) {
            logWarning("丢掉 hotbar 中的所有物品...");
            INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actions(actions)
                    .priority(600)
                    .build());
        }
    }

    /**
     * 丢掉 hotbar 中的所有物品
     */
    private void dropAll() {
        if (!isRunning) return;
        if (!config.allowDropHotbar) return;

        Container playerInventory = CACHE.getPlayerCache().getInventoryCache().getPlayerInventory();
        int size = playerInventory.getSize();
        List<InventoryAction> actions = new ArrayList<>();

        // drop all hotbar
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = playerInventory.getItemStack(slot);
            if (item != null && item != Container.EMPTY_STACK) {
                actions.add(new DropItem(playerInventory.getContainerId(), slot, DropItemAction.DROP_SELECTED_STACK));
            }
        }

        INPUTS.submit(InputRequest.builder()
                .owner(this)
                .yaw(-90)
                .pitch(0)
                .priority(500)
                .build());

        if (!actions.isEmpty()) {
            logWarning("丢掉 hotbar 中的所有物品...");
            INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actions(actions)
                    .priority(600)
                    .build());
        }
    }


    private void pressButton(int x, int y, int z) {
        if (!isRunning) return;
        AutoVaultOpenerConfig.VaultInfo vault = config.vaults.get(currentVaultIndex);

        info("按下配置的按钮坐标: {}, {}, {}", vault.buttonX, vault.buttonY, vault.buttonZ);

        currentState = State.GETTING_KEY; // 复用原状态

        dropAllHotbarItems();

        BARITONE.rightClickBlock(vault.buttonX, vault.buttonY, vault.buttonZ).addExecutedListener(e -> {
            // 按下按钮后2秒进入开宝库流程
            vaultCurrent.vaultX = x;
            vaultCurrent.vaultY = y;
            vaultCurrent.vaultZ = z;
            scheduleAction(() -> openVault(), 2);
        });

    }

    private void openChest(int x, int y, int z) {
        if (!isRunning) return;

        currentState = State.GETTING_KEY; // 复用原状态

        dropAllHotbarItems();

        tryWithdrawOminousKey();

    }


    private void searchForShulker2() {
        if (!isRunning) return;
        info("搜索附近的潜影盒...");
        currentState = State.GOTO_LOCATION;

        // 搜索附近的潜影盒
        ChunkCache chunkCache = CACHE.getChunkCache();
        int playerX = (int) CACHE.getPlayerCache().getX();
        int playerY = (int) CACHE.getPlayerCache().getY();
        int playerZ = (int) CACHE.getPlayerCache().getZ();

        boolean foundShulker = false;

        // 在搜索范围内查找潜影盒
        for (int dx = -config.searchRadius; dx <= config.searchRadius; dx++) {
            for (int dy = -config.searchRadius; dy <= config.searchRadius; dy++) {
                for (int dz = -config.searchRadius; dz <= config.searchRadius; dz++) {
                    int x = playerX + dx;
                    int y = playerY + dy;
                    int z = playerZ + dz;

                    if (isShulkerBox(x, y, z)) {
                        info("找到潜影盒在坐标: {}, {}, {}", x, y, z);
                        openShulkerBox(x, y, z);
                        foundShulker = true;
                        break;
                    }
                }
                if (foundShulker) break;
            }
            if (foundShulker) break;
        }

        if (!foundShulker) {
            warn("未找到潜影盒");
            handleError("未找到潜影盒");
        }
    }

    private boolean isShulkerBox(int x, int y, int z) {
        ChunkCache chunkCache = CACHE.getChunkCache();
        int blockStateId = BlockStateInterface.getId(x, y, z);
        // 检查是否是潜影盒
        return blockStateId >= BlockRegistry.SHULKER_BOX.minStateId() &&
                blockStateId <= BlockRegistry.SHULKER_BOX.maxStateId();
    }

    private void openShulkerBox(int x, int y, int z) {
        if (!isRunning) return;
        info("打开潜影盒: {}, {}, {}", x, y, z);
        currentState = State.GETTING_KEY;

        // 发送右键点击包
        sendClientPacketAsync(new ServerboundUseItemOnPacket(
                x, y, z, Direction.UP, Hand.MAIN_HAND, 0, 0, 0, false, false, 0
        ));

        scheduleAction(this::takeKeyFromShulker, config.waitAfterShulkerOpen);
    }

    private void takeKeyFromShulker() {
        if (!isRunning) return;
        info("从潜影盒中取钥匙...");
        currentState = State.TAKING_KEY;

        // 检查打开的容器
        Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer == null) {
            warn("没有打开的容器");
            handleError("没有打开的容器");
            return;
        }

        // 查找不详钥匙
        int keySlot = -1;
        for (int i = 0; i < openContainer.getContents().size(); i++) {
            ItemStack item = openContainer.getContents().get(i);
            if (item != null && isOminousKey(item)) {
                keySlot = i;
                break;
            }
        }

        if (keySlot == -1) {
            warn("未找到不详钥匙");
            handleError("未找到不详钥匙");
            return;
        }

        // 点击取钥匙


        // 关闭容器
        scheduleAction(() -> {
            sendClientPacketAsync(new ServerboundContainerClosePacket(openContainer.getContainerId()));
            scheduleAction(this::searchForVault, 1);
        }, 1);
    }

    private int ominousKeyWithdrawAttempts = 0;
    private static final int MAX_WITHDRAW_ATTEMPTS = 3;
    private static final int MAX_SWITCH_KEY_ATTEMPTS = 5;

    private void tryWithdrawOminousKey() {
        if (ominousKeyWithdrawAttempts >= MAX_WITHDRAW_ATTEMPTS) {
            warn("Reached max attempts to withdraw Ominous Trial Key. Giving up.");
            return;
        }

        ominousKeyWithdrawAttempts++;

        AutoVaultOpenerConfig.VaultInfo vault = config.vaults.get(currentVaultIndex);

        info("打开箱子: {}, {}, {}", vault.buttonX, vault.buttonY, vault.buttonZ);


        // Step 1. 打开指定坐标的箱子

        clickFuture = BARITONE.rightClickBlock(vault.buttonX, vault.buttonY, vault.buttonZ);
        currentState = State.WAITING_FOR_CHEST_OPEN;
        vaultCurrent = new AutoVaultOpenerConfig.VaultInfo();

//        clickFuture.addExecutedListener(f -> waitForInteractTimer.reset());

    }


    private void checkContainerOpen() {
        if (clickFuture.isCompleted()) {
            var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
            int containerId = container.getContainerId();
            if (containerId != 0) {
                List<InventoryAction> actions = new ArrayList<>();
                for (int i = 0; i < 27; i++) {
                    ItemStack itemStack = container.getItemStack(i);
                    if (itemStack == Container.EMPTY_STACK) continue;

                    if (isOminousKey(itemStack)) {
                        actions.add(new ClickItem(i, ClickItemAction.LEFT_CLICK));

                        for (int slotid = 54; slotid <= 62; slotid++) {
                            ItemStack myslot = container.getItemStack(slotid);
                            if (myslot == null) {

                                actions.add(new ClickItem(slotid, ClickItemAction.RIGHT_CLICK));
                                actions.add(new ClickItem(i, ClickItemAction.LEFT_CLICK));
                                actions.add(new ShiftClick(i, ShiftClickItemAction.LEFT_CLICK));

                                actions.add(new CloseContainer(containerId));
                                break;
                            }
                        }
                        break;
                    }
                }

                // Step 2. 从箱子取出1个 OMINOUS_TRIAL_KEY
                if (actions.size() <= 1) {
//                    info("背包未找到空槽位");
                    dropAllHotbarItems();
                    tryWithdrawOminousKey();
                    return;
                }

                withdrawFuture = INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(actions)
                        .priority(500)
                        .build());

            }
        }
    }

    /**
     * 关闭当前容器
     */
    private void closeCurrentContainer() {
        try {
            List<InventoryAction> actions = new ArrayList<>();
            actions.add(new CloseContainer());

            INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actions(actions)
                    .priority(600)
                    .build());

            Thread.sleep(100); // 等待容器关闭

        } catch (Exception e) {
            error("关闭容器失败: " + e.getMessage());
        }
    }


    private void checkContainerClose() {
        if (withdrawFuture.isCompleted()) {
            info("成功拿到钥匙");

            // 按下按钮后2秒进入开宝库流程
            openVault();

//            warn("尝试提取钥匙失败 {}. Retrying...", ominousKeyWithdrawAttempts);
//            tryWithdrawOminousKey();
        }
    }


    private boolean isOminousKey(ItemStack item) {
        // 检查是否是不详钥匙
        // 不详钥匙通常是带有特定NBT的物品，这里使用一个通用的检查方法
        if (item == null) return false;

        return item.getId() == ItemRegistry.OMINOUS_TRIAL_KEY.id();

    }

    private boolean hasKeyInInventory() {
        final List<ItemStack> inventory = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 44; i >= 9; i--) {
            ItemStack itemStack = inventory.get(i);
            if (isOminousKey(itemStack)) {
                return true;
            }
        }

        return false;
    }

    private void searchForVault() {
        if (!isRunning) return;
        info("搜索附近的宝库...");
        currentState = State.SEARCHING_VAULT;

        // 搜索附近的宝库
        int playerX = (int) CACHE.getPlayerCache().getX();
        int playerY = (int) CACHE.getPlayerCache().getY();
        int playerZ = (int) CACHE.getPlayerCache().getZ();

        boolean foundVault = false;

        // 在搜索范围内查找宝库
        for (int dx = -config.searchRadius; dx <= config.searchRadius; dx++) {
            for (int dy = -config.searchRadius; dy <= config.searchRadius; dy++) {
                for (int dz = -config.searchRadius; dz <= config.searchRadius; dz++) {
                    int x = playerX + dx;
                    int y = playerY + dy;
                    int z = playerZ + dz;

                    if (isVault(x, y, z)) {
                        info("找到宝库: {}, {}, {}", x, y, z);
                        checkVault(x, y, z);

                        foundVault = true;
                        break;
                    }
                }
                if (foundVault) break;
            }
            if (foundVault) break;
        }

        if (!foundVault) {
            logFail("未找到附近的宝库");
            handleError("未找到附近的宝库");
        }
    }

    private void checkVault(int x, int y, int z) {
        if (checkVaultRetryTime > 3) {
            checkVaultRetryTime = 0;
            switchKeyRetryCount = 0; // 重置切换钥匙重试计数器
            String msg = currentVaultIndex + 1 + "号宝库状态不可用" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logFail(msg);
            sendChatMessageNear(msg);

            if (currentVaultIndex + 1 < config.vaults.size()) {


                currentVaultIndex++;
                // 重置超时计时器，适配NodeClient连接切换
                lastActionTime = Instant.now();
                reachedTargetPosition = false;
                currentState = State.WAITING_FOR_INGAME;
                gotoLocation();
            } else {

                // 正常断开，处理下一个账号
                boolean wasLastAccount = isLastAccount();
                if (wasLastAccount) {
                    if (config.loopAccounts || config.justLogin) {
                        // 在重新开始前检查钥匙数量（如果配置了钥匙容器位置）
                        openKeyContainer();
                    }
                } else {
                    stopAndNext();
                }
            }

            return;
        }
        if (isVaultActive(x, y, z)) {
            checkVaultRetryTime = 0;
            switchKeyRetryCount = 0; // 重置切换钥匙重试计数器

            // 检查玩家库存中是否已经有钥匙
            if (hasKeyInInventory()) {
                info("玩家库存中已有钥匙，直接开宝库，跳过按按钮获取钥匙");
                vaultCurrent.vaultX = x;
                vaultCurrent.vaultY = y;
                vaultCurrent.vaultZ = z;
                scheduleAction(() -> openVault(), 1);
            } else {
                pressButton(x, y, z);
            }

        } else {
            checkVaultRetryTime++;
            scheduleAction(() -> checkVault(x, y, z), 1);

        }

    }

    public static class VaultProperties {
        public boolean isVault;
        public Boolean ominous;
        public VaultState vaultState;
    }

    private VaultProperties getVaultProperties(int x, int y, int z) {
        VaultProperties props = new VaultProperties();

        int blockStateId = BlockStateInterface.getId(x, y, z);
        props.isVault = blockStateId >= BlockRegistry.VAULT.minStateId() &&
                blockStateId <= BlockRegistry.VAULT.maxStateId();

        if (props.isVault) {
            props.ominous = World.getBlockStateProperty(blockStateId, BlockStateProperties.OMINOUS);
            props.vaultState = World.getBlockStateProperty(blockStateId, BlockStateProperties.VAULT_STATE);
        }

        return props;
    }

    private boolean isVault(int x, int y, int z) {
        VaultProperties props = getVaultProperties(x, y, z);
        return props.isVault && Boolean.TRUE.equals(props.ominous);
    }

    private boolean isVaultActive(int x, int y, int z) {
        VaultProperties props = getVaultProperties(x, y, z);
        return props.isVault && Boolean.TRUE.equals(props.ominous) && VaultState.ACTIVE.equals(props.vaultState);
    }

    private boolean isVaultOpen(int x, int y, int z) {
        VaultProperties props = getVaultProperties(x, y, z);
        return props.isVault && Boolean.TRUE.equals(props.ominous) && !VaultState.ACTIVE.equals(props.vaultState);
    }

    private void openVault() {
        final int x = vaultCurrent.vaultX;
        final int y = vaultCurrent.vaultY;
        final int z = vaultCurrent.vaultZ;

        if (!isRunning) return;
        currentState = State.OPENING_VAULT;

        // 参考 AutoOmen 的逻辑，在打开宝库前切换到钥匙
        if (switchToKey()) {
            // 如果成功切换到钥匙，重置重试计数器并等待切换完成后再打开宝库
            switchKeyRetryCount = 0;
            logSuccess("成功切换到钥匙");
            info("打开宝库: {}, {}, {}", x, y, z);
            BARITONE.rightClickBlock(x, y, z).addExecutedListener(e -> {
                scheduleAction(() -> {
                    if (isVaultOpen(x, y, z)) {
                        logSuccess("开启成功");
                        waitForVault();
                    } else {
                        scheduleAction(() -> {
                            scheduleAction(() -> openVault(), 1);
                        }, config.waitAfterVaultOpen);
                    }

                }, 1);
            });

        } else {
            switchKeyRetryCount++;
            if (switchKeyRetryCount >= MAX_SWITCH_KEY_ATTEMPTS) {
                logFail("切换钥匙失败次数达到上限, 重启");
                sendChatMessageNear("切换钥匙失败次数达到上限, 重启");
                switchKeyRetryCount = 0;

                // 重启
                Proxy.getInstance().stop();

            } else {
                logWarning("切换钥匙失败 重试中 " + switchKeyRetryCount);
//                sendChatMessageNear("切换钥匙失败 重试中 (" + switchKeyRetryCount + "/" + MAX_SWITCH_KEY_ATTEMPTS + ")");
                scheduleAction(() -> openVault(), 1);
            }

        }
    }


    private void waitForVault() {
        if (!isRunning) return;
        info("等待宝库打开...");
        currentState = State.WAITING_VAULT;

        // 等待一段时间后断开连接
        scheduleAction(() -> {

            if (currentVaultIndex + 1 < config.vaults.size()) {
                info("宝库1操作完成，准备开下一个");
                currentVaultIndex++;
                // 重置超时计时器，适配NodeClient连接切换
                lastActionTime = Instant.now();
                reachedTargetPosition = false;
                gotoLocation();
            } else {
                info("宝库全部操作完成，准备断开连接");

                // 正常断开，处理下一个账号
                boolean wasLastAccount = isLastAccount();
                if (wasLastAccount) {
                    if (config.loopAccounts || config.justLogin) {
                        // 在重新开始前检查钥匙数量（如果配置了钥匙容器位置）
                        openKeyContainer();
                    }
                } else {
                    stopAndNext();
                }
            }


        }, config.waitAfterVaultOpen);
    }


    // 参考 AutoOmen 的 switchToFood 方法
    public boolean switchToKey() {
        delay = doInventoryActions();
        final boolean shouldStartUsing = getHand() != null && delay == 0;
        isUsingKey = getHand() != null || delay != 0;
        return shouldStartUsing;
    }

    private void stopAndNext() {
        currentState = State.DISCONNECTING;
        playersInRange.clear();
        reachedTargetPosition = false;
        currentVaultIndex = 0;
        retryCount = 0;
        switchKeyRetryCount = 0; // 重置切换钥匙重试计数器

        Proxy.getInstance().disconnect();
    }

    private void handleError(String error) {
        error("发生错误: {}", error);
        sendChatMessageNear("发生错误: " + error);
        retryCount++;

        Proxy.getInstance().stop();

//        if (retryCount >= config.maxRetries) {
//            error("重试次数已达上限，跳过当前账号");
//            sendChatMessageNear("重试次数已达上限，跳过当前账号");
//
//            // 记录失败账号和原因
//            if (config.currentAccountIndex < config.accounts.size()) {
//                AutoVaultOpenerConfig.AccountInfo acc = config.accounts.get(config.currentAccountIndex);
//                failedAccounts.add(new FailedAccount(acc.username, error));
//            }
//
//            retryCount = 0;
//            config.currentAccountIndex++;
//            scheduleAction(this::processNextAccount, config.retryDelaySeconds);
//        } else {
//            info("准备重试，当前重试次数: {}/{}", retryCount, config.maxRetries);
//            scheduleAction(() -> {
//                retryCount = 0;
//                Proxy.getInstance().stop();
//
//            }, config.retryDelaySeconds);
//        }
    }

    private void scheduleAction(Runnable action, int delaySeconds) {
        if (nextActionFuture != null) {
            nextActionFuture.cancel(false);
        }

        nextActionFuture = EXECUTOR.schedule(() -> {
            if (isRunning) {
                lastActionTime = Instant.now();
                action.run();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }


    private void sendChatMessage(String message) {
        sendClientPacketAsync(new ServerboundChatPacket(message));
        info("发送聊天消息: {}", message);
    }

    private void sendChatMessageNear(String message) {
        for (String name : getPlayersInRange()) {
            if (whisperCount < 3) {
                sendClientPacketAsync(ChatUtil.getWhisperChatPacket(name, message));
                whisperCount++;
            }
        }

    }

    private void saveConfig() {
        // 触发配置保存
        saveConfigAsync();
        info("已保存当前进度：第 {}/{} 个账号", config.currentAccountIndex + 1, config.accounts.size());
    }

    /**
     * 从输入字符串中提取验证码
     *
     * @param input 输入字符串，例如: "你必须输入验证码才能登入: /captcha csbvh"
     * @return 返回验证码，如果未找到则返回 null
     */
    public static String extractCaptcha(String input) {
        if (input == null) return null;

        // 更严格 + 通用的正则
        // ^.*? 表示前面任何字符，非贪婪
        // /captcha 后跟任意空白，再捕获验证码，验证码为非空白字符串 (\\S+)
        Pattern pattern = Pattern.compile("^.*?/captcha\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public void logSuccess(String msg) {
        MODULE_LOG.info(Component.text(msg, NamedTextColor.GREEN));
    }

    public void logFail(String msg) {
        MODULE_LOG.info(Component.text(msg, NamedTextColor.RED));
    }

    public void logWarning(String msg) {
        MODULE_LOG.info(Component.text(msg, NamedTextColor.YELLOW));
    }

    private void writeFailedAccountsToFile() {
        if (failedAccounts.isEmpty()) {
            return;
        }

        try (FileWriter writer = new FileWriter(FAILED_ACCOUNTS_FILE, false)) {
            for (FailedAccount account : failedAccounts) {
                writer.write(account.username + "\t" + account.reason + "\n");
            }
            info("已将失败账号写入文件: {}", FAILED_ACCOUNTS_FILE);
        } catch (IOException e) {
            error("写入失败账号文件时出错: {}", e.getMessage());
        }
    }

    private void printFailedAccountsSummary() {
        if (failedAccounts.isEmpty()) {
            info("所有账号均成功，无失败账号");
        } else {
            info("失败账号总结:");
            for (FailedAccount account : failedAccounts) {
                info("- {}  原因: {}", account.username, account.reason);
            }
            info("共 {} 个账号失败", failedAccounts.size());
        }
    }

    /**
     * 检查是否为最后一个账号
     */
    private boolean isLastAccount() {
        return config.currentAccountIndex >= config.accounts.size() - 1;
    }

    /**
     * 启动独立的账号超时检查器
     */
    private void startAccountTimeoutChecker() {
        // 停止之前的检查器
        stopAccountTimeoutChecker();

        accountStartTime = Instant.now();

        accountTimeoutChecker = TIMEOUT_EXECUTOR.scheduleAtFixedRate(() -> {
            if (!isRunning) return;

            long elapsed = Duration.between(accountStartTime, Instant.now()).getSeconds();

            // 检查是否超时
            if (elapsed > config.accountTimeoutSeconds) {
                handleAccountTimeout();
            }
        }, 1, 1, TimeUnit.SECONDS); // 每秒检查一次

        info("已启动账号超时检查器，超时时间: {} 秒", config.accountTimeoutSeconds);
    }

    /**
     * 停止独立的账号超时检查器
     */
    private void stopAccountTimeoutChecker() {
        if (accountTimeoutChecker != null && !accountTimeoutChecker.isCancelled()) {
            accountTimeoutChecker.cancel(false);
            accountTimeoutChecker = null;
        }
    }

    /**
     * 处理账号超时
     */
    private void handleAccountTimeout() {
        error("账号操作超时 ({} 秒)，自动重启应用", config.accountTimeoutSeconds);
        sendChatMessageNear("账号操作超时，自动重启");

        // 停止超时检查器
        stopAccountTimeoutChecker();

        // 重启整个应用
        Proxy.getInstance().stop();

    }

    /**
     * 检查是否配置了钥匙容器位置
     */
    private boolean hasKeyContainerConfigured() {
        if (config.vaults.isEmpty()) return false;

        // 检查第一个宝库配置是否有钥匙容器坐标
        AutoVaultOpenerConfig.VaultInfo firstVault = config.vaults.get(0);
        return firstVault.keyContainerX != 0 || firstVault.keyContainerY != 0 || firstVault.keyContainerZ != 0;
    }

    /**
     * 检查钥匙容器中的钥匙数量
     */
    private void openKeyContainer() {
        if (!isRunning) return;

        AutoVaultOpenerConfig.VaultInfo vault = config.vaults.get(currentVaultIndex);

        currentState = State.CHECKING_KEY_CONTAINER;

        info("检查钥匙容器中的钥匙数量: {}, {}, {}", vault.keyContainerX, vault.keyContainerY, vault.keyContainerZ);
        // 打开钥匙容器
        BARITONE.rightClickBlock(vault.keyContainerX, vault.keyContainerY, vault.keyContainerZ).addExecutedListener(f -> {

            // 等待容器打开后检查钥匙数量
            scheduleAction(this::countKeysInContainer, 1);
        });
    }

    /**
     * 统计容器中的钥匙数量
     */
    private void countKeysInContainer() {
        if (!isRunning) return;

        Container openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer == null && openContainer.getContainerId() != 0) {
            warn("钥匙容器未打开，重试中...");
            scheduleAction(this::openKeyContainer, 1);
            return;
        }

        int keyCount = 0;


        // 遍历容器中的所有物品
        // for (int slot = 0; slot < openContainer.getSize() - 36; slot++) {
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack item = openContainer.getItemStack(slot);
            if (isOminousKey(item)) {
                keyCount += item.getAmount();
            }
        }


        info("钥匙容器中共有 {} 个钥匙", keyCount);
        // 关闭容器
        closeCurrentContainer();

        // 根据钥匙数量决定下一步操作
        if (keyCount >= config.minKeyCount) {
            info("钥匙数量充足 ({} >= {})，继续下一轮", keyCount, config.minKeyCount);
            // 继续下一轮，重置账号索引
            config.justLogin = false;
            config.loopAccounts = true;
            stopAndNext();
        } else {
            info("钥匙数量不足 ({} < {})，设置为仅登录模式并继续下一轮", keyCount, config.minKeyCount);
            // 设置为仅登录模式
            config.justLogin = true;
            // 重置账号索引，继续下一轮
            stopAndNext();
        }

        // 保存配置
        saveConfig();
    }
}
