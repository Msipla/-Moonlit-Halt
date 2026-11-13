package dev.mispla.abyss;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.literal;

/** No destructive regeneration, no candidate coordinates, no survival bypass. */
public final class CreativeDebug {
    private CreativeDebug(){}
    private static boolean creative(ServerCommandSource s){return s.getEntity() instanceof net.minecraft.server.network.ServerPlayerEntity p&&p.isCreative();}
    public static void initialize(){
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(literal("abyss").then(literal("debug").requires(CreativeDebug::creative)
            .then(literal("status").executes(c->{var p=c.getSource().getPlayerOrThrow();var s=OverworldSite.get(p.getServer().getOverworld());p.sendMessage(Text.literal(DiscoveryStatus.describe(s.stage,s.cursor,s.attempt,s.reason,s.dreams.getOrDefault(p.getUuid(),0),s.chunks())+" 布局版本："+s.layout),false);return 1;}))
            .then(literal("entrance").executes(c->land(c.getSource(),true)))
            .then(literal("town").executes(c->land(c.getSource(),false)))
            .then(literal("dream").then(literal("1").executes(c->{Journey.previewDream(c.getSource().getPlayerOrThrow(),1);return 1;})).then(literal("2").executes(c->{Journey.previewDream(c.getSource().getPlayerOrThrow(),2);return 1;})))
            .then(literal("ride").executes(c->{var p=c.getSource().getPlayerOrThrow();var s=OverworldSite.get(p.getServer().getOverworld());if(!s.ready()||!p.getWorld().getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)||p.getPos().squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(s.landing(p.getY()>100)))>100){p.sendMessage(Text.literal("请先定位到已生成的安全站台，再测试乘车。"),false);return 0;}Journey.board(p,s,p.getY()>100);return 1;}))
        )));
    }
    private static int land(ServerCommandSource source,boolean upper)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var p=source.getPlayerOrThrow();var s=OverworldSite.get(source.getServer().getOverworld());
        if(!s.ready()){p.sendMessage(Text.literal(DiscoveryStatus.describe(s.stage,s.cursor,s.attempt,s.reason,0,s.chunks())),false);return 0;}
        if(p.hasVehicle()){p.sendMessage(Text.literal("请先结束乘车。"),false);return 0;}
        // Reuse safe landing only after world generation has completed.
        Journey.land(p,s,upper);p.sendMessage(Text.literal("测试定位：已到达生成完成的安全站台。"),false);return 1;
    }
}
