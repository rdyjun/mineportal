package com.mineportal.protocol;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

/** Central protocol decision policy shared by the backend relay and desktop agent. */
public final class ProtocolCompatibility {

    public enum Mode {
        DIRECT,
        TRANSLATED,
        PROBE_CURRENT,
        UNSUPPORTED
    }

    public record Plan(Mode mode, int clientProtocol, int serverProtocol, String reason) {
        public boolean canConnect() {
            return mode != Mode.UNSUPPORTED;
        }
    }

    private ProtocolCompatibility() {
    }

    public static Plan plan(int clientProtocol, int serverProtocol) {
        if (serverProtocol <= 0) {
            return new Plan(Mode.PROBE_CURRENT, clientProtocol, serverProtocol,
                    "server status did not report a protocol; try the current codec once");
        }
        if (serverProtocol == clientProtocol) {
            return new Plan(Mode.DIRECT, clientProtocol, serverProtocol, "same protocol");
        }
        if (ProtocolVersion.isRegistered(serverProtocol)) {
            return new Plan(Mode.TRANSLATED, clientProtocol, serverProtocol,
                    "translate through the registered ViaVersion protocol graph");
        }
        return new Plan(Mode.UNSUPPORTED, clientProtocol, serverProtocol,
                "server reported an unknown protocol id " + serverProtocol);
    }
}
