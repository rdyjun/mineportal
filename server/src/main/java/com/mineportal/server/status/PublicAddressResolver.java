package com.mineportal.server.status;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Resolves an outbound target once and rejects addresses that are not globally routable. */
@Component
public class PublicAddressResolver {

    public InetAddress resolve(String rawHost) throws UnknownHostException {
        String host = normalize(rawHost);
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) throw new UnknownHostException(host);
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new UnknownHostException("private or non-routable address is not allowed");
            }
        }
        // Preserve the requested hostname without another DNS lookup. Minecraft uses it as the
        // virtual host in its handshake while the socket connects to these already-checked bytes.
        return InetAddress.getByAddress(host, addresses[0].getAddress());
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return false;

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if (first == 0 || (first == 100 && second >= 64 && second <= 127)) return false;
            if (first == 192 && second == 0 && (bytes[2] & 0xff) == 0) return false;
            if (first == 198 && (second == 18 || second == 19)) return false;
            return first < 224 && first != 255;
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) != 0xfc; // fc00::/7 unique-local addresses
        }
        return false;
    }

    private static String normalize(String rawHost) throws UnknownHostException {
        if (rawHost == null) throw new UnknownHostException("host is required");
        String host = rawHost.trim();
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        if (host.isBlank() || host.length() > 253) throw new UnknownHostException("invalid host");
        return host;
    }
}
