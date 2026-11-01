package com.mineportal.protocol;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftAddressResolverTest {

    @Test
    void usesMinecraftSrvPortAndPinsOriginalVirtualHost() throws Exception {
        FakeDns dns = new FakeDns(
                List.of(new MinecraftAddressResolver.SrvRecord(0, 10, 25570, "edge.example.net")),
                Map.of("edge.example.net", new String[]{"1.1.1.1"}));
        MinecraftAddressResolver resolver = new MinecraftAddressResolver(dns);

        var endpoint = resolver.resolve("play.example.net", 25565, true);

        assertTrue(endpoint.fromSrv());
        assertEquals("edge.example.net", endpoint.targetHost());
        assertEquals(25570, endpoint.targetPort());
        assertEquals("play.example.net", endpoint.address().getHostName());
        assertEquals("1.1.1.1", endpoint.address().getHostAddress());
    }

    @Test
    void explicitPortSkipsSrvLookup() throws Exception {
        FakeDns dns = new FakeDns(
                List.of(new MinecraftAddressResolver.SrvRecord(0, 10, 25570, "edge.example.net")),
                Map.of("play.example.net", new String[]{"8.8.8.8"}));

        var endpoint = new MinecraftAddressResolver(dns).resolve("play.example.net", 25566, true);

        assertFalse(endpoint.fromSrv());
        assertEquals(25566, endpoint.targetPort());
        assertEquals(0, dns.srvCalls);
    }

    @Test
    void rejectsPrivateSpecialAndDocumentationAddresses() throws Exception {
        for (String value : new String[]{"127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1",
                "169.254.169.254", "100.64.0.1", "192.0.2.1", "198.18.0.1", "198.51.100.1",
                "203.0.113.1", "::1", "fc00::1", "2001:db8::1"}) {
            assertFalse(MinecraftAddressResolver.isPublic(InetAddress.getByName(value)), value);
        }
    }

    @Test
    void rejectsSrvTargetThatResolvesToPrivateAddress() {
        FakeDns dns = new FakeDns(
                List.of(new MinecraftAddressResolver.SrvRecord(0, 1, 25565, "internal.example.net")),
                Map.of(
                        "internal.example.net", new String[]{"127.0.0.1"},
                        "play.example.net", new String[]{"127.0.0.1"}));
        assertThrows(UnknownHostException.class,
                () -> new MinecraftAddressResolver(dns).resolve("play.example.net", 25565, true));
    }

    private static final class FakeDns implements MinecraftAddressResolver.DnsLookup {
        private final List<MinecraftAddressResolver.SrvRecord> srv;
        private final Map<String, String[]> addresses;
        private int srvCalls;

        private FakeDns(List<MinecraftAddressResolver.SrvRecord> srv, Map<String, String[]> addresses) {
            this.srv = srv;
            this.addresses = addresses;
        }

        @Override
        public List<MinecraftAddressResolver.SrvRecord> srv(String host) {
            srvCalls++;
            return srv;
        }

        @Override
        public InetAddress[] addresses(String host) throws UnknownHostException {
            String[] values = addresses.get(host);
            if (values == null) throw new UnknownHostException(host);
            InetAddress[] result = new InetAddress[values.length];
            for (int i = 0; i < values.length; i++) {
                result[i] = InetAddress.getByName(values[i]);
            }
            return result;
        }
    }
}
