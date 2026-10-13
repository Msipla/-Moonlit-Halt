package dev.mispla.abyss;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CavernShapeTest {
    @Test void closedEnvelopeAndBedrockBuffer(){
        for(int x=-112;x<=111;x+=3)for(int z=-176;z<=111;z+=3){
            var c=CavernShape.column(x,z);assertTrue(c.rock(-63));assertTrue(c.rock(CavernShape.ceiling(x,z)));
            if(x<-108||x>107||z<-172||z>107)for(int y=-63;y<111;y+=3)assertTrue(c.rock(y));
        }
    }
    @Test void scenicRouteHasNaturalCavernClearance(){
        for(int i=550;i<=1000;i++){
            var p=CableRoute.at(i/1000.0,150,75);
            for(int x=(int)Math.floor(p.x()-1.5);x<=Math.floor(p.x()+1.5);x++)for(int z=(int)Math.floor(p.z()-1.5);z<=Math.floor(p.z()+1.5);z++)for(int y=(int)Math.floor(p.y()-6);y<=Math.floor(p.y()+3.3-6);y++)
                assertFalse(CavernShape.column(x,z).rock(y),"Route obstruction at "+x+","+y+","+z+" t="+i);
        }
    }
    @Test void twoDocksAndSubstantialHorizontalExcursion(){
        for(int upper:new int[]{101,150,241}){
            var top=CableRoute.at(0,upper,75);var bottom=CableRoute.at(1,upper,75);
            assertEquals(.5,top.x(),1e-9);assertEquals(upper,top.y(),1e-9);assertEquals(45,bottom.y(),1e-9);assertEquals(70.5,bottom.z(),1e-9);
            assertTrue(Math.abs(CableRoute.at(.775,upper,75).x()-.5)>25);
            double last=upper;
            for(int i=1;i<=1000;i++){double y=CableRoute.at(i/1000.0,upper,75).y();assertTrue(y<=last+1e-8);last=y;}
        }
    }
}
