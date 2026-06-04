package com.github.futa.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.Globals;
import com.zenith.Proxy;
import com.zenith.event.chat.SystemChatEvent;
import com.zenith.event.client.ClientConnectEvent;
import com.zenith.event.client.ClientDisconnectEvent;
import com.zenith.module.api.Module;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CONFIG;
import static com.zenith.util.DisconnectMessages.MANUAL_DISCONNECT;

/**
 * 一个简单的自动登录插件 AutoLoginPlugin，只注册了 SystemChatEvent 事件监听器。插件会在聊天中出现 /login 时自动发送登录指令
 * 还有自动重启
 */
public class AutoLoginModule extends Module {
    final Timer timer = Timers.tickTimer();
    private static final String LOGIN_COMMAND = "/login";

    // 定时任务相关
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> rebootTask;
    private ScheduledFuture<?> offlineMonitorTask;

    // 防抖相关
    private long lastRestartMessageTime = 0;
    private static final long DEBOUNCE_INTERVAL = 5000; // 5秒防抖间隔

    // 时间解析相关
    private static final Pattern TIME_PATTERN = Pattern.compile("at (\\d{1,2}):(\\d{2})");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // 离线监控相关
    private long lastOnlineTime = System.currentTimeMillis();
    private static final long OFFLINE_TIMEOUT = 30 * 60 * 1000; // 30分钟，单位：毫秒

    public boolean isOnline = false;

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.autoLogin;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        startOfflineMonitor();
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientDisconnectEvent.class, this::handleDisconnectEvent),
                of(ClientConnectEvent.class, this::handleConnectEvent),
                of(SystemChatEvent.class, this::handleLogin)
        );
    }

    private void handleConnectEvent(ClientConnectEvent clientConnectEvent) {
        if (!Globals.CONFIG.client.server.address.toLowerCase().contains("3c3u.org")) {
            return;
        }
        info("客户端连接成功，清理重启任务");
        clearRebootTasks();
//        PLUGIN_CONFIG.autoReboot = true;

//        updateOnlineStatus(true);
    }

    private void handleDisconnectEvent(ClientDisconnectEvent event) {
        if (!Globals.CONFIG.client.server.address.toLowerCase().contains("3c3u.org")) {
            return;
        }
        String reason = event.reason();
        info("客户端断开连接:" + reason);
        updateOnlineStatus(false);


        if (MANUAL_DISCONNECT.equals(reason)) {
            PLUGIN_CONFIG.autoReboot = false;
        }
    }

    public boolean getOnline() {
        boolean connected = Proxy.getInstance().isConnected();

        return connected && !isIn3cSpawn(CACHE.getPlayerCache().getThePlayer().getX(), CACHE.getPlayerCache().getThePlayer().getZ());
    }

    public void reboot() {
        Proxy.getInstance().disconnect();
        Proxy.getInstance().stop();
    }

    private void handleLogin(SystemChatEvent event) {
        if (!PLUGIN_CONFIG.autoLogin) {
            return;
        }
        if (!Globals.CONFIG.client.server.address.toLowerCase().contains("3c3u.org")) {
            return;
        }

        String message = event.message();
        if (message.contains("/login")) {
            info("自动登录中...: {}", CONFIG.authentication.username);

            sendChatMessage(LOGIN_COMMAND + " " + CONFIG.authentication.password);
        }

        // 检查登录是否成功
        if (message.contains("Connecting to the server")) {
            info("bot 进入游戏服中");
            updateOnlineStatus(true);
        }

        // 检查是否被踢出
        if (message.contains("kicked") || message.contains("disconnected")) {
            warn("bot被服务器踢出");
            updateOnlineStatus(false);
        }


//        if (message.contains("/email add")) {
//            info("邮箱绑定");
//            AutoVaultOpenerConfig.AccountInfo account = config.accounts.get(config.currentAccountIndex);
//
//            String email = account.username + "@xxxx.com";
//            sendChatMessage("/email add " + email + " " + email);
//        }

        //一一一一一一一一一一一一一一一一一一一一一一一
        //This server is restarting in 1m at 05:00 !
        //You were kicked from server: Server restarting, please wait a minute
        //You were kicked from login: A player with the same IP is already in game!

        if (message.contains("This server is restarting in")) {
            handleServerRestart(message);
        }

//        你必须输入验证码才能登入: /captcha lje0a
        //你必须输入验证码才能登入: /captcha csbvh
        if (message.contains("/captcha")) {
            info("bot输入验证码");

            sendChatMessage("/captcha " + extractCaptcha(message));
        }
    }

    private void sendChatMessage(String message) {
        if (Proxy.getInstance().isConnected()) {
            Proxy.getInstance().getClient().sendAsync(new ServerboundChatPacket(message));
            info("发送login消息: {}", message);
        }
    }

    /**
     * 处理服务器重启消息
     */
    private void handleServerRestart(String message) {
        long currentTime = System.currentTimeMillis();


        //todo /ar check 检查重启消息
        // 防抖：如果距离上次处理重启消息不足5秒，则忽略
        if (currentTime - lastRestartMessageTime < DEBOUNCE_INTERVAL) {
            info("重启消息防抖，忽略重复消息");
            return;
        }

        lastRestartMessageTime = currentTime;
        info("检测到服务器重启消息: {}", message);

        // 取消现有的重启任务
        clearRebootTasks();

        // 解析重启时间
        LocalTime restartTime = parseRestartTime(message);
        if (restartTime == null) {
            warn("无法解析重启时间，使用默认延迟");
            scheduleRebootTask(2 * 60); // 默认2分钟后开始
            return;
        }

        // 计算延迟时间
        long initialDelay = calculateDelayToRestartTime(restartTime);
        info("解析到重启时间: {}, 计算延迟: {}秒", restartTime.format(TIME_FORMATTER), initialDelay);

        scheduleRebootTask(initialDelay);
    }

    /**
     * 解析重启时间
     */
    private LocalTime parseRestartTime(String message) {
        try {
            Matcher matcher = TIME_PATTERN.matcher(message);
            if (matcher.find()) {
                int hour = Integer.parseInt(matcher.group(1));
                int minute = Integer.parseInt(matcher.group(2));
                return LocalTime.of(hour, minute);
            }
        } catch (Exception e) {
            warn("解析重启时间失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 计算到重启时间的延迟（秒）
     */
    private long calculateDelayToRestartTime(LocalTime restartTime) {
        LocalTime now = LocalTime.now();

        // 如果当前时间已经超过重启时间，说明重启时间是明天的
        if (now.isAfter(restartTime)) {
            info("当前时间{}已超过重启时间{}，认为重启时间是明天",
                    now.format(TIME_FORMATTER), restartTime.format(TIME_FORMATTER));
            // 计算到明天该时间的秒数
            long secondsUntilMidnight = 24 * 3600 - now.toSecondOfDay();
            long secondsFromMidnight = restartTime.toSecondOfDay();
            return secondsUntilMidnight + secondsFromMidnight + 2 * 60; // 重启时间后2分钟开始
        } else {
            // 计算到今天重启时间的秒数，然后再加2分钟
            long delayToRestart = restartTime.toSecondOfDay() - now.toSecondOfDay();
            return delayToRestart + 2 * 60; // 重启时间后2分钟开始
        }
    }

    /**
     * 安排重启任务
     */
    private void scheduleRebootTask(long initialDelay) {
        long period = 3 * 60; // 3分钟间隔，单位：秒

        info("开始设置重启任务：{}秒后开始，每{}秒重试一次", initialDelay, period);

        // 创建定时重启任务
        rebootTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (getOnline()) {
                    info("检测到已重新在线，取消重启任务");
                    clearRebootTasks();
                    return;
                }
                if (!PLUGIN_CONFIG.autoReboot) {
                    info("检测到手动断开的，取消重启任务");
                    return;
                }

                info("执行自动重启...");
                reboot();
            } catch (Exception e) {
                warn("重启任务执行异常: {}", e.getMessage());
            }
        }, initialDelay, period, TimeUnit.SECONDS);

        info("重启任务已设置");
    }

    /**
     * 清空所有重启任务
     */
    private void clearRebootTasks() {
        if (rebootTask != null && !rebootTask.isCancelled()) {
            rebootTask.cancel(false);
            info("已取消重启任务");
        }
        rebootTask = null;
    }

    /**
     * 启动离线监控任务
     */
    private void startOfflineMonitor() {
        if (offlineMonitorTask != null && !offlineMonitorTask.isCancelled()) {
            offlineMonitorTask.cancel(false);
        }

        // 每分钟检查一次离线状态
        offlineMonitorTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                checkOfflineTimeout();
            } catch (Exception e) {
                warn("离线监控任务执行异常: {}", e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS); // 1分钟后开始，每分钟执行一次

        info("离线监控任务已启动");
    }

    /**
     * 检查离线超时
     */
    private void checkOfflineTimeout() {
        if (getOnline()) {
            // 如果在线，更新最后在线时间
            lastOnlineTime = System.currentTimeMillis();
            return;
        }

        long offlineTime = System.currentTimeMillis() - lastOnlineTime;
        long offlineMinutes = offlineTime / (60 * 1000);

        int timeout = PLUGIN_CONFIG.autoLoginTimeout * 60 * 1000;
        if (offlineTime >= timeout) {
            warn("离线超过设定分钟（实际{}分钟），执行自动重启", offlineMinutes);
            reboot();
            // 重启后重置时间，避免重复重启
            lastOnlineTime = System.currentTimeMillis();
        } else {
            info("当前离线{}分钟，距离自动重启还有{}分钟", offlineMinutes, 30 - offlineMinutes);
        }
    }

    /**
     * 更新在线状态
     */
    private void updateOnlineStatus(boolean online) {
        isOnline = online;
        if (online) {
            lastOnlineTime = System.currentTimeMillis();
            info("状态更新：在线");
        } else {
            info("状态更新：离线");
        }
    }

    /**
     * 模块关闭时清理资源
     */
    @Override
    public void onDisable() {
        clearRebootTasks();
        clearOfflineMonitor();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        super.onDisable();
    }

    /**
     * 清理离线监控任务
     */
    private void clearOfflineMonitor() {
        if (offlineMonitorTask != null && !offlineMonitorTask.isCancelled()) {
            offlineMonitorTask.cancel(false);
            info("已取消离线监控任务");
        }
        offlineMonitorTask = null;
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

    /**
     * 判断坐标 (x, z) 是否在区域 [100,300] × [100,300] 内
     *
     * @return true 表示在区域内，否则 false
     */
    public static boolean isIn3cSpawn() {
        if (!Globals.CONFIG.client.server.address.toLowerCase().contains("3c3u.org")) {
            return false;
        }
        return isIn3cSpawn(CACHE.getPlayerCache().getX(), CACHE.getPlayerCache().getZ());
    }

    /**
     * 判断坐标 (x, z) 是否在区域 [100,300] × [100,300] 内
     *
     * @param x x坐标
     * @param z z坐标
     * @return true 表示在区域内，否则 false
     */
    public static boolean isIn3cSpawn(double x, double z) {
        return x >= 100 && x <= 300 && z >= 100 && z <= 300;
    }
}
