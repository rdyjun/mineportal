package com.mineportal.pair;

import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Registers the mineportal:// URL scheme under HKCU so Windows forwards pairing
 * links straight to this app's launcher exe — no admin rights needed, no separate
 * installer step. Re-registering on every launch is cheap and idempotent, so
 * there's no need to track whether it already ran. */
public final class ProtocolRegistrar {

    private static final String SCHEME = "mineportal";

    public static void registerIfNeeded() {
        Path exe = launcherExe();
        if (exe == null || !Files.isRegularFile(exe)) return; // dev/IDE run, or jar-only layout after a self-update — nothing to point the scheme at
        try {
            importRegFile(buildRegFile(exe));
        } catch (Exception ignored) {
        }
    }

    /** The launcher command needs embedded quotes ("<exe>" "%1") so paths with spaces
     * still work. Passing that as a `reg add /d` ProcessBuilder argument hits a known
     * JDK bug where Windows command-line reconstruction mangles arguments that already
     * contain literal quote characters — the value silently never gets set. Writing a
     * .reg file and `reg import`-ing it sidesteps that: the only process argument is a
     * plain temp file path, nothing for ProcessBuilder's quoting to trip over. */
    private static String buildRegFile(Path exe) {
        String exeEscaped = exe.toString().replace("\\", "\\\\");
        String commandValue = "\\\"" + exeEscaped + "\\\" \\\"%1\\\"";
        return "Windows Registry Editor Version 5.00\r\n\r\n"
                + "[HKEY_CURRENT_USER\\Software\\Classes\\" + SCHEME + "]\r\n"
                + "@=\"URL:MinePortal Protocol\"\r\n"
                + "\"URL Protocol\"=\"\"\r\n\r\n"
                + "[HKEY_CURRENT_USER\\Software\\Classes\\" + SCHEME + "\\shell\\open\\command]\r\n"
                + "@=\"" + commandValue + "\"\r\n";
    }

    private static void importRegFile(String content) throws Exception {
        Path regFile = Files.createTempFile("mineportal-protocol", ".reg");
        try {
            // .reg files need UTF-16LE with a BOM — reg.exe won't parse a plain UTF-8 file.
            try (OutputStream out = Files.newOutputStream(regFile)) {
                out.write(new byte[]{(byte) 0xFF, (byte) 0xFE});
                out.write(content.getBytes(StandardCharsets.UTF_16LE));
            }
            new ProcessBuilder("reg", "import", regFile.toString())
                    .redirectErrorStream(true).start().waitFor();
        } finally {
            Files.deleteIfExists(regFile);
        }
    }

    /** Extracts the pairing code from a launch argument like mineportal://pair?code=ABC123. */
    public static String extractPairCode(String[] args) {
        if (args.length == 0 || args[0] == null) return null;
        try {
            URI uri = URI.create(args[0]);
            if (!SCHEME.equalsIgnoreCase(uri.getScheme())) return null;
            String query = uri.getQuery();
            if (query == null) return null;
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && "code".equals(pair.substring(0, eq))) {
                    return pair.substring(eq + 1).trim().toUpperCase();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** jpackage app-image layout is .../MinePortal/app/mineportal-client.jar next to
     * .../MinePortal/MinePortal.exe — derive the launcher from the running jar's location. */
    private static Path launcherExe() {
        try {
            URI uri = ProtocolRegistrar.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path jar = Paths.get(uri);
            if (!jar.toString().toLowerCase().endsWith(".jar")) return null;
            Path appImageRoot = jar.getParent().getParent();
            String exeName = appImageRoot.getFileName().toString() + ".exe";
            return appImageRoot.resolve(exeName);
        } catch (Exception e) {
            return null;
        }
    }

    private ProtocolRegistrar() {
    }
}
