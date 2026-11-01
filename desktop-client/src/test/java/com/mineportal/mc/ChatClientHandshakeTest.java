package com.mineportal.mc;

import static org.junit.jupiter.api.Assertions.*;

import com.mineportal.auth.Account;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatClientHandshakeTest {
  @Test
  void translatedHandshakePreservesUserHostnameAndUsesTargetProtocolOnTheWire() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("localhost"))) {
      server.setSoTimeout(5000);
      ChatClient client =
          new ChatClient(
              new Account(UUID.randomUUID(), "TestPlayer", "unused", null),
              new ChatClient.Listener() {
                public void onConnected() {
                  fail("TCP startup is not game login");
                }

                public void onChat(String line) {}

                public void onDisconnected(String reason) {}
              });
      try {
        client.connect("localhost", server.getLocalPort(), 767, -1);
        try (var socket = server.accept()) {
          socket.setSoTimeout(5000);
          InputStream input = socket.getInputStream();
          int length = varInt(input);
          assertTrue(length > 0 && length < 1024);
          DataInputStream frame =
              new DataInputStream(new ByteArrayInputStream(input.readNBytes(length)));
          assertEquals(0, varInt(frame));
          assertEquals(767, varInt(frame), "ViaVersion must rewrite the actual handshake version");
          String host = new String(frame.readNBytes(varInt(frame)), StandardCharsets.UTF_8);
          assertEquals(
              "localhost",
              host,
              "Virtual-host routing needs the user hostname, not its resolved IP");
          assertEquals(server.getLocalPort(), frame.readUnsignedShort());
          assertEquals(2, varInt(frame));
        }
      } finally {
        client.disconnect();
      }
    }
  }

  private static int varInt(InputStream in) throws Exception {
    int result = 0;
    for (int i = 0; i < 5; i++) {
      int value = in.read();
      if (value < 0) throw new java.io.EOFException();
      result |= (value & 0x7f) << (i * 7);
      if ((value & 0x80) == 0) return result;
    }
    throw new java.io.IOException("oversized VarInt");
  }
}
