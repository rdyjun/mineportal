package com.mineportal.server.status;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class PublicAddressResolverTest {

    @Test
    void rejectsPrivateAndSpecialPurposeAddresses() throws Exception {
        for (String value : new String[]{"127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1",
                "169.254.169.254", "100.64.0.1", "0.0.0.1", "198.18.0.1", "::1", "fc00::1"}) {
            assertFalse(PublicAddressResolver.isPublic(InetAddress.getByName(value)), value);
        }
    }

    @Test
    void acceptsPublicIpv4AndIpv6Addresses() throws Exception {
        assertTrue(PublicAddressResolver.isPublic(InetAddress.getByName("1.1.1.1")));
        assertTrue(PublicAddressResolver.isPublic(InetAddress.getByName("2606:4700:4700::1111")));
    }

    @Test
    void bracketedLoopbackCannotBypassValidation() {
        PublicAddressResolver resolver = new PublicAddressResolver();
        assertThrows(Exception.class, () -> resolver.resolve("[::1]"));
    }
}
