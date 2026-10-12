package dev.mispla.abyss;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Small depth-tested surface accents on existing sources, not additional illumination. */
final class GlowMarkers {
    private static final java.util.List<Terrain.Fixture> FIXTURES=Terrain.fixtures(Terrain.SEED);
    private static final RenderLayer LAYER=RenderLayer.getEyes(new Identifier("minecraft","textures/block/white_concrete.png"));
    private static final VertexConsumerProvider.Immediate BUFFER=VertexConsumerProvider.immediate(new BufferBuilder(4096));
    static int facesDrawn;
    static void render(WorldRenderContext context) {
        facesDrawn=0;if(!EnvironmentClient.customActive()||EnvironmentClient.profile().glow()<=0)return;
        var matrices=context.matrixStack();var camera=context.camera().getPos();matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);
        var matrix=matrices.peek().getPositionMatrix();var normal=matrices.peek().getNormalMatrix();var vertices=BUFFER.getBuffer(LAYER);float glow=EnvironmentClient.profile().glow();
        for(var f:FIXTURES) {
            var pos=new BlockPos(f.x(),f.y(),f.z());if(!context.world().getBlockState(pos).isOf(GlimmeringAbyss.GLIMMER))continue;
            for(var direction:Direction.values()) {
                if(!context.world().getBlockState(pos.offset(direction)).isAir())continue;facesDrawn++;
                float nx=direction.getOffsetX(),ny=direction.getOffsetY(),nz=direction.getOffsetZ();
                float ux,uz,vy,vz;if(ny!=0){ux=1;uz=0;vy=0;vz=-ny;}else{ux=nz;uz=-nx;vy=1;vz=0;}
                float cx=f.x()+.5f+nx*.502f,cy=f.y()+.5f+ny*.502f,cz=f.z()+.5f+nz*.502f;
                for(int i=0;i<4;i++) {
                    float a=i==0||i==3?-.14f:.14f,b=i<2?-.14f:.14f;
                    vertices.vertex(matrix,cx+ux*a,cy+vy*b,cz+uz*a+vz*b).color(.18f*glow,.72f*glow,.76f*glow,1)
                            .texture(i==0||i==3?0:1,i<2?0:1).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(normal,nx,ny,nz).next();
                }
            }
        }
        BUFFER.draw(LAYER);matrices.pop();EnvironmentClient.applyDistance();
    }
}
