package com.github.futa.module.ccchandler;

import com.github.futa.FutaPlugin;
import com.zenith.Globals;
import com.zenith.feature.api.sessionserver.SessionServerApi;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.PacketHandler;
import com.zenith.util.config.Config.Authentication.AccountType;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundHelloPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundKeyPacket;

import javax.crypto.SecretKey;

public class OfflineAuthenticateHelloHandler implements PacketHandler<ClientboundHelloPacket, ClientSession> {
    public ClientboundHelloPacket apply(ClientboundHelloPacket packet, ClientSession session) {
        if (Globals.CONFIG.authentication.accountType != AccountType.OFFLINE) {
            return packet;
        }
        if (!Globals.CONFIG.client.server.address.toLowerCase().contains("3c3u.org")) {
            return packet;
        }
        if (FutaPlugin.PLUGIN_CONFIG.cccuuuAuthBypasser.bypassAuthenticateHello && packet.isShouldAuthenticate()) {
            SecretKey key = SessionServerApi.INSTANCE.generateClientKey();
            if (key == null) {
                session.disconnect("Failed to generate secret key.");
                return null;
            } else {
                FutaPlugin.log.info("Bypassing authenticate=true hello for offline account: {}", session.getProfile().getName());
                session.send(new ServerboundKeyPacket(packet.getPublicKey(), key, packet.getChallenge()), future -> session.enableEncryption(key));
                return null;
            }
        } else {
            return packet;
        }
    }
}
