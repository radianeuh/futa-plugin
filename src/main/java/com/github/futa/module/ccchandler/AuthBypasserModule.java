package com.github.futa.module.ccchandler;

import com.github.futa.FutaPlugin;
import com.zenith.module.api.Module;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundHelloPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundHelloPacket;

/**
 * description = "Fixes offline login flow for compatible hybrid-auth servers",
 * authors = "CandyMagicShow",
 */
public class AuthBypasserModule extends Module {
    private static final int PRIORITY = 100000;

    public boolean enabledSetting() {
        return FutaPlugin.PLUGIN_CONFIG.cccuuuAuthBypasser.enabled;
    }

    @Override
    public PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder()
                .setId("cccuuu-new-auth-bypasser-login-fix")
                .setPriority(100000)
                .state(
                        ProtocolState.LOGIN,
                        PacketHandlerStateCodec.clientBuilder()
                                .inbound(ClientboundHelloPacket.class, new OfflineAuthenticateHelloHandler())
                                .outbound(ServerboundHelloPacket.class, new OfflineUuidHelloHandler())
                                .build()
                )
                .build();
    }
}
