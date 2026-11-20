package dev.mispla.abyss;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Sleep-linked HUD dissolve. Leaves vanilla sleep/wake controls and gameplay intact. */
public final class DreamScreen {
    private DreamScreen(){}
    public static int step,renderedFrames;
    private static long opened,waking;
    private static boolean asleep;
    public static void start(int value,boolean sleeping){step=value;renderedFrames=0;opened=System.nanoTime();waking=0;asleep=sleeping;}
    public static void wake(){if(step>0&&waking==0)waking=System.nanoTime();}
    public static void clear(){step=0;opened=waking=0;asleep=false;}
    public static float alpha(){
        if(step==0)return 0;
        long now=System.nanoTime();
        if(!asleep&&waking==0&&now-opened>1_800_000_000L)waking=now;
        double in=Math.min(1,(now-opened)/650_000_000.0);
        double out=waking==0?1:Math.max(0,1-(now-waking)/850_000_000.0);
        if(out==0){clear();return 0;}
        return (float)(in*in*(3-2*in)*out*out*(3-2*out));
    }
    public static void render(DrawContext c,float delta){
        var client=MinecraftClient.getInstance();
        if(client.player==null||client.world==null||!client.player.isAlive()){clear();return;}
        float opacity=alpha();if(opacity<=0)return;
        int width=client.getWindow().getScaledWidth(),height=client.getWindow().getScaledHeight();
        float scale=Math.max(width/1280f,height/720f);
        if(client.player.isSleeping())c.enableScissor(0,0,width,Math.max(0,height-40));
        c.fill(0,0,width,height,((int)(255*opacity)<<24)|0x07101a);
        RenderSystem.enableBlend();c.setShaderColor(1,1,1,opacity);
        c.getMatrices().push();c.getMatrices().translate((width-1280*scale)/2,(height-720*scale)/2,0);c.getMatrices().scale(scale,scale,1);
        c.drawTexture(GlimmeringAbyss.id("textures/gui/dream_"+step+".png"),0,0,0,0,1280,720,1280,720);c.getMatrices().pop();
        c.setShaderColor(1,1,1,1);RenderSystem.disableBlend();if(client.player.isSleeping())c.disableScissor();renderedFrames++;
    }
}
