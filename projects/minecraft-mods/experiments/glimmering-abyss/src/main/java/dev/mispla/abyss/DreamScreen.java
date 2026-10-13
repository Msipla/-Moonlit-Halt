package dev.mispla.abyss;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Two seconds of an actual captured scene. Does not teleport or pause the world. */
public final class DreamScreen extends Screen {
    public final int step;
    private final long opened=System.nanoTime();
    public DreamScreen(int step){super(Text.literal("梦中的微光"));this.step=step;}
    @Override public boolean shouldPause(){return false;}
    @Override public void tick(){if(client.world==null||client.player==null||!client.player.isAlive()||System.nanoTime()-opened>2_000_000_000L)close();}
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        c.fill(0,0,width,height,0xff07101a);
        float scale=Math.max(width/1280f,height/720f);
        c.getMatrices().push();c.getMatrices().translate((width-1280*scale)/2,(height-720*scale)/2,0);c.getMatrices().scale(scale,scale,1);
        c.drawTexture(GlimmeringAbyss.id("textures/gui/dream_"+step+".png"),0,0,0,0,1280,720,1280,720);c.getMatrices().pop();
        c.drawCenteredTextWithShadow(textRenderer,Text.literal(step==1?"巨柱与冷蓝洞穴":"岩台上的暖窗"),width/2,height-22,0xffe4e8ed);
    }
}
