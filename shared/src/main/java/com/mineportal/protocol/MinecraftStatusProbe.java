package com.mineportal.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Minimal server-list ping used when the headless agent must inspect a LAN-only server locally. */
public final class MinecraftStatusProbe {

    private static final int TIMEOUT_MS = 3_000;
    private static final int MAX_FRAME_BYTES = 1024 * 1024;

    public record Status(Integer protocol, Integer forgeNetworkVersion) {
    }

    private MinecraftStatusProbe() {
    }

    public static Status probe(MinecraftAddressResolver.ResolvedEndpoint endpoint, int clientProtocol)
            throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(endpoint.socketAddress(), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            writeFrame(out, handshake(endpoint.logicalHost(), endpoint.targetPort(), clientProtocol));
            writeFrame(out, new byte[]{0});

            InputStream in = socket.getInputStream();
            int frameLength = readVarInt(in);
            if (frameLength <= 0 || frameLength > MAX_FRAME_BYTES) {
                throw new IOException("invalid status response size");
            }
            byte[] frame = in.readNBytes(frameLength);
            if (frame.length != frameLength) throw new EOFException("incomplete status response");

            return parseStatusFrame(frame);
        }
    }

    static Status parseStatusFrame(byte[] frame) throws IOException {
        try {
            Cursor cursor = new Cursor(frame);
            if (cursor.readVarInt() != 0) throw new IOException("unexpected status packet");
            JsonObject root = JsonParser.parseString(cursor.readString()).getAsJsonObject();
            Integer protocol = integer(root, "version", "protocol");
            Integer forge = integer(root, "forgeData", "fmlNetworkVersion");
            return new Status(protocol, forge);
        } catch (IllegalStateException | com.google.gson.JsonParseException e) {
            throw new IOException("invalid status json", e);
        }
    }

    private static Integer integer(JsonObject root, String objectName, String fieldName) {
        if (!root.has(objectName) || !root.get(objectName).isJsonObject()) return null;
        JsonObject object = root.getAsJsonObject(objectName);
        if (!object.has(fieldName) || !object.get(fieldName).isJsonPrimitive()) return null;
        try {
            return object.get(fieldName).getAsInt();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static byte[] handshake(String host, int port, int clientProtocol) throws IOException {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        writeVarInt(packet, 0);
        writeVarInt(packet, clientProtocol);
        writeString(packet, host);
        packet.write((port >>> 8) & 0xff);
        packet.write(port & 0xff);
        writeVarInt(packet, 1);
        return packet.toByteArray();
    }

    private static void writeFrame(OutputStream out, byte[] packet) throws IOException {
        writeVarInt(out, packet.length);
        out.write(packet);
        out.flush();
    }

    private static void writeString(OutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        do {
            int part = value & 0x7f;
            value >>>= 7;
            out.write(value == 0 ? part : part | 0x80);
        } while (value != 0);
    }

    private static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 32; shift += 7) {
            int next = in.read();
            if (next < 0) throw new EOFException("incomplete VarInt");
            value |= (next & 0x7f) << shift;
            if ((next & 0x80) == 0) return value;
        }
        throw new IOException("VarInt too large");
    }

    private static final class Cursor {
        private final byte[] data;
        private int offset;

        private Cursor(byte[] data) {
            this.data = data;
        }

        private int readVarInt() throws IOException {
            int value = 0;
            for (int shift = 0; shift < 32; shift += 7) {
                if (offset >= data.length) throw new EOFException("incomplete VarInt");
                int next = data[offset++] & 0xff;
                value |= (next & 0x7f) << shift;
                if ((next & 0x80) == 0) return value;
            }
            throw new IOException("VarInt too large");
        }

        private String readString() throws IOException {
            int length = readVarInt();
            if (length < 0 || length > data.length - offset) throw new EOFException("invalid string length");
            String value = new String(data, offset, length, StandardCharsets.UTF_8);
            offset += length;
            return value;
        }
    }
}
