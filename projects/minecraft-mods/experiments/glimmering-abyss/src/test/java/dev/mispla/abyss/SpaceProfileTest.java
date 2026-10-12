package dev.mispla.abyss;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpaceProfileTest {
    private SpaceProfile profile(float airMax) {
        return new SpaceProfile("test",new float[]{.2f,.5f,.8f},.18f,new float[]{-20,52,-80},92,.95f,
                new float[]{-.25f,.6f,-.75f},new float[]{.04f,.19f,.3f},30,235,airMax);
    }
    @Test void localNativeFullLightIsProtected() {
        assertEquals(0,SpaceProfile.nativeLightProtection(15),1e-6);
        assertEquals(1,SpaceProfile.nativeLightProtection(0),1e-6);
        for(int i=1;i<=15;i++)assertTrue(SpaceProfile.nativeLightProtection(i)<=SpaceProfile.nativeLightProtection(i-1));
    }
    @Test void perspectiveCannotEraseTheScene() {
        var p=profile(.22f);assertEquals(0,p.airMix(10));
        assertEquals(.22f,p.airMix(10000),1e-6);assertTrue(p.field(-20,52,-80)>p.field(0,39,74));
        assertThrows(IllegalArgumentException.class,()->profile(1));
    }
    @Test void invalidConfigurationDoesNotReachTheShader() {
        assertThrows(IllegalArgumentException.class,()->new SpaceProfile("bad",new float[]{Float.NaN,.5f,.8f},.2f,
                new float[]{0,0,0},100,1,new float[]{0,1,0},new float[]{0,0,0},0,100,.2f));
    }
}
