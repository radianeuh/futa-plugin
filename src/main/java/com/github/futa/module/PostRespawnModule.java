package com.github.futa.module;


import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientDeathEvent;
import com.zenith.module.api.Module;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.EXECUTOR;

public class PostRespawnModule extends Module {
    public PostRespawnModule() {
        super();
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientDeathEvent.class, this::handleDeathEvent)
        );
    }

    @Override
    public boolean enabledSetting() {
        // 可以在配置文件中添加开关控制此模块
        return true; // 简单起见，默认启用
    }

    public void handleDeathEvent(final ClientDeathEvent event) {
        // 在重生后执行代码，这里延迟5秒确保重生已完成
        EXECUTOR.schedule(this::postRespawnAction, 5, TimeUnit.SECONDS);
    }

    private void postRespawnAction() {
        // 在这里添加重生后需要执行的代码
        // 例如发送消息、执行命令等

        // 示例：输出日志
        info("Player has respawned! Executing post-respawn actions...");

        // 示例：执行其他操作
        // 可以在这里添加任何你需要的逻辑
        performPostRespawnActions();
    }

    private void performPostRespawnActions() {
        // 在这里实现你的重生后逻辑
        // 例如：
        // 1. 发送欢迎消息到聊天
        // 2. 检查并补充物品
        // 3. 设置特定的游戏模式
        // 4. 传送到特定位置
        // 5. 启动其他模块

        info("Performing post-respawn actions...");

        // 添加你自己的逻辑实现
        // 例如：
        // sendClientPacketAsync(new ServerboundChatCommandPacket("say I'm back!"));
    }
}
