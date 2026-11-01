package com.mineportal.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolCompatibilityTest {

    private static final int CLIENT_26_1 = 775;

    @Test
    void coversEveryProtocolFamilySeenAcrossTwentyPublicListings() {
        int[] observedFamilies = {
                759, // 1.19
                763, // 1.20-1.20.1
                767, // 1.21-1.21.1
                769, // 1.21.4
                772, // 1.21.7-1.21.8
                773, // 1.21.9-1.21.10
                774, // 1.21.11
                775, // 26.1-26.1.2
                776, // 26.2
                777  // 26.3
        };
        for (int protocol : observedFamilies) {
            ProtocolCompatibility.Plan plan = ProtocolCompatibility.plan(CLIENT_26_1, protocol);
            assertTrue(plan.canConnect(), "protocol " + protocol + ": " + plan.reason());
            assertTrue(ViaTranslation.hasTranslationPath(CLIENT_26_1, protocol),
                    "missing Via translation path for protocol " + protocol);
        }
    }

    @Test
    void unknownPositiveProtocolIsNeverSentDirectly() {
        ProtocolCompatibility.Plan plan = ProtocolCompatibility.plan(CLIENT_26_1, 999_999);
        assertEquals(ProtocolCompatibility.Mode.UNSUPPORTED, plan.mode());
        assertFalse(plan.canConnect());
    }

    @Test
    void missingPingCanProbeCurrentCodecButDoesNotPretendTranslationExists() {
        ProtocolCompatibility.Plan plan = ProtocolCompatibility.plan(CLIENT_26_1, 0);
        assertEquals(ProtocolCompatibility.Mode.PROBE_CURRENT, plan.mode());
        assertTrue(plan.canConnect());
    }
}
