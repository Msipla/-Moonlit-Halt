package lab;

import com.dwinovo.numen.api.NumenPlugins;
import com.dwinovo.numen.api.permission.Action;
import com.dwinovo.numen.api.permission.PlacedBlocks;
import com.dwinovo.numen.api.sdk.Fn;
import com.dwinovo.numen.api.sdk.Pending;
import com.dwinovo.numen.api.sdk.ServerCall;
import com.google.gson.Gson;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.eureka.ship.EurekaShipControl;
import org.valkyrienskies.eureka.EurekaConfig;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;

import static lab.EngineeringSite.Site.*;

public final class EngineeringShips {
    private static final Gson JSON = new Gson();
    private static final String HELM = "vs_eureka:oak_ship_helm";
    private static final String ANCHOR = "vs_eureka:anchor";
    // Server-level keys isolate dimensions and sequential integrated-server worlds.
    private static final Map<class_3218, Session> SESSIONS = new java.util.WeakHashMap<>();

    private static final class Session {
        final UUID operator;
        final long id;
        final class_2338 anchorOffset;
        class_3222 pilot;
        class_3222 manualOwner;
        boolean awaitingPilot;
        int until;

        Session(UUID operator, long id, class_2338 anchorOffset) {
            this.operator = operator;
            this.id = id;
            this.anchorOffset = anchorOffset;
        }
    }

    public static void install() {
        NumenPlugins.register("engineering_vessels", api -> api.api("ship",
            "Use engineering.site.info().hull and batch-place your hull. In Lua print " +
            "engineering_vessels.ship.assemble(helmPos), then inspect() after loading. " +
            "Drive takes (heading,forward,up,left,ticks); left turns. The pulse ends and anchors automatically. " +
            "Use inspect() for actual motion and inspect_onboard for machinery relative to the helm. "+
            "After anchoring, handover lets the owner control the native helm and cruise inside the site.", Tools.class));
        ServerTickEvents.END_WORLD_TICK.register(EngineeringShips::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            var world = server.method_30002();
            var session = SESSIONS.remove(world);
            if (session != null && (session.pilot != null || session.manualOwner != null)) stop(world,session);
        });
    }

    public static final class Tools {
        public record Hull(class_2338 helm) {}
        public record Drive(String heading, double forward, double up, double left, int ticks) {}
        public record Empty() {}
        public record Offset(int dx,int dy,int dz) {}

        @Fn("Assemble a connected companion-built hull at an oak helm. Read engineering.site.info() for the hull palette and limit. Include one oak_ship_helm with horizontal facing, one anchor, and balloon or floater as needed.")
        public static Pending<String> assemble(ServerCall call, Hull args) {
            var world = level(call);
            var original = hull(call, world, args.helm());
            Pending<Void> permissions = Pending.of(null);
            for (var cell : original.entrySet()) {
                permissions = permissions.thenWait(ignored -> call.authorize(Action.breakBlock(cell.getKey(), cell.getValue())));
            }
            return permissions.then(ignored -> {
                var current = level(call);
                require(current==world && original.equals(hull(call, world,args.helm())), "hull changed before assembly");
                var backup = backup(world, original.keySet());
                // The VS relocation used by DSH BoatAssembly; pass precisely the approved cells.
                var ship = ShipAssembler.assembleToShip(world,original.keySet(),1.0);
                require(ship != null,"Eureka rejected assembly. Backup engineering:"+backup);
                for (var cell : original.keySet()) {
                    if (world.method_8320(cell).method_26215()) PlacedBlocks.of(world).forget(cell);
                }
                var anchor=original.entrySet().stream().filter(e->id(e.getValue()).equals(ANCHOR)).findFirst().orElseThrow().getKey();
                var session=new Session(call.companion(),ship.getId(),anchor.method_10059(args.helm()));
                SESSIONS.put(world,session);
                return JSON.toJson(Map.of("id",Long.toString(session.id),"backup","engineering:"+backup,
                    "cells",original.size(),"state","assembling; inspect after the ship has loaded"));
            });
        }

        @Fn("Read the authorized ship's world position, quaternion, velocity, anchor state and pilot status.")
        public static String inspect(ServerCall call,Empty args) {
            var world=level(call);
            var session=session(call,world);
            var ship=loaded(world,session);
            if (ship==null) return JSON.toJson(Map.of("id",Long.toString(session.id),"state","loading"));
            return sample(ship,session);
        }

        @Fn("Read a supported loaded onboard block at dx,dy,dz from the helm, using ship-local block axes.")
        public static lab.EngineeringSite.Site.Reading inspectOnboard(ServerCall call,Offset a) {
            var world=level(call);
            var ship=loaded(world,session(call,world));
            require(ship!=null && withinSite(ship),"ship must be loaded inside the configured site");
            require(a.dx()>=-47 && a.dx()<=47 && a.dy()>=-31 && a.dy()<=31
                && a.dz()>=-47 && a.dz()<=47,"offset outside site dimensions");
            var helm=LabShips.findHelm(world,ship);
            require(helm!=null,"loaded ship helm is unavailable");
            var at=helm.method_10069(a.dx(),a.dy(),a.dz());
            require(onboard(world,ship,at),"offset has no supported block in the loaded hull");
            return read(world,at,source->onboard(world,ship,source));
        }

        @Fn("With owner approval, hand over the anchored ship to the owner at the native Eureka helm for manual cruising inside the site.")
        public static Pending<String> handover(ServerCall call,Empty args) {
            var world=level(call);
            var session=session(call,world);
            var ship=loaded(world,session);
            require(ship!=null && ship.isStatic() && withinSite(ship),"park the ship inside the configured site first");
            var helm=LabShips.findHelm(world,ship);
            require(helm!=null,"loaded ship helm is unavailable");
            return call.authorize(Action.useBlock(helm,world.method_8320(helm))).then(ignored -> {
                require(level(call)==world && session(call,world)==session && loaded(world,session)==ship
                    && ship.isStatic() && withinSite(ship),"ship or site changed before handover");
                var owner=call.her().resolveOwnerPlayer();
                var control=ship.getAttachment(EurekaShipControl.class);
                require(control!=null && control.getAnchors()==1 && control.getSeatedPlayer()==null,
                    "handover requires one anchor and an empty helm");
                session.manualOwner=owner;
                session.awaitingPilot=true;
                return sample(ship,session);
            });
        }

        @Fn("Pilot the authorized ship for 1..100 ticks within the configured site, then anchor. Inputs -1..1; left applies yaw. Heading is ship-local north/east/south/west.")
        public static Pending<String> drive(ServerCall call,Drive a) {
            var world=level(call);
            var session=session(call,world);
            var heading=class_2350.method_10168(a.heading().toLowerCase(Locale.ROOT));
            require(heading!=null && heading!=class_2350.field_11036 && heading!=class_2350.field_11033,"expected horizontal heading");
            require(a.ticks()>=1 && a.ticks()<=100 &&
                List.of(a.forward(),a.up(),a.left()).stream().allMatch(x -> Double.isFinite(x)&&Math.abs(x)<=1),
                "expected bounded inputs and 1..100 ticks");
            var ship=loaded(world,session);
            require(ship!=null && withinSite(ship),"ship must be loaded with clearance from the site edge");
            var helm=LabShips.findHelm(world,ship);
            require(helm!=null,"loaded ship helm is unavailable");
            return call.authorize(Action.useBlock(helm,world.method_8320(helm))).then(ignored -> {
                require(level(call)==world && session(call,world)==session &&
                    loaded(world,session)==ship && withinSite(ship),"ship or site changed before drive");
                var control=ship.getAttachment(EurekaShipControl.class);
                require(control!=null && control.getAnchors()==1,"expected one installed Eureka anchor");
                require(session.manualOwner==null,"anchor the manual voyage before starting an engineering pulse");
                require(LabShips.engage(world,ship,control,call.her(),heading,
                    (float)a.forward(),(float)a.up(),(float)a.left()),"native helm refused the companion mount");
                session.pilot=call.her();
                session.until=world.method_8503().method_3780()+a.ticks();
                return sample(ship,session);
            });
        }

        @Fn("Release the companion's control and engage the installed anchor on the authorized ship.")
        public static String anchor(ServerCall call,Empty args) {
            var world=level(call);
            var session=session(call,world);
            var ship=loaded(world,session);
            require(ship!=null,"ship is not loaded");
            var control=ship.getAttachment(EurekaShipControl.class);
            require(control!=null && control.getAnchors()==1,"expected one installed Eureka anchor");
            require(control.getSeatedPlayer()==null || control.getSeatedPlayer()==call.her()
                || control.getSeatedPlayer()==session.manualOwner,"helm is occupied by another rider");
            stop(world,session);
            return sample(ship,session);
        }
    }

    private static Map<class_2338,class_2680> hull(ServerCall call,class_3218 world,class_2338 helm) {
        require(!SESSIONS.containsKey(world),"a ship is already designated for this world session");
        check(world,helm);
        require(id(world.method_8320(helm)).equals(HELM),"expected an oak Eureka helm");
        var joined=new LinkedHashMap<class_2338,class_2680>();
        var queue=new ArrayDeque<class_2338>();
        var seen=new LinkedHashSet<class_2338>();
        queue.add(helm);
        int nativeLimit=EurekaConfig.SERVER.getMaxShipBlocks();
        int limit=nativeLimit>0 ? Math.min(nativeLimit,EngineeringSite.MAX_HULL_CELLS) : EngineeringSite.MAX_HULL_CELLS;
        int helms=0,anchors=0;
        int minX=helm.method_10263(),maxX=minX,minY=helm.method_10264(),maxY=minY,minZ=helm.method_10260(),maxZ=minZ;
        while (!queue.isEmpty()) {
            var at=queue.remove();
            if (!seen.add(at)) continue;
            require(world.method_37118(at),"the ship hull and neighboring cells must already be loaded");
            var state=world.method_8320(at);
            if (!EngineeringSite.HULL.contains(id(state))) continue;
            check(world,at);
            var placer=PlacedBlocks.of(world).placerAt(at,state);
            require(placer!=null && placer.id().equals(call.her().method_5667()),
                "every hull cell must have been placed by the calling companion");
            joined.put(at,state);
            require(joined.size()<=limit,"hull exceeds "+limit+" cells");
            int ax=at.method_10263(),ay=at.method_10264(),az=at.method_10260();
            minX=Math.min(minX,ax); maxX=Math.max(maxX,ax);
            minY=Math.min(minY,ay); maxY=Math.max(maxY,ay);
            minZ=Math.min(minZ,az); maxZ=Math.max(maxZ,az);
            require((long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1)<=LabShips.MAX_HULL_VOLUME,
                "hull bounding volume exceeds "+LabShips.MAX_HULL_VOLUME+" cells");
            if (id(state).equals(HELM)) helms++;
            if (id(state).equals(ANCHOR)) anchors++;
            // Eureka's collector can include diagonal neighbors even with its diagonals option disabled.
            for (int x=-1;x<=1;x++) for (int y=-1;y<=1;y++) for (int z=-1;z<=1;z++) {
                if (x!=0 || y!=0 || z!=0) queue.add(at.method_10069(x,y,z));
            }
        }
        require(helms==1 && anchors==1,"hull must contain exactly one oak helm and one anchor");
        return joined;
    }

    private static Session session(ServerCall call,class_3218 world) {
        var session=SESSIONS.get(world);
        require(session!=null && session.operator.equals(call.companion()),"no ship assembled by this companion");
        return session;
    }

    private static LoadedServerShip loaded(class_3218 world,Session session) {
        for (var ship:((IShipObjectWorldServerProvider)world).getShipObjectWorld().getLoadedShips()) {
            if (ship.getId()==session.id) return ship;
        }
        return null;
    }

    private static boolean withinSite(LoadedServerShip ship) {
        var box=ship.getWorldAABB();
        return box!=null && inSite(box.minX()-1, box.minY()-1, box.minZ()-1) &&
            inSite(box.maxX()+1, box.maxY()+1, box.maxZ()+1);
    }

    private static boolean onboard(class_3218 world,LoadedServerShip ship,class_2338 at) {
        var box=ship.getShipAABB();
        int x=at.method_10263(),y=at.method_10264(),z=at.method_10260();
        return box!=null && x>=box.minX() && x<=box.maxX() && y>=box.minY() && y<=box.maxY()
            && z>=box.minZ() && z<=box.maxZ() && world.method_37118(at)
            && EngineeringSite.HULL.contains(id(world.method_8320(at)));
    }

    private static void tick(class_3218 world) {
        var session=SESSIONS.get(world);
        if (session==null || (session.pilot==null && session.manualOwner==null)) return;
        var ship=loaded(world,session);
        if (session.manualOwner!=null) {
            var owner=session.manualOwner;
            var control=ship==null?null:ship.getAttachment(EurekaShipControl.class);
            if (ship==null || !withinSite(ship) || owner.method_14239() || owner.method_51469()!=world
                || control==null || control.getAnchors()!=1
                || (control.getSeatedPlayer()!=null && control.getSeatedPlayer()!=owner)) {
                stop(world,session);
            } else if (session.awaitingPilot && control.getSeatedPlayer()==owner && owner.method_5765()) {
                session.awaitingPilot=false;
                var helm=LabShips.findHelm(world,ship);
                if (helm!=null) {
                    var anchor=helm.method_10081(session.anchorOffset);
                    if (id(world.method_8320(anchor)).equals(ANCHOR) && !world.method_49803(anchor)) {
                        control.setAnchorsActive(0);
                        ship.setStatic(false);
                    }
                }
            }
            return;
        }
        var owner=((com.dwinovo.numen.api.entity.NumenPlayer)session.pilot).resolveOwnerPlayer();
        if (ship==null || !withinSite(ship) || owner==null || owner.method_51469()!=world || !owner.method_7337() ||
            !session.pilot.method_7337() || !session.pilot.method_5765() ||
            world.method_8503().method_3780()>=session.until) stop(world,session);
    }

    private static void stop(class_3218 world,Session session) {
        var ship=loaded(world,session);
        if (ship!=null) {
            var control=ship.getAttachment(EurekaShipControl.class);
            if (control!=null && (session.manualOwner!=null || control.getSeatedPlayer()==null
                || control.getSeatedPlayer()==session.pilot)) {
                LabShips.anchor(ship,control,session.manualOwner==null?session.pilot:session.manualOwner);
            }
        }
        session.pilot=null;
        session.manualOwner=null;
        session.awaitingPilot=false;
    }

    private static String sample(LoadedServerShip ship,Session session) {
        var transform=ship.getTransform();
        var p=transform.getPositionInWorld();
        var q=transform.getShipToWorldRotation();
        var v=ship.getVelocity();
        var control=ship.getAttachment(EurekaShipControl.class);
        return JSON.toJson(Map.of("id",Long.toString(ship.getId()),"position",List.of(p.x(),p.y(),p.z()),
            "rotation",List.of(q.x(),q.y(),q.z(),q.w()),"velocity",List.of(v.x(),v.y(),v.z()),
            "static",ship.isStatic(),"mounted",session.pilot!=null && session.pilot.method_5765(),
            "manual",session.manualOwner!=null,"cruising",control!=null && control.isCruising()));
    }
}
