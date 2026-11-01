package com.mineportal.protocol;

import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundCustomQueryPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForgeHandshakeTest {

    @Test
    void marksLegacyAndModernForgeHostsWithoutChangingPlainHost() {
        assertEquals("play.example.net\0FML3\0", ForgeHandshake.markHost("play.example.net", 3, 763));
        assertEquals("play.example.net\0FORGE", ForgeHandshake.markHost("play.example.net", 4, 764));
    }

    @Test
    void malformedModernPayloadIsIgnoredInsteadOfCrashingConnection() {
        var malformed = new ClientboundCustomPayloadPacket(Key.key("forge", "handshake"),
                new byte[]{(byte) 0x80});
        assertDoesNotThrow(() -> ForgeHandshake.reply(malformed));
        assertNull(ForgeHandshake.reply(malformed));
    }

    @Test
    void malformedLegacyQueryGetsSafeEmptyAnswer() {
        var malformed = new ClientboundCustomQueryPacket(7, Key.key("fml", "loginwrapper"),
                new byte[]{(byte) 0x80});
        var answer = assertDoesNotThrow(() -> ForgeHandshake.reply(malformed));
        assertNotNull(answer);
    }
}
