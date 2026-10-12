package dev.mispla.abyss;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PhantomEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Vanilla materials and phantom model. No textures copied, no actual phantom mob or attack AI. */
public final class CableCarRenderer extends EntityRenderer<CableCarEntity> {
    private static final Identifier PHANTOM=new Identifier("textures/entity/phantom.png");
    private final PhantomEntityModel<PhantomEntity> phantom;
    private PhantomEntity pose;
    public CableCarRenderer(EntityRendererFactory.Context context){super(context);phantom=new PhantomEntityModel<>(context.getPart(EntityModelLayers.PHANTOM));shadowRadius=1.4f;}
    @Override public Identifier getTexture(CableCarEntity entity){return PHANTOM;}
    private void box(MatrixStack m,VertexConsumerProvider v,int light,BlockState state,float x,float y,float z,float sx,float sy,float sz){
        m.push();m.translate(x,y,z);m.scale(sx,sy,sz);MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state,m,v,light,OverlayTexture.DEFAULT_UV);m.pop();
    }
    @Override public void render(CableCarEntity car,float yaw,float delta,MatrixStack m,VertexConsumerProvider v,int worldLight){
        int light=LightmapTextureManager.pack(12,0);
        var floor=Blocks.DARK_OAK_PLANKS.getDefaultState();var frame=Blocks.POLISHED_DEEPSLATE.getDefaultState();var glass=Blocks.GLASS.getDefaultState();
        box(m,v,light,floor,-1.5f,0,-1.5f,3,.2f,3);
        box(m,v,light,frame,-1.6f,3.1f,-1.6f,3.2f,.25f,3.2f);
        for(float x:new float[]{-1.5f,1.35f})for(float z:new float[]{-1.5f,1.35f})box(m,v,light,frame,x,.2f,z,.15f,2.9f,.15f);
        for(float x:new float[]{-1.5f,1.42f})box(m,v,light,glass,x,.2f,-1.4f,.08f,2.9f,2.8f);
        for(float z:new float[]{-1.5f,1.42f})box(m,v,light,glass,-1.4f,.2f,z,2.8f,2.9f,.08f);
        for(float x:new float[]{-.8f,.8f})box(m,v,light,Blocks.CHAIN.getDefaultState(),x,3.35f,-.8f,.18f,1.8f,.18f);
        // A fixed neutral scenic rig pulling the car: it cannot acquire targets, attack, burn or spawn separately.
        if(pose==null||pose.getWorld()!=car.getWorld())pose=new PhantomEntity(EntityType.PHANTOM,car.getWorld());
        pose.age=car.age;phantom.setAngles(pose,0,0,car.age+delta,0,0);
        m.push();m.translate(0,5.1,-1.8);m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));m.scale(1.8f,1.8f,1.8f);
        phantom.render(m,v.getBuffer(RenderLayer.getEntityCutoutNoCull(PHANTOM)),light,OverlayTexture.DEFAULT_UV,.65f,.88f,1,1);m.pop();
        super.render(car,yaw,delta,m,v,worldLight);
    }
}
