package com.github.futa.module;

import com.github.futa.FutaPlugin;
import com.github.futa.config.FixedAngleViewConfig;
import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.player.InputRequest;
import com.zenith.module.api.Module;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.INPUTS;

public class FixedAngleView extends Module {

    FixedAngleViewConfig PLUGIN_CONFIG = FutaPlugin.PLUGIN_CONFIG.fixedAngleView;
    private final Timer rotationTimer = Timers.tickTimer();
    public static final int MOVEMENT_PRIORITY = 80;

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::handleClientTickEvent)
        );
    }

    @Override
    public void onDisable() {
        // 模块禁用时不需要特殊处理
    }

    private void handleClientTickEvent(final ClientBotTick event) {
        if (rotationTimer.tick(PLUGIN_CONFIG.intervalTicks)) {
            rotationTimer.reset();
            rotateToFixedAngle();
        }
    }

    private void rotateToFixedAngle() {
        float yaw = PLUGIN_CONFIG.yaw;
        float pitch = PLUGIN_CONFIG.pitch;

        INPUTS.submit(InputRequest.builder()
                .owner(this)
                .yaw(yaw)
                .pitch(pitch)
                .priority(MOVEMENT_PRIORITY)
                .build());
    }
}
