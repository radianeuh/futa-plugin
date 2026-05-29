package com.github.futa.module.ccchandler;

import com.github.futa.FutaPlugin;
import com.zenith.Globals;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.PacketHandler;
import com.zenith.util.config.Config.Authentication.AccountType;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundHelloPacket;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class OfflineUuidHelloHandler implements PacketHandler<ServerboundHelloPacket, ClientSession> {
    public ServerboundHelloPacket apply(ServerboundHelloPacket packet, ClientSession session) {
        if (Globals.CONFIG.authentication.accountType != AccountType.OFFLINE) {
            return packet;
        }

        if (!FutaPlugin.PLUGIN_CONFIG.cccuuuAuthBypasser.rewriteOfflineUuid) {
            return packet;
        }

        if (!Globals.CONFIG.client.server.address.toLowerCase().contains("3c3u.org")) {
            return packet;
        }

        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + packet.getUsername()).getBytes(StandardCharsets.UTF_8));
        if (offlineUuid.equals(packet.getProfileId())) {
            return packet;
        }

        FutaPlugin.log.info("Rewriting offline login UUID for {} to {}", packet.getUsername(), offlineUuid);
        return new ServerboundHelloPacket(packet.getUsername(), offlineUuid);
    }
}
