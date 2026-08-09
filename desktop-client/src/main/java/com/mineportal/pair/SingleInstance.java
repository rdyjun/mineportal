package com.mineportal.pair;

import com.mineportal.util.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Ensures only one copy of the app runs at a time. When a second launch happens
 * (e.g. the user clicks a mineportal:// pairing link while already running), the
 * new process drops its pairing code into a hand-off file and exits immediately;
 * the already-running process picks it up on its next poll. This is file-based on
 * purpose — the app never opens a local network port for anything, pairing links
 * included. */
public final class SingleInstance {

    private static FileChannel channel;

    /** Returns true if this process holds the lock (first instance). If false, the
     * caller should hand off its pairing code (if any) via handOffAndExit and stop. */
    public static boolean acquire() {
        try {
            Path lockFile = AppPaths.dir().resolve("instance.lock");
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            return lock != null;
        } catch (IOException e) {
            return true; // fail open — a lock issue shouldn't block startup
        }
    }

    public static void handOffAndExit(String pairCode) {
        if (pairCode != null) {
            try {
                Files.writeString(AppPaths.dir().resolve("pending-pair.txt"), pairCode, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }
        System.exit(0);
    }

    /** Polled by the running instance to see if a newer launch handed off a code. */
    public static String consumePendingPairCode() {
        Path file = AppPaths.dir().resolve("pending-pair.txt");
        if (!Files.isRegularFile(file)) return null;
        try {
            String code = Files.readString(file, StandardCharsets.UTF_8).trim();
            Files.deleteIfExists(file);
            return code.isEmpty() ? null : code;
        } catch (IOException e) {
            return null;
        }
    }

    private SingleInstance() {
    }
}
