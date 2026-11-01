package com.mineportal.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecraftStatusProbeTest {

    @Test
    void readsProtocolAndForgeNetworkVersion() throws Exception {
        String json = "{\"version\":{\"protocol\":763},\"forgeData\":{\"fmlNetworkVersion\":3}}";
        MinecraftStatusProbe.Status status = MinecraftStatusProbe.parseStatusFrame(statusFrame(json));

        assertEquals(763, status.protocol());
        assertEquals(3, status.forgeNetworkVersion());
    }

    @Test
    void rejectsTruncatedStatusString() throws Exception {
        byte[] frame = statusFrame("{}");
        frame[1] = 100;
        assertThrows(IOException.class, () -> MinecraftStatusProbe.parseStatusFrame(frame));
    }

    private static byte[] statusFrame(String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        writeVarInt(out, bytes.length);
        out.write(bytes);
        return out.toByteArray();
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        do {
            int part = value & 0x7f;
            value >>>= 7;
            out.write(value == 0 ? part : part | 0x80);
        } while (value != 0);
    }
}
