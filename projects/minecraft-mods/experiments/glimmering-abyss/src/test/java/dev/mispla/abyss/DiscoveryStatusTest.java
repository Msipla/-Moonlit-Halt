package dev.mispla.abyss;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveryStatusTest {
    @Test void waitingStatesCannotBeMistakenForSleepUnlock(){
        assertTrue(DiscoveryStatus.describe("search",0,15,"coverage",0).contains("自动重选"));
        assertTrue(DiscoveryStatus.describe("check",31,15,"",0).contains("31/252"));
        assertTrue(DiscoveryStatus.describe("carve",42,15,"",0).contains("生成"));
        assertTrue(DiscoveryStatus.describe("unavailable",0,4096,"No covered candidate",0).contains("继续睡觉不会"));
    }
    @Test void readyStatesExplainDiscoveryWithoutBlockingACraftedGuide(){
        for(int dream=0;dream<=2;dream++)assertTrue(DiscoveryStatus.describe("ready",252,15,"",dream).contains("定位")||DiscoveryStatus.describe("ready",252,15,"",dream).contains("指引"));
    }
}
