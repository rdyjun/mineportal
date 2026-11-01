package com.mineportal.protocol;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

/** Resolves Minecraft's optional DNS SRV endpoint once and pins the selected address bytes. */
public final class MinecraftAddressResolver {

    public static final int DEFAULT_PORT = 25565;

    public record SrvRecord(int priority, int weight, int port, String target) {
    }

    public record ResolvedEndpoint(
            String logicalHost,
            int requestedPort,
            String targetHost,
            int targetPort,
            InetAddress address,
            boolean fromSrv
    ) {
        public InetSocketAddress socketAddress() {
            return new InetSocketAddress(address, targetPort);
        }
    }

    interface DnsLookup {
        List<SrvRecord> srv(String host);

        InetAddress[] addresses(String host) throws UnknownHostException;
    }

    private static final class SystemDnsLookup implements DnsLookup {
        @Override
        public List<SrvRecord> srv(String host) {
            List<SrvRecord> records = new ArrayList<>();
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            InitialDirContext context = null;
            try {
                context = new InitialDirContext(env);
                Attributes attributes = context.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"});
                Attribute values = attributes.get("SRV");
                if (values == null) return List.of();
                NamingEnumeration<?> all = values.getAll();
                while (all.hasMore()) {
                    String[] parts = String.valueOf(all.next()).trim().split("\\s+");
                    if (parts.length != 4) continue;
                    int port = Integer.parseInt(parts[2]);
                    String target = stripTrailingDot(parts[3]);
                    if (validPort(port) && !target.isBlank() && !".".equals(target)) {
                        records.add(new SrvRecord(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), port, target));
                    }
                }
            } catch (Exception ignored) {
                return List.of();
            } finally {
                if (context != null) {
                    try {
                        context.close();
                    } catch (Exception ignored) {
                    }
                }
            }
            records.sort(Comparator.comparingInt(SrvRecord::priority)
                    .thenComparing(Comparator.comparingInt(SrvRecord::weight).reversed())
                    .thenComparing(SrvRecord::target));
            return List.copyOf(records);
        }

        @Override
        public InetAddress[] addresses(String host) throws UnknownHostException {
            return InetAddress.getAllByName(host);
        }
    }

    private final DnsLookup dns;

    public MinecraftAddressResolver() {
        this(new SystemDnsLookup());
    }

    MinecraftAddressResolver(DnsLookup dns) {
        this.dns = dns;
    }

    public ResolvedEndpoint resolve(String rawHost, int port, boolean requirePublic) throws UnknownHostException {
        String logicalHost = normalizeHost(rawHost);
        if (!validPort(port)) throw new UnknownHostException("invalid port");

        List<SrvRecord> candidates = port == DEFAULT_PORT && !isIpLiteral(logicalHost)
                ? dns.srv(logicalHost) : List.of();
        UnknownHostException lastFailure = null;
        for (SrvRecord candidate : candidates) {
            try {
                return resolveTarget(logicalHost, port, candidate.target(), candidate.port(), true, requirePublic);
            } catch (UnknownHostException e) {
                lastFailure = e;
            }
        }
        try {
            return resolveTarget(logicalHost, port, logicalHost, port, false, requirePublic);
        } catch (UnknownHostException directFailure) {
            if (lastFailure != null) directFailure.addSuppressed(lastFailure);
            throw directFailure;
        }
    }

    private ResolvedEndpoint resolveTarget(String logicalHost, int requestedPort, String targetHost,
                                           int targetPort, boolean fromSrv, boolean requirePublic)
            throws UnknownHostException {
        InetAddress[] addresses = dns.addresses(targetHost);
        if (addresses.length == 0) throw new UnknownHostException(targetHost);
        if (requirePublic) {
            for (InetAddress candidate : addresses) {
                if (!isPublic(candidate)) {
                    throw new UnknownHostException("private or non-routable address is not allowed");
                }
            }
        }
        // The socket uses already-resolved bytes. The alias preserves the user's original virtual host
        // in MCProtocolLib's intention packet without a second DNS lookup.
        InetAddress pinned = InetAddress.getByAddress(logicalHost, addresses[0].getAddress());
        return new ResolvedEndpoint(logicalHost, requestedPort, targetHost, targetPort, pinned, fromSrv);
    }

    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return false;

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            int third = bytes[2] & 0xff;
            if (first == 0 || (first == 100 && second >= 64 && second <= 127)) return false;
            if (first == 192 && second == 0 && (third == 0 || third == 2)) return false;
            if (first == 192 && second == 88 && third == 99) return false;
            if (first == 198 && (second == 18 || second == 19)) return false;
            if (first == 198 && second == 51 && third == 100) return false;
            if (first == 203 && second == 0 && third == 113) return false;
            return first < 224 && first != 255;
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            if ((first & 0xfe) == 0xfc) return false;
            return !(first == 0x20 && (bytes[1] & 0xff) == 0x01
                    && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8);
        }
        return false;
    }

    static String normalizeHost(String rawHost) throws UnknownHostException {
        if (rawHost == null) throw new UnknownHostException("host is required");
        String host = rawHost.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        host = stripTrailingDot(host);
        if (host.isBlank() || host.length() > 253 || host.indexOf('\0') >= 0) {
            throw new UnknownHostException("invalid host");
        }
        return host;
    }

    private static boolean validPort(int port) {
        return port >= 1 && port <= 65535;
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$");
    }

    private static String stripTrailingDot(String host) {
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }
}
