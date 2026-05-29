package com.github.futa.module;

import com.github.futa.FutaPlugin;
import com.github.futa.config.EndGatewayConfig;
import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.player.*;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.dimension.DimensionData;
import com.zenith.mc.dimension.DimensionRegistry;
import com.zenith.module.api.Module;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;

import java.util.List;
import java.util.Optional;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.INPUTS;

public class EndGateway extends Module {

    EndGatewayConfig PLUGIN_CONFIG = FutaPlugin.PLUGIN_CONFIG.endGateway;

    public static final int PRIORITY = 8000;
    private State state = State.IDLE;
    private boolean isMovingForward = false;
    private boolean isSprinting = false;
    private BlockPos targetPosition = null;
    private final Timer pathfindingTimer = Timers.tickTimer();
    private final Timer waitForInteractTimer = Timers.tickTimer();
    InputRequestFuture future;

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.enabled;
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
        state = State.IDLE;
        targetPosition = null;
        pathfindingTimer.reset();
        waitForInteractTimer.reset();
    }

    public PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder()
                .setId("end-gateway")
                .state(ProtocolState.GAME, PacketHandlerStateCodec.clientBuilder()
                        .build())
                .build();
    }

    private void onTick(ClientBotTick event) {
        if (isEnd()) {
            setState(State.IDLE);
            return;
        }
        switch (state) {
            case IDLE -> {
                if (isEnd()) {
                    return;
                }

                setState(State.FIND_GATEWAY);
            }
            case FIND_GATEWAY -> {
                var gatewayPos = findGatewayPortal();
                if (gatewayPos.isPresent()) {
                    info("Found end gateway at: {}", gatewayPos.get());
                    setState(State.PATH_TO_GATEWAY);
                } else {
                    info("No end gateway found within {} blocks of configured position", PLUGIN_CONFIG.detectionRadius);
                    syncEnabledFromConfig();
                }
            }
            case PATH_TO_GATEWAY -> {
                var gatewayPos = findGatewayPortal();
                if (gatewayPos.isEmpty()) {
                    warn("Lost sight of end gateway, returning to search");
                    setState(State.FIND_GATEWAY);
                    return;
                }

                targetPosition = gatewayPos.get();
                pathfindingTimer.reset();
                setState(State.MOVING_TO_GATEWAY);
                info("Starting movement to end gateway at: {}", targetPosition);
            }
            case MOVING_TO_GATEWAY -> {
                if (targetPosition == null) {
                    setState(State.FIND_GATEWAY);
                    return;
                }

                // Face towards target and move forward
                faceTowards(targetPosition);
            }
            case WAITING_FOR_TELEPORT -> {
                info("Teleportation timeout, attempting to interact again");
                setState(State.PATH_TO_GATEWAY);
            }
        }
    }

    private static boolean isEnd() {
        DimensionData currentDimension = World.getCurrentDimension();
        return currentDimension == DimensionRegistry.THE_END.get();
    }

    private Optional<BlockPos> findGatewayPortal() {
        var playerPos = CACHE.getPlayerCache().getThePlayer().position();
        var configPos = PLUGIN_CONFIG.gatewayPosition;

        if (configPos.equals(BlockPos.ZERO)) {
            warn("Gateway position not configured");
//            BlockRegistry.END_PORTAL_FRAME
            return Optional.empty();
        }

        double distance = Math.sqrt(
                Math.pow(playerPos.getX() - configPos.x(), 2) +
                        Math.pow(playerPos.getY() - configPos.y(), 2) +
                        Math.pow(playerPos.getZ() - configPos.z(), 2)
        );

        if (distance <= PLUGIN_CONFIG.detectionRadius) {
            return Optional.of(configPos);
        }

        return Optional.empty();
    }

    private void setState(State newState) {
        debug("State change: {} -> {}", state, newState);
        this.state = newState;
    }

    private void faceTowards(BlockPos target) {

        var rotation = RotationHelper.rotationTo(target.x(), target.y() + 1, target.z());
        future = INPUTS.submit(InputRequest.builder()
                .owner(this)
                .input(Input.builder().pressingForward(true).sprinting(true).jumping(true).build())
                .yaw(rotation.getX())
                .pitch(rotation.getY())
                .priority(1000)
                .build());
    }


    public enum State {
        IDLE,
        FIND_GATEWAY,
        PATH_TO_GATEWAY,
        MOVING_TO_GATEWAY,
        WAITING_FOR_TELEPORT
    }
}
