package dev.mispla.abyss;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AbyssShapeTest {
    @Test void solidCapAndBedrockBufferAcrossExpandedFootprint(){
        for(int x=-208;x<=127;x+=4)for(int z=-320;z<=127;z+=4){
            for(int layout:new int[]{100,101}){var c=AbyssShape.column(x,z,AbyssShape.ceiling(x,z),layout);assertTrue(c.rock(-63));assertTrue(c.rock(AbyssShape.ceiling(x,z)));}
        }
    }
    @Test void continuousDescendingRouteHasNoLateralReversal(){
        for(int upper:new int[]{101,150,241}){
            var last=CableRoute.at(0,upper,100);assertEquals(upper,last.y(),1e-9);
            double speed=0;
            for(int i=1;i<=900;i++){
                var p=CableRoute.at(i/900.0,upper,100);
                assertTrue(p.y()<=last.y()+1e-8);assertTrue(p.x()>=last.x()-1e-8);assertTrue(p.z()<=last.z()+1e-8);
                double v=Math.sqrt(Math.pow(p.x()-last.x(),2)+Math.pow(p.y()-last.y(),2)+Math.pow(p.z()-last.z(),2));
                assertTrue(Math.abs(v-speed)<.012,"Abrupt speed change");speed=v;last=p;
            }
            assertEquals(24.5,last.x(),1e-8);assertEquals(58.5,last.z(),1e-8);assertEquals(45,last.y(),1e-8);
        }
    }
    @Test void cabinClearanceBelowShaft(){
        for(int i=0;i<=900;i++){
            var p=CableRoute.at(i/900.0,150,100);if(p.y()>80)continue;
            for(int x=(int)Math.floor(p.x()-1.5);x<=Math.floor(p.x()+1.5);x++)for(int z=(int)Math.floor(p.z()-1.5);z<=Math.floor(p.z()+1.5);z++)for(int y=(int)Math.floor(p.y()-6);y<=Math.floor(p.y()+3.3-6);y++)
                for(int layout:new int[]{100,101})assertFalse(AbyssShape.column(x,z,AbyssShape.ceiling(x,z),layout).rock(y),"Cabin obstruction at "+x+","+y+","+z);
        }
    }
    @Test void distantVaultsRemainConnectedToCentralRift(){
        for(int[] p:new int[][]{{-42,36,-77},{-78,36,-222},{-167,36,-187},{-153,36,-252}})assertFalse(AbyssShape.column(p[0],p[2]).rock(p[1]));
    }
    @Test void localEastWallRevisionPreservesMainRiftAndRemoteVaults(){
        for(int x=-208;x<=127;x+=5)for(int z=-320;z<=127;z+=5){
            if(x>72&&Math.abs(z+10)<140)continue;
            var old=AbyssShape.column(x,z,AbyssShape.ceiling(x,z),100);var next=AbyssShape.column(x,z,AbyssShape.ceiling(x,z),101);
            for(int y=-63;y<111;y+=3)assertEquals(old.rock(y),next.rock(y),"Changed outside east-wall scope");
        }
    }
}
