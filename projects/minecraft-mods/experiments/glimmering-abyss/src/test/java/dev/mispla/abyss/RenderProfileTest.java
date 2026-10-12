package dev.mispla.abyss;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RenderProfileTest {
    @Test void baselineAndFullLightStayUnchanged(){for(int i=0;i<16;i++)assertEquals(0xff3d2817,RenderProfile.A.lightmapColor(0xff3d2817,i));assertEquals(0xffc9dab7,RenderProfile.B.lightmapColor(0xffc9dab7,15));assertEquals(0xffc9dab7,RenderProfile.C.lightmapColor(0xffc9dab7,15));}
    @Test void distanceDoesNotEraseSilhouettes(){assertEquals(0,RenderProfile.B.distanceMix(10));float old=0;for(int i=0;i<600;i++){float v=RenderProfile.C.distanceMix(i);assertTrue(v>=old&&v<=.64001f);old=v;}assertTrue(RenderProfile.C.distanceMix(160)<RenderProfile.B.distanceMix(160));assertTrue(1-RenderProfile.C.distanceMix(600)>.35f);}
}
