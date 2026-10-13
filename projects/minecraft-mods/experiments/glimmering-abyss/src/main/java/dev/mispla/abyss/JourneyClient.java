package dev.mispla.abyss;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class JourneyClient implements ClientModInitializer {
    private static boolean ready;
    public static int x,z,entryY,approachLength,approachY,layout;
    private static int dreamPending,wakeTicks;
    @Override public void onInitializeClient(){
        EntityRendererRegistry.register(Journey.CAR,CableCarRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(Journey.SITE_PACKET,(client,handler,b,response)->{
            boolean ok=b.readBoolean();int nx=b.readInt(),nz=b.readInt(),ny=b.readInt(),nl=b.readInt(),na=b.readInt(),version=b.readInt();
            client.execute(()->{ready=ok;x=nx;z=nz;entryY=ny;approachLength=nl;approachY=na;layout=version;});
        });
        ClientPlayNetworking.registerGlobalReceiver(Journey.DREAM_PACKET,(client,handler,b,response)->{int step=b.readInt();client.execute(()->{dreamPending=step;wakeTicks=0;});});
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(dreamPending>0&&client.player!=null&&!client.player.isSleeping()&&client.currentScreen==null&&++wakeTicks>=3){int step=dreamPending;dreamPending=0;client.setScreen(new DreamScreen(step));}
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{ready=false;dreamPending=0;wakeTicks=0;});
    }
    public static boolean ready(){return ready;}
    public static BlockPos origin(){
        var c=MinecraftClient.getInstance();if(!ready||c.world==null||!c.world.getRegistryKey().equals(World.OVERWORLD))return null;
        var p=c.gameRenderer.getCamera().getPos();
        if(p.x<x-120||p.x>x+120||p.z<z-184||p.z>z+120||p.y< -60||p.y>116)return null;
        return new BlockPos(x,OverworldSite.OFFSET_Y,z);
    }
}
