package com.mineportal.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
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
        var result = assertDoesNotThrow(() -> ForgeHandshake.handle(malformed));
        assertNull(result.reply());
        assertFalse(result.rejected());
    }

    @Test
    void malformedLegacyQueryGetsSafeEmptyAnswer() {
        var malformed = new ClientboundCustomQueryPacket(7, Key.key("fml", "loginwrapper"),
                new byte[]{(byte) 0x80});
        var result = assertDoesNotThrow(() -> ForgeHandshake.handle(malformed));
        assertNotNull(result.reply());
        assertFalse(result.rejected());
    }

    @Test
    void reportsAnEmptyClientModListInsteadOfEchoingServerMods() {
        ByteBuf payload = Unpooled.buffer();
        MinecraftTypes.writeVarInt(payload, 1);
        MinecraftTypes.writeVarInt(payload, 1);
        MinecraftTypes.writeString(payload, "requiredmod");
        MinecraftTypes.writeString(payload, "Required Mod");
        MinecraftTypes.writeString(payload, "1.0.0");
        byte[] bytes = new byte[payload.readableBytes()];
        payload.readBytes(bytes);

        var result = ForgeHandshake.handle(
                new ClientboundCustomPayloadPacket(Key.key("forge", "handshake"), bytes));

        assertFalse(result.rejected());
        assertNotNull(result.reply());
        ByteBuf reply = Unpooled.wrappedBuffer(result.reply().getData());
        assertEquals(1, MinecraftTypes.readVarInt(reply));
        assertEquals(0, MinecraftTypes.readVarInt(reply));
        assertFalse(reply.isReadable());
    }

    @Test
    void rejectsRegistrySynchronizationThatAChatProxyCannotApply() {
        ByteBuf payload = Unpooled.buffer();
        MinecraftTypes.writeVarInt(payload, 3);
        MinecraftTypes.writeVarInt(payload, 7);
        byte[] bytes = new byte[payload.readableBytes()];
        payload.readBytes(bytes);

        var result = ForgeHandshake.handle(
                new ClientboundCustomPayloadPacket(Key.key("forge", "handshake"), bytes));

        assertTrue(result.rejected());
        assertNull(result.reply());
    }
}
