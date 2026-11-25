// Runtime intermediary mappings for the 1.20.1 Fabric research server.
package lab;

import com.dwinovo.numen.api.NumenPlugins;
import com.dwinovo.numen.api.permission.Action;
import com.dwinovo.numen.api.permission.PlacedBlocks;
import com.dwinovo.numen.api.sdk.Doc;
import com.dwinovo.numen.api.sdk.Fn;
import com.dwinovo.numen.api.sdk.Pending;
import com.dwinovo.numen.api.sdk.ServerCall;
import com.dwinovo.numen.script.ApiError;
import com.dwinovo.numen.script.ErrorKind;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import net.fabricmc.api.ModInitializer;
import net.minecraft.class_1937;
import net.minecraft.class_1263;
import net.minecraft.class_2248;
import net.minecraft.class_2259;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.class_2382;
import net.minecraft.class_2487;
import net.minecraft.class_2505;
import net.minecraft.class_2507;
import net.minecraft.class_2512;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_3499;
import net.minecraft.class_7923;

public final class EngineeringSite implements ModInitializer {
    private static int[] bounds;
    private static boolean vessels;
    static final Set<String> HULL = Set.of("minecraft:oak_planks", "minecraft:oak_fence",
        "vs_eureka:oak_ship_helm", "vs_eureka:anchor", "vs_eureka:balloon", "vs_eureka:white_balloon",
        "vs_eureka:floater");
    private static final int LIMIT=64;
    private static final Set<String> PALETTE=Set.of(
        "minecraft:air","minecraft:stone","minecraft:glass","minecraft:oak_planks","minecraft:barrel","minecraft:hopper",
        "create:creative_motor","create:shaft","create:gearbox","create:cogwheel","create:large_cogwheel",
        "create:millstone","create:encased_fan","create:mechanical_press","create:depot","create:chute","create:smart_chute");

    @Override
    public void onInitialize() {
        CreateReadings.registerLabProbe();
        String value=System.getProperty("engineering.site");
        if (value==null || value.isBlank()) return;
        bounds=java.util.Arrays.stream(value.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
        if (bounds.length!=6) throw new IllegalArgumentException("engineering.site requires six coordinates");
        for (int i=0;i<3;i++) {
            long size=(long)bounds[i+3]-bounds[i]+1;
            if(size<1 || size>16) throw new IllegalArgumentException("engineering.site dimensions must be 1..16");
        }
        vessels=Boolean.getBoolean("engineering.ships");
        if (vessels) {
            var mods=net.fabricmc.loader.api.FabricLoader.getInstance();
            boolean supported=mods.getModContainer("valkyrienskies")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString().startsWith("2.4.11")).orElse(false)
                && mods.getModContainer("vs_eureka")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString().startsWith("1.6.4")).orElse(false);
            if (!supported) throw new IllegalStateException("engineering.ships requires VS 2.4.11 and Eureka 1.6.4");
            try {
                Class.forName("lab.EngineeringShips").getMethod("install").invoke(null);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("engineering.ships requires a VS-enabled engineering JAR",error);
            }
        }
        CreateReadings.installSettling();
        NumenPlugins.register("engineering", api -> api.api("site",
            "Create power, logistics and authorized edits. Lua namespace engineering.site (no numen prefix); print or return results to receive a Lua receipt. " +
            "For a new functional build: survey feet height, plan critical clearances, compile shaft connections from your chosen motor and load positions if useful, " +
            "prove working devices and throughput, then finish accessible enclosure with native building_design/shape/build. " +
            "check(cells) and plan(...) are read-only. apply(cells) is the same authorized batch up to 64 cells; " +
            "power readings settle on server ticks. Inspect after rejected or changed geometry; do not repeat writes blindly. " +
            "Use one exact Ponder/recipe query only if needed.",Site.class));
    }

    public static final class Site {
        public record Cell(@Doc("Exact loaded block position within the configured creative site.") class_2338 pos,
                           @Doc("Registry ID with optional block state properties. No NBT.") String state,
                           @Doc("Exact expected old state, or omit for air.") Optional<String> expected,
                           @Doc("Required physical shaft faces. Omit axis/facing from state for automatic native orientation.") Optional<List<String>> faces,
                           @Doc("Signed creative motor setting, 1 to 256 in magnitude.") Optional<Integer> rpm,
                           @Doc("Approved item ID for a smart chute.") Optional<String> filter) {}
        public record Work(@Doc("1..64 cells in one batch. Use expected for an occupied cell.") List<Cell> cells) {}
        public record Box(@Doc("Inclusive minimum corner inside the site.") class_2338 from,
                          @Doc("Inclusive maximum corner; every cell receives the same state.") class_2338 to,
                          @Doc("Registry ID with optional block state properties. No NBT.") String state,
                          @Doc("Exact expected old state in every cell, or omit for air.") Optional<String> expected) {}
        public record Reject(@Doc("Cell that would be refused.") class_2338 pos,
                             @Doc("Actual state or the reason the cell is invalid.") String reason) {}
        public record Check(int cells, List<Reject> rejects, List<String> warnings) {}
        public record Port(String face, boolean items, boolean insert, boolean extract, boolean fluid) {}
        public record NoArgs() {}
        public record Points(@Doc("1..16 loaded coordinates in the site.") List<class_2338> positions) {}
        public record SiteInfo(class_2338 from,class_2338 to,List<String> blocks,List<String> hull,
                               List<String> test_items,int batch_limit,Column center_surface) {}
        public record Observation(class_2338 pos,Reading reading) {}

        @Fn("Get exact permissions, inclusive bounds, allowed blocks and center_surface.{floor_y,feet_y}. The supported floor can be one block below the editable site. Use survey for other chosen columns.")
        public static SiteInfo info(ServerCall call, NoArgs args) {
            var level=level(call);
            var blocks=new TreeSet<>(PALETTE);
            if (vessels) blocks.addAll(HULL);
            return new SiteInfo(new class_2338(bounds[0],bounds[1],bounds[2]),new class_2338(bounds[3],bounds[4],bounds[5]),
                List.copyOf(blocks),vessels?HULL.stream().sorted().toList():List.of(),
                CreateSettings.FEEDS.stream().sorted().toList(),LIMIT,
                column(level,bounds[0]+(bounds[3]-bounds[0])/2,bounds[2]+(bounds[5]-bounds[2])/2));
        }

        @Fn("Check up to sixteen selected positions in one live snapshot. Each result is {pos, reading}; access row.reading.state, rpm, shaft_faces, power_status and items.")
        public static List<Observation> inspectMany(ServerCall call, Points args) {
            var world=level(call);
            require(!args.positions().isEmpty() && args.positions().size()<=16,"expected 1..16 inspection positions");
            return args.positions().stream().distinct().map(p->{
                check(world,p);
                return new Observation(p,read(world,p));
            }).toList();
        }
        public record Point(class_2338 pos) {}
        public record Topic(String topic) {}

        @Fn("Read Create Ponder teaching by scene or keyword, or an installed Create recipe by type/name. Returns bounded JSON.")
        public static String guide(ServerCall call,Topic args) {
            return PonderGuide.query(args.topic());
        }
        public record Feed(class_2338 pos,String item,int count) {}
        public record Region(String name,class_2338 from,class_2338 to) {}
        public record Replay(String name,class_2338 at) {}
        public record ResolvedCell(class_2338 pos,String state) {}
        public record Applied(int cells,String backup,List<ResolvedCell> resolved,List<String> warnings,
                              int power_wait_ticks,List<CreateReadings.Power> power) {}
        public record Reading(String block,String properties,Optional<Double> rpm,Optional<Boolean> overstressed,
                              List<String> shaft_faces,Optional<String> network,Optional<class_2338> source,
                              Optional<Integer> motor_setting,Optional<String> filter,List<CreateSettings.Stock> items,
                              String state,Optional<String> power_status) {}
        private record Edit(class_2338 at,class_2680 before,class_2680 state,
                            Map<class_2769<?>,Comparable<?>> specified,boolean oriented,CreateSettings.Settings settings) {
            boolean matches(class_2680 actual) {
                if (actual==state) return true;
                return actual.method_26204()==state.method_26204() && specified.keySet().stream()
                    .allMatch(property->state.method_11656().get(property).equals(actual.method_11656().get(property)))
                    && (!oriented || CreateReadings.matchesOrientation(state,actual));
            }
        }

        @Fn("Place or repair up to 64 cells in one batch. To modify an occupied cell, use the exact state from inspect as expected. For shaft blocks specify faces to select a legal orientation. Returns changed count, backup and resolved states.")
        public static Pending<Applied> apply(ServerCall call,Work args) {
            var level=level(call);
            require(!args.cells().isEmpty() && args.cells().size()<=LIMIT,"expected 1..64 block edits");
            var occupied=new HashSet<class_2338>();
            for(var cell:args.cells()) occupied.add(cell.pos());
            var edits=args.cells().stream().map(c->prepare(level,inferAxis(c,occupied))).toList();
            return execute(call,edits);
        }

        private static Cell inferAxis(Cell cell,Set<class_2338> batch) {
            String state=cell.state();
            if(cell.faces().isPresent() || !state.equals("create:shaft")) return cell;
            if(state.contains("[")) return cell;
            int x=0,y=0,z=0;
            var p=cell.pos();
            if(batch.contains(new class_2338(p.method_10263()+1,p.method_10264(),p.method_10260()))
                || batch.contains(new class_2338(p.method_10263()-1,p.method_10264(),p.method_10260()))) x=1;
            if(batch.contains(new class_2338(p.method_10263(),p.method_10264()+1,p.method_10260()))
                || batch.contains(new class_2338(p.method_10263(),p.method_10264()-1,p.method_10260()))) y=1;
            if(batch.contains(new class_2338(p.method_10263(),p.method_10264(),p.method_10260()+1))
                || batch.contains(new class_2338(p.method_10263(),p.method_10264(),p.method_10260()-1))) z=1;
            if(x+y+z!=1) return cell;
            String axis=x==1?"x":y==1?"y":"z";
            return new Cell(cell.pos(),state+"[axis="+axis+"]",cell.expected(),cell.faces(),cell.rpm(),cell.filter());
        }

        @Fn("Dry-run the same authorized edit format as apply, including orientation, occupied inventory and duplicate cells. Returns compact rejects and warnings without changing the world.")
        public static Check check(ServerCall call,Work args) {
            var level=level(call);
            require(!args.cells().isEmpty() && args.cells().size()<=LIMIT,"expected 1..64 block edits");
            return review(level,args.cells()).check();
        }

        private record Review(Check check, Map<class_2338,class_2680> overlay) {}

        private static Review review(class_3218 level,List<Cell> cells) {
            var rejects=new ArrayList<Reject>();
            var overlay=new java.util.LinkedHashMap<class_2338,class_2680>();
            var valid=new ArrayList<Cell>();
            var seen=new HashSet<class_2338>();
            var batch=cells.stream().map(Cell::pos).collect(java.util.stream.Collectors.toSet());
            for(var cell:cells) {
                try {
                    require(seen.add(cell.pos()),"duplicate cell "+cell.pos());
                    var edit=prepare(level,inferAxis(cell,batch));
                    preflight(level,List.of(edit));
                    overlay.put(edit.at(),edit.state());
                    valid.add(new Cell(cell.pos(),CreateReadings.commandState(edit.state()),cell.expected(),
                        cell.faces(),cell.rpm(),cell.filter()));
                } catch(RuntimeException error) {
                    rejects.add(new Reject(cell.pos(),String.valueOf(error.getMessage())));
                }
            }
            return new Review(new Check(cells.size(),rejects,layoutWarnings(level,valid)),overlay);
        }

        public record Column(int x,int z,Optional<Integer> floor_y,Optional<Integer> feet_y) {}
        @Fn("Get physical floor and player feet Y at 1..16 selected columns (input x,y,z; use site.from.y for y). Use returned feet_y for a ground-level entrance. Null means no solid support in the loaded column.")
        public static List<Column> survey(ServerCall call,Points args) {
            var level=level(call);
            require(!args.positions().isEmpty() && args.positions().size()<=16,"expected 1..16 survey columns");
            return args.positions().stream().map(p->{
                check(level,p);
                return column(level,p.method_10263(),p.method_10260());
            }).toList();
        }

        private static Column column(class_3218 level,int x,int z) {
            if(level.method_37118(new class_2338(x,bounds[1],z))) {
                for(int y=bounds[4]-1;y>=bounds[1];y--){
                    var feet=new class_2338(x,y,z);
                    var floor=feet.method_10074();
                    var head=feet.method_10084();
                    if(support(level,()->level.method_8320(floor),floor) &&
                        level.method_8320(feet).method_26218(level,feet).method_1110() &&
                        level.method_8320(head).method_26218(level,head).method_1110())
                        return new Column(x,z,Optional.of(y-1),Optional.of(y));
                }
            }
            return new Column(x,z,Optional.empty(),Optional.empty());
        }

        public record Plan(@Doc("0..64 optional candidate block edits; same syntax as apply.")
                           Optional<List<Cell>> cells,
                           @Doc("Optional ordered path of feet cells, including exterior and interior; one horizontal step at a time, Y change at most 1.")
                           Optional<List<class_2338>> walk,
                           @Doc("Optional positions of planned or existing machines, to check necessary workspace and local ports.")
                           Optional<List<class_2338>> machines) {}
        public record PlanResult(Check changes,List<Reject> clearance) {}
        @Fn("Read-only planning: plan({cells=edits, walk=feetPath, machines=positions}). Omit any unused field. Reports blocked feet/head, missing support or press workspace, and adjacent shaft contacts on your exact proposed geometry. This is a static check; verify power and products after building.")
        public static PlanResult plan(ServerCall call,Plan args) {
            var level=level(call);
            var cells=args.cells().orElse(List.of());
            var walk=args.walk().orElse(List.of());
            var machines=args.machines().orElse(List.of());
            require(cells.size()<=LIMIT && walk.size()<=32 && machines.size()<=16,
                "plan supports up to 64 edits, 32 path steps and 16 machine positions");
            var review=review(level,cells);
            var issues=new ArrayList<Reject>();
            var blocks=review.overlay();
            class_2338 previous=null;
            for(var at:walk) {
                check(level,at);
                var head=at.method_10084();
                var below=at.method_10074();
                if(!free(level,blocks,at,true)) issues.add(new Reject(at,"feet blocked by "+describe(level,blocks,at)));
                if(!free(level,blocks,head,true)) issues.add(new Reject(head,"head blocked by "+describe(level,blocks,head)));
                if(!support(level,()->state(level,blocks,below),below)) issues.add(new Reject(below,"no floor support"));
                if(previous!=null){
                    var low=at.method_10264()>previous.method_10264()?previous:at;
                    int horizontal=Math.abs(at.method_10263()-previous.method_10263())+
                        Math.abs(at.method_10260()-previous.method_10260());
                    int vertical=Math.abs(at.method_10264()-previous.method_10264());
                    if(horizontal!=1 || vertical>1) issues.add(new Reject(at,"walk steps must be adjacent with at most one block rise"));
                    else if(vertical==1 && !free(level,blocks,low.method_10086(2),true))
                        issues.add(new Reject(low.method_10086(2),"head blocked on step"));
                }
                previous=at;
            }
            for(var pos:machines) {
                check(level,pos);
                var state=state(level,blocks,pos);
                String id=id(state);
                if(id.equals("create:mechanical_press")) {
                    if(!free(level,blocks,pos.method_10074(),false))
                        issues.add(new Reject(pos.method_10074(),"press needs one open working cell below the head"));
                    if(!inSiteCell(level,pos.method_10087(2)) ||
                        !id(state(level,blocks,pos.method_10087(2))).equals("create:depot"))
                        issues.add(new Reject(pos.method_10087(2),"for depot pressing place depot exactly two blocks under the press"));
                }
                var faces=CreateReadings.faces(level,pos,state);
                if(!faces.isEmpty() && faces.stream().noneMatch(face->matchingFace(level,blocks,pos,face))) {
                    if(!id.equals("create:millstone") || !hasSideCog(level,blocks,pos))
                        issues.add(new Reject(pos,"no matching adjacent shaft face in planned layout; device faces "+faces));
                }
            }
            return new PlanResult(review.check(),issues.stream().distinct().limit(24).toList());
        }

        private static class_2680 state(class_3218 level,Map<class_2338,class_2680> blocks,class_2338 at) {
            var proposed=blocks.get(at);
            return proposed!=null?proposed:level.method_8320(at);
        }

        private static String describe(class_3218 level,Map<class_2338,class_2680> blocks,class_2338 at) {
            return inSiteCell(level,at)?id(state(level,blocks,at)):"site edge";
        }

        private static boolean free(class_3218 level,Map<class_2338,class_2680> blocks,class_2338 at,boolean canOpenDoor) {
            if(!inSiteCell(level,at)) return false;
            var next=state(level,blocks,at);
            if(canOpenDoor && next.method_26204() instanceof net.minecraft.class_2323 && !id(next).equals("minecraft:iron_door")) return true;
            return next.method_26218(level,at).method_1110() && next.method_26227().method_15769();
        }

        private static boolean support(class_3218 level,java.util.function.Supplier<class_2680> state,class_2338 at) {
            return at.method_10264()>=bounds[1]-1 && level.method_37118(at) &&
                state.get().method_26206(level,at,class_2350.field_11036);
        }

        private static boolean matchingFace(class_3218 level,Map<class_2338,class_2680> blocks,class_2338 at,String face) {
            var direction=class_2350.method_10168(face);
            if(direction==null) return false;
            var near=at.method_10093(direction);
            return inSiteCell(level,near) && CreateReadings.faces(level,near,state(level,blocks,near))
                .contains(direction.method_10153().method_10151());
        }

        private static boolean hasSideCog(class_3218 level,Map<class_2338,class_2680> blocks,class_2338 at) {
            for(var side:List.of("north","south","east","west")){
                var near=at.method_10093(class_2350.method_10168(side));
                if(!inSiteCell(level,near)) continue;
                var gear=state(level,blocks,near);
                if(id(gear).equals("create:cogwheel") && CreateReadings.faces(level,near,gear).contains("up"))
                    return true;
            }
            return false;
        }

        public record DrivePath(@Doc("Face-adjacent positions starting at your motor and ending at your load.") List<class_2338> points,
                                @Doc("Terminal kinetic block ID or exact state.") String load,
                                @Doc("For horizontal millstone side drive, use true and approach the last cog vertically.") Optional<Boolean> side_mesh) {}
        public record Route(@Doc("One path per load, all starting at the same new creative motor.") List<DrivePath> paths,
                            @Doc("Motor RPM between -256 and 256, excluding zero.") int rpm) {}
        public record Routed(List<Cell> cells, Check check) {}

        @Fn("Compile model-chosen paths into motor, shafts, gearboxes and loads, without writing. route(paths,rpm), paths={ {points={motor,...,loadPos},load='create:mechanical_press'}, ... }. Use neighboring cells, share the motor's first step; millstone accepts shaft from below, or side_mesh=true with the last cog powered vertically. Inspect result.check then apply(result.cells) if appropriate.")
        public static Routed route(ServerCall call,Route args) {
            var level=level(call);
            require(!args.paths().isEmpty() && args.paths().size()<=8,"expected 1..8 paths");
            require(args.rpm()!=0 && Math.abs((long)args.rpm())<=256,"motor RPM magnitude must be 1..256");
            var starts=args.paths().get(0).points();
            require(!starts.isEmpty(),"missing motor");
            var motor=starts.get(0);
            var contacts=new java.util.LinkedHashMap<class_2338,Set<String>>();
            var loads=new java.util.LinkedHashMap<class_2338,String>();
            var meshes=new HashSet<class_2338>();
            for(var path:args.paths()) {
                var positions=path.points();
                require(positions.size()>=2 && positions.size()<=LIMIT && positions.get(0).equals(motor) &&
                    new HashSet<>(positions).size()==positions.size(),"paths must share the motor, have 2..64 points and no loop");
                var load=positions.get(positions.size()-1);
                var terminal=parse(path.load());
                require(terminal.method_26204() instanceof com.simibubi.create.content.kinetics.base.IRotate &&
                    loads.putIfAbsent(load,path.load())==null,"each endpoint must be a different kinetic load");
                boolean side=path.side_mesh().orElse(false);
                for(int i=0;i<positions.size()-1;i++){
                    var from=positions.get(i);
                    var to=positions.get(i+1);
                    check(level,from);
                    check(level,to);
                    var direction=java.util.Arrays.stream(class_2350.values())
                        .filter(dir->from.method_10093(dir).equals(to)).findFirst()
                        .orElseThrow(()->bad("face-neighbor required between "+from+" and "+to));
                    var fromFaces=contacts.computeIfAbsent(from,p->new java.util.LinkedHashSet<>());
                    var toFaces=contacts.computeIfAbsent(to,p->new java.util.LinkedHashSet<>());
                    if(side && to.equals(load)) {
                        require(id(terminal).equals("create:millstone") && i>=1 && from.method_10264()==to.method_10264(),
                            "side_mesh requires a horizontal final millstone edge and a vertical cog input");
                        meshes.add(from);
                    } else {
                        fromFaces.add(direction.method_10151());
                        toFaces.add(direction.method_10153().method_10151());
                    }
                }
            }
            require(contacts.size()<=LIMIT && contacts.get(motor).size()==1 && !loads.containsKey(motor),
                "route needs one motor output and at most 64 cells");
            var cells=new ArrayList<Cell>();
            for(var entry:contacts.entrySet()){
                var pos=entry.getKey();
                var faces=List.copyOf(entry.getValue());
                if(loads.containsKey(pos))
                    require(faces.size()+(faces.isEmpty()?1:0)==1 && !meshes.contains(pos),
                        "load may terminate only one edge at "+pos);
                String state;
                if(pos.equals(motor)) state="create:creative_motor";
                else if(loads.containsKey(pos)) state=loads.get(pos);
                else if(meshes.contains(pos)) {
                    require(!faces.isEmpty() && faces.stream().allMatch(f->f.equals("up") || f.equals("down")),
                        "feed millstone cog vertically at "+pos);
                    state="create:cogwheel[axis=y]";
                } else {
                    long axes=faces.stream().map(f->class_2350.method_10168(f).method_10166()).distinct().count();
                    require(axes<=2,"a gearbox serves at most two axes at "+pos+"; separate the bends");
                    state=axes==1?"create:shaft":"create:gearbox";
                }
                var cell=new Cell(pos,state,Optional.empty(),faces.isEmpty()?Optional.empty():Optional.of(faces),
                    pos.equals(motor)?Optional.of(args.rpm()):Optional.empty(),Optional.empty());
                cells.add(cell);
            }
            return new Routed(cells,review(level,cells).check());
        }

        public record Load(@Doc("Position you chose for one load.") class_2338 pos,
                           @Doc("Kinetic block ID or exact block state.") String state,
                           @Doc("Use true for millstone side-drive; leaves space under the millstone for logistics.") Optional<Boolean> side_mesh) {}
        public record Connect(@Doc("Empty position you chose for the creative motor.") class_2338 motor,
                              @Doc("1..4 devices at selected positions.") List<Load> loads,
                              @Doc("Signed motor RPM (1..256 in magnitude).") int rpm,
                              @Doc("Optional cells reserved for walkways, depot clearance, or logistics.") Optional<List<class_2338>> avoid) {}

        @Fn("Read-only: connect(motor, {{pos=loadPos, state=blockId, side_mesh=true if needed}}, rpm, {avoid=reservedPositions}). Find compact right-angle shafts between your selected positions. Supports 1..4 devices, sharing one motor output. Reserve planned clearance/logistics cells in avoid. If check.rejects is empty, use apply(result.cells), then verify real power. If no route fits, shift a device or use route with explicit paths.")
        public static Routed connect(ServerCall call,Connect args) {
            var level=level(call);
            require(!args.loads().isEmpty() && args.loads().size()<=4,"choose 1..4 loads");
            require(args.rpm()!=0 && Math.abs((long)args.rpm())<=256,"motor RPM magnitude must be 1..256");
            var avoid=args.avoid().orElse(List.of());
            require(avoid.size()<=64,"at most 64 reserved positions");
            var reserved=new HashSet<>(avoid);
            check(level,args.motor());
            require(level.method_8320(args.motor()).method_26215() && !reserved.contains(args.motor()),
                "motor position is occupied or reserved");
            var loadPositions=new HashSet<class_2338>();
            for(var load:args.loads()){
                check(level,load.pos());
                require(level.method_8320(load.pos()).method_26215() && !reserved.contains(load.pos()) &&
                    !load.pos().equals(args.motor()) && loadPositions.add(load.pos()),"load position occupied, reserved or duplicated at "+load.pos());
                var state=parse(load.state());
                require(state.method_26204() instanceof com.simibubi.create.content.kinetics.base.IRotate,
                    "expected kinetic load at "+load.pos());
                if(load.side_mesh().orElse(false)) require(id(state).equals("create:millstone"),"side_mesh is for millstone only");
            }
            reserved.addAll(loadPositions);
            reserved.add(args.motor());
            var cache=new java.util.HashMap<class_2338,Boolean>();
            java.util.function.Predicate<class_2338> empty=at->!reserved.contains(at) &&
                cache.computeIfAbsent(at,p->inSiteCell(level,p) && level.method_8320(p).method_26215());
            for(var outlet:class_2350.values()){
                var first=args.motor().method_10093(outlet);
                if(!empty.test(first)) continue;
                var alternatives=new ArrayList<List<DrivePath>>();
                for(var load:args.loads()) alternatives.add(candidates(level,empty,args.motor(),first,load));
                if(alternatives.stream().anyMatch(List::isEmpty)) continue;
                // Try a few deterministic route combinations. Explicit route() remains available for complex geometry.
                int attempts=1;
                for(var paths:alternatives) attempts=Math.min(48,attempts*Math.min(paths.size(),5));
                for(int option=0;option<attempts;option++){
                    var paths=new ArrayList<DrivePath>();
                    int remaining=option;
                    for(var options:alternatives){
                        int n=Math.min(options.size(),5);
                        paths.add(options.get(remaining%n));
                        remaining/=n;
                    }
                    try {
                        var result=route(call,new Route(paths,args.rpm()));
                        if(result.check().rejects().isEmpty()) return result;
                    } catch(ApiError incompatible) {
                        // A three-axis junction or an opposed motor port needs a different candidate.
                    }
                }
            }
            return new Routed(List.of(),new Check(0,List.of(new Reject(args.motor(),
                "no protected right-angle route fits; move a motor/load, free a corridor, or supply explicit paths")),List.of()));
        }

        private static List<DrivePath> candidates(class_3218 level,java.util.function.Predicate<class_2338> empty,
                                                  class_2338 motor,class_2338 first,Load load) {
            var paths=new java.util.LinkedHashSet<List<class_2338>>();
            boolean mesh=load.side_mesh().orElse(false);
            for(var port:class_2350.values()){
                if(mesh && (port==class_2350.field_11036 || port==class_2350.field_11033)) continue;
                if(!mesh){
                    try { CreateReadings.orient(level,load.pos(),load.state(),List.of(port.method_10151())); }
                    catch(CommandSyntaxException | IllegalArgumentException invalid) { continue; }
                }
                var contact=load.pos().method_10093(port);
                if(!empty.test(contact)) continue;
                for(var vertical:mesh?List.of(class_2350.field_11033,class_2350.field_11036):List.of(port)){
                    var approach=mesh?contact.method_10093(vertical):contact;
                    if(!empty.test(approach)) continue;
                    for(var order:List.of("xyz","xzy","yxz","yzx","zxy","zyx")){
                        var path=new ArrayList<class_2338>();
                        path.add(motor);
                        path.add(first);
                        var next=first;
                        boolean blocked=false;
                        for(char axis:order.toCharArray()){
                            int distance=component(approach,axis)-component(next,axis);
                            int step=Integer.signum(distance);
                            for(int i=0;i<Math.abs(distance);i++){
                                next=next.method_10069(axis=='x'?step:0,axis=='y'?step:0,axis=='z'?step:0);
                                if(!empty.test(next)){ blocked=true; break; }
                                path.add(next);
                            }
                            if(blocked) break;
                        }
                        if(blocked || mesh && path.contains(contact)) continue;
                        if(mesh) path.add(contact);
                        path.add(load.pos());
                        paths.add(List.copyOf(path));
                    }
                }
            }
            return paths.stream().sorted(java.util.Comparator.comparingInt(List::size))
                .map(path->new DrivePath(path,load.state(),load.side_mesh())).toList();
        }

        private static int component(class_2338 pos,char axis) {
            return axis=='x'?pos.method_10263():axis=='y'?pos.method_10264():pos.method_10260();
        }

        private static List<String> layoutWarnings(class_3218 level,List<Cell> cells) {
            var planned=new java.util.HashMap<class_2338,String>();
            for(var cell:cells) planned.put(cell.pos(),cell.state());
            var warnings=new ArrayList<String>();
            for(var cell:cells) {
                var pos=cell.pos();
                var state=cell.state();
                if(state.startsWith("create:creative_motor")) {
                    String facing=state.contains("facing=")?state.replaceAll(".*facing=([a-z]+).*","$1"):"north";
                    var dir=class_2350.method_10168(facing);
                    if(dir==null) continue;
                    var target=pos.method_10093(dir);
                    var next=planned.getOrDefault(target,"");
                    boolean connected=next.startsWith("create:shaft")||next.contains("cogwheel")||next.startsWith("create:gearbox")
                        ||(planned.get(target)==null && level.method_8321(target) instanceof KineticBlockEntity);
                    if(!connected) warnings.add(pos+" motor faces "+facing+" but "+target+" is not planned as a shaft or cogwheel;"
                        +" put the transmission cell on the facing side or change facing");
                    continue;
                }
                if(state.startsWith("create:millstone")) {
                    var below=pos.method_10074();
                    var next=planned.get(below);
                    boolean kineticBelow=next!=null && (next.startsWith("create:shaft")||next.contains("cogwheel")||next.startsWith("create:gearbox")||next.startsWith("create:creative_motor"));
                    if(kineticBelow) warnings.add(pos+" millstone has "+next+" below, which blocks the downward output; drive it from a side cogwheel[axis=y] instead");
                    else if(next==null && level.method_8320(below).method_26215())
                        warnings.add(pos+" millstone bottom is free; put a chute or barrel directly below to collect output");
                    continue;
                }
                if(state.startsWith("create:chute") || state.startsWith("create:smart_chute")) {
                    var below=pos.method_10074();
                    var next=planned.get(below);
                    if(next!=null && !next.equals("minecraft:air") && !next.contains("chute")
                        && !next.equals("minecraft:barrel"))
                        warnings.add(pos+" chute outputs into "+next+" below; chutes only move items downward into air or a container");
                }
            }
            return List.copyOf(warnings);
        }

        @Fn("Fill an inclusive axis-aligned box with one block state in a single batch. Use it for floors, walls, pillars and shaft runs instead of writing every cell; volume is 1..64.")
        public static Pending<Applied> fill(ServerCall call,Box args) {
            var level=level(call);
            check(level,args.from());
            check(level,args.to());
            int x0=Math.min(args.from().method_10263(),args.to().method_10263());
            int y0=Math.min(args.from().method_10264(),args.to().method_10264());
            int z0=Math.min(args.from().method_10260(),args.to().method_10260());
            int x1=Math.max(args.from().method_10263(),args.to().method_10263());
            int y1=Math.max(args.from().method_10264(),args.to().method_10264());
            int z1=Math.max(args.from().method_10260(),args.to().method_10260());
            long volume=(long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1);
            require(volume>=1 && volume<=LIMIT,"box volume must be 1..64, got "+volume);
            var state=args.state();
            if(state.equals("create:shaft") && (x1==x0 || y1==y0 || z1==z0)) {
                int varying=(x1>x0?1:0)+(y1>y0?1:0)+(z1>z0?1:0);
                if(varying==1) state="create:shaft[axis="+(x1>x0?"x":y1>y0?"y":"z")+"]";
            }
            var cells=new ArrayList<Cell>((int)volume);
            for(int x=x0;x<=x1;x++) for(int y=y0;y<=y1;y++) for(int z=z0;z<=z1;z++)
                cells.add(new Cell(new class_2338(x,y,z),state,args.expected(),
                    Optional.empty(),Optional.empty(),Optional.empty()));
            return apply(call,new Work(cells));
        }

        @Fn("Report which faces of a block expose item or fluid transfer and whether they insert or extract. Use it to place chutes and funnels on real ports instead of guessing.")
        public static List<Port> ports(ServerCall call, Point args) {
            var level=level(call);
            check(level,args.pos());
            var state=level.method_8320(args.pos());
            var entity=level.method_8321(args.pos());
            var result=new ArrayList<Port>();
            for(var dir:class_2350.values()) {
                var items=ItemStorage.SIDED.find(level,args.pos(),state,entity,dir);
                var fluids=FluidStorage.SIDED.find(level,args.pos(),state,entity,dir);
                if(items==null && fluids==null) continue;
                result.add(new Port(dir.method_10151(),items!=null,
                    items!=null && items.supportsInsertion(),items!=null && items.supportsExtraction(),fluids!=null));
            }
            return result;
        }

        private static Edit prepare(class_3218 level,Cell c) {
            check(level,c.pos());
            var parsed=parseState(c.state());
            var state=parsed.comp_622();
            if(id(state).equals("create:shaft")
                && c.faces().isEmpty() && parsed.comp_623().isEmpty()) {
                var neighbours=new ArrayList<class_2350>();
                for(var dir:class_2350.values())
                    if(level.method_8321(c.pos().method_10093(dir)) instanceof KineticBlockEntity) neighbours.add(dir);
                if(neighbours.size()==1) {
                    try {
                        state=CreateReadings.orient(level,c.pos(),c.state(),
                            List.of(neighbours.get(0).method_10151()));
                    } catch (CommandSyntaxException | IllegalArgumentException ignored) {
                    }
                }
            }
            if(c.faces().isPresent()) {
                var requested=c.faces().get();
                require(requested.size()>=1 && requested.size()<=6,"expected one to six shaft directions");
                try {
                    state=CreateReadings.orient(level,c.pos(),c.state(),requested);
                } catch (CommandSyntaxException | IllegalArgumentException error) {
                    throw bad(error.getMessage());
                }
            }
            var settings=new CreateSettings.Settings(c.rpm().orElse(null),c.filter().orElse(null));
            require(settings.allowedFor(id(state)),"unsupported settings for "+id(state));
            return new Edit(c.pos(),parse(c.expected().orElse("minecraft:air")),state,parsed.comp_623(),c.faces().isPresent(),settings);
        }

        @Fn("Read the actual block and signed Create RPM at a loaded, authorized site cell.")
        public static Reading inspect(ServerCall call,Point args) {
            var level=level(call);
            check(level,args.pos());
            return read(level,args.pos());
        }

        private static Reading read(class_3218 level,class_2338 pos) {
            var state=level.method_8320(pos);
            var entity=level.method_8321(pos);
            var settings=CreateSettings.read(entity);
            return new Reading(id(state),class_2512.method_10686(state).method_10562("Properties").toString(),
                entity instanceof KineticBlockEntity kinetic ? Optional.of((double)kinetic.getSpeed()):Optional.empty(),
                entity instanceof KineticBlockEntity kinetic ? Optional.of(kinetic.isOverStressed()):Optional.empty(),
                CreateReadings.faces(level,pos,state),
                entity instanceof KineticBlockEntity kinetic ? Optional.ofNullable(kinetic.network).map(Object::toString):Optional.empty(),
                entity instanceof KineticBlockEntity kinetic ? Optional.ofNullable(kinetic.source):Optional.empty(),
                Optional.ofNullable(settings.rpm()),Optional.ofNullable(settings.filter()),CreateSettings.inventory(entity),
                CreateReadings.commandState(state),entity instanceof KineticBlockEntity
                    ?Optional.of(CreateReadings.powerStatus(level,pos)):Optional.empty());
        }

        @Fn("Insert 1..3 approved test items into an empty barrel. Returns the count verified in the insertion tick. Vanilla/Create can move it before later inspection; inspect the downstream stock before feeding again.")
        public static Pending<Integer> feed(ServerCall call,Feed args) {
            var level=level(call);
            check(level,args.pos());
            require(CreateSettings.FEEDS.contains(args.item()) && args.count()>=1 && args.count()<=3,"unsupported test feed");
            var before=level.method_8320(args.pos());
            require(id(before).equals("minecraft:barrel"),"test feed requires a barrel at "+args.pos());
            var original=level.method_8321(args.pos());
            require(original instanceof class_1263,"barrel inventory unavailable");
            return call.authorize(Action.useBlock(args.pos(),before)).then(ignored->{
                var current=level(call);
                check(current,args.pos());
                require(current==level && current.method_8320(args.pos())==before &&
                    current.method_8321(args.pos())==original,"target barrel changed before feed");
                var barrel=(class_1263)original;
                require(barrel.method_5442(),"input barrel is occupied; no items inserted");
                var insert=CreateSettings.stack(args.item(),args.count());
                barrel.method_5447(0,insert);
                var actual=barrel.method_5438(0);
                if (actual.method_7909()!=insert.method_7909() || actual.method_7947()!=args.count()) {
                    barrel.method_5447(0,net.minecraft.class_1799.field_8037);
                    throw bad("test feed could not be verified; input slot restored");
                }
                barrel.method_5431();
                return actual.method_7947();
            });
        }

        @Fn("Save a vanilla structure NBT with approved device settings. Inventories and entities are removed.")
        public static String export(ServerCall call,Region args) {
            var level=level(call);
            var at=corner(args.from(),args.to(),true);
            var end=corner(args.from(),args.to(),false);
            check(level,at);
            check(level,end);
            var tag=capture(level,at,end);
            var cells=tag.method_10554("blocks",10);
            var palette=tag.method_10554("palette",10);
            for(int i=0;i<cells.size();i++) {
                var cell=cells.method_10602(i);
                var block=palette.method_10602(cell.method_10550("state")).method_10558("Name");
                CreateSettings.sanitize(cell,block);
            }
            store(level,args.name(),tag);
            return "engineering:"+args.name();
        }

        @Fn("Import a named structure blueprint into empty site cells. Only approved block states are applied.")
        public static Pending<Applied> importAt(ServerCall call,Replay args) {
            var level=level(call);
            check(level,args.at());
            var file=path(level,args.name());
            var edits=new ArrayList<Edit>();
            try (var stream=new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
                var tag=class_2507.method_10625(stream,new class_2505(262144));
                var palette=tag.method_10554("palette",10);
                var cells=tag.method_10554("blocks",10);
                var size=tag.method_10554("size",3);
                require(size.size()==3 && palette.size()<=LIMIT && cells.size()<=4096,"unsupported structure size");
                for(int axis=0;axis<3;axis++) require(size.method_10600(axis)>=1 && size.method_10600(axis)<=16,"invalid structure dimensions");
                for(int i=0;i<cells.size();i++){
                    var cell=cells.method_10602(i);
                    int index=cell.method_10550("state");
                    require(index>=0 && index<palette.size(),"invalid structure palette index");
                    var state=class_2512.method_10681(class_7923.field_41175.method_46771(),palette.method_10602(index));
                    var offset=cell.method_10554("pos",3);
                    require(offset.size()==3,"invalid structure position");
                    for(int axis=0;axis<3;axis++) require(offset.method_10600(axis)>=0 &&
                        offset.method_10600(axis)<size.method_10600(axis),"position outside structure dimensions");
                    if(state.method_26215()) continue;
                    require(supported(id(state)),"unsupported blueprint block "+id(state));
                    var at=new class_2338(args.at().method_10263()+offset.method_10600(0),
                        args.at().method_10264()+offset.method_10600(1),args.at().method_10260()+offset.method_10600(2));
                    var settings=CreateSettings.fromTag(id(state),cell.method_10562("nbt"));
                    require(settings.allowedFor(id(state)),"unsupported blueprint settings");
                    edits.add(new Edit(at,parse("minecraft:air"),state,state.method_11656(),false,settings));
                }
            } catch(IOException error) {
                throw bad("could not read this structure");
            }
            return execute(call,edits);
        }

        private static Pending<Applied> execute(ServerCall call,List<Edit> edits) {
            preflight(level(call),edits);
            Pending<Void> consent=Pending.of(null);
            for(var edit:edits){
                if(edit.before()==edit.state() && edit.settings().present()) {
                    consent=consent.thenWait(x->call.authorize(Action.useBlock(edit.at(),edit.before())));
                }
                if(edit.before()!=edit.state() && !edit.before().method_26215()) {
                    consent=consent.thenWait(x->call.authorize(Action.breakBlock(edit.at(),edit.before())));
                }
                if(edit.before()!=edit.state() && !edit.state().method_26215()) {
                    consent=consent.thenWait(x->call.authorize(Action.place(edit.at(),edit.before(),edit.state().method_26204().method_8389())));
                }
            }
            return consent.thenWait(x->{
                var level=level(call);
                preflight(level,edits);
                String backup=backup(level,edits.stream().map(Edit::at).toList());
                int written=0;
                var changed=new ArrayList<Edit>();
                try {
                    for(var edit:edits) {
                        if(edit.state()==edit.before()) continue;
                        changed.add(edit);
                        require(level.method_8652(edit.at(),edit.state(),3),"server rejected a state at "+edit.at());
                        written++;
                    }
                    for(var edit:edits) {
                        var actual=level.method_8320(edit.at());
                        require(edit.matches(actual),"neighbor update changed "+edit.at()+" to "+CreateReadings.commandState(actual));
                    }
                    for(var edit:edits) require(CreateSettings.apply(level.method_8321(edit.at()),edit.settings()),
                        "device rejected configuration at "+edit.at());
                    for(var edit:changed) {
                        if(level.method_8321(edit.at()) instanceof KineticBlockEntity kinetic) {
                            try {
                                kinetic.initialize();
                                kinetic.updateSpeed=true;
                            } catch(RuntimeException ignored) {
                            }
                        }
                    }
                } catch(RuntimeException error) {
                    for(int i=changed.size()-1;i>=0;i--){
                        var edit=changed.get(i);
                        level.method_8652(edit.at(),edit.before(),3);
                    }
                    throw bad("batch aborted: "+error.getMessage()+". Original states restored; full backup engineering:"+backup);
                }
                for(var edit:changed){
                    if(edit.state().method_26215()) PlacedBlocks.of(level).forget(edit.at());
                    else PlacedBlocks.placedBy(level,edit.at(),call.her());
                }
                var resolved=edits.stream().filter(edit->edit.oriented() || level.method_8320(edit.at())!=edit.state())
                    .map(edit->new ResolvedCell(edit.at(),CreateReadings.commandState(level.method_8320(edit.at())))).toList();
                var watched=new java.util.LinkedHashSet<class_2338>();
                for(var edit:edits) {
                    if(edit.before()==edit.state() && !edit.settings().present()) continue;
                    if(!(edit.before().method_26204() instanceof com.simibubi.create.content.kinetics.base.IRotate)
                        && !(edit.state().method_26204() instanceof com.simibubi.create.content.kinetics.base.IRotate)) continue;
                    if(level.method_8321(edit.at()) instanceof KineticBlockEntity) watched.add(edit.at());
                    for(var direction:class_2350.values()) {
                        var near=edit.at().method_10093(direction);
                        if(inSiteCell(level,near) && level.method_8321(near) instanceof KineticBlockEntity) watched.add(near);
                    }
                }
                var points=watched.stream().sorted(java.util.Comparator.comparing(
                    p->isTransmission(id(level.method_8320(p))))).limit(16).toList();
                int count=written;
                String saved="engineering:"+backup;
                if(points.isEmpty()) return Pending.of(new Applied(count,saved,resolved,List.of(),0,List.of()));
                return CreateReadings.afterTicks(level,()->{
                    require(level(call)==level,"owner or companion changed worlds after write; backup "+saved);
                    var power=points.stream().filter(p->inSiteCell(level,p)).map(p->CreateReadings.power(level,p)).toList();
                    var warnings=power.stream().filter(p->p.status().equals("disconnected") ||
                        p.status().equals("overstressed") || p.status().equals("stale_source"))
                        .limit(8).map(p->p.pos()+" "+p.status()+" after wait").toList();
                    return new Applied(count,saved,resolved,warnings,CreateReadings.SETTLE_TICKS,power);
                });

            });
        }

        private static void preflight(class_3218 level,List<Edit> edits) {
            require(!edits.isEmpty()&&edits.size()<=LIMIT,"expected 1..64 block edits");
            var seen=new HashSet<class_2338>();
            for(var edit:edits) {
                check(level,edit.at());
                require(seen.add(edit.at()),"duplicate cell "+edit.at());
                var actual=level.method_8320(edit.at());
                require(actual==edit.before(),"state conflict at "+edit.at()+": actual "+CreateReadings.commandState(actual)
                    +"; supply that state as expected if replacing it is intended");
                if (actual.method_26204()!=edit.state().method_26204()) {
                    require(CreateSettings.inventory(level.method_8321(edit.at())).isEmpty(),
                        "occupied inventory at "+edit.at()+"; keep the container or empty it before replacing the block");
                }
            }
        }

        static class_3218 level(ServerCall call) {
            var owner=call.her().resolveOwnerPlayer();
            var level=call.her().method_51469();
            String gate="creative companion and connected creative owner in the same dimension required: ";
            require(owner!=null && owner.method_51469()==level,gate+"owner must join this dimension before retrying");
            require(owner.method_7337(),gate+"owner must enter creative mode before retrying");
            require(call.her().method_7337(),gate+"companion must enter creative mode before retrying");
            require(level.method_27983().equals(class_1937.field_25179),"the engineering site is in the overworld");
            return level;
        }

        static boolean inSiteCell(class_3218 level,class_2338 p) {
            return p.method_10263()>=bounds[0] && p.method_10263()<=bounds[3]
                && p.method_10264()>=bounds[1] && p.method_10264()<=bounds[4]
                && p.method_10260()>=bounds[2] && p.method_10260()<=bounds[5] && level.method_37118(p);
        }

        private static boolean isTransmission(String id) {
            return id.equals("create:shaft") || id.equals("create:gearbox") ||
                id.equals("create:cogwheel") || id.equals("create:large_cogwheel");
        }

        static void check(class_3218 level,class_2338 p) {
            int[] point={p.method_10263(),p.method_10264(),p.method_10260()};
            for(int i=0;i<3;i++) require(point[i]>=bounds[i] && point[i]<=bounds[i+3],"outside the configured site");
            require(level.method_37118(p),"site chunk must already be loaded");
        }

        static boolean inSite(double x,double y,double z) {
            return x>=bounds[0] && x<=bounds[3]+1.0 && y>=bounds[1] && y<=bounds[4]+1.0 &&
                z>=bounds[2] && z<=bounds[5]+1.0;
        }

        static String backup(class_3218 level,Iterable<class_2338> cells) {
            var cursor=cells.iterator();
            var min=cursor.next();
            var max=min;
            while (cursor.hasNext()) {
                var cell=cursor.next();
                min=corner(min,cell,true);
                max=corner(max,cell,false);
            }
            String name="backup_"+UUID.randomUUID().toString().replace("-","");
            store(level,name,capture(level,min,max));
            return name;
        }

        private static boolean supported(String id) {
            return PALETTE.contains(id) || vessels && HULL.contains(id);
        }

        private static class_2680 parse(String spec) {
            return parseState(spec).comp_622();
        }

        private static class_2259.class_7211 parseState(String spec) {
            require(spec!=null && spec.length()<220 && !spec.contains("{"),"expected a block state without NBT");
            try {
                var parsed=class_2259.method_41957(class_7923.field_41175.method_46771(),spec,false);
                var id=id(parsed.comp_622());
                require(supported(id),"unsupported site block "+id+"; see engineering.site.info()");
                return parsed;
            } catch(CommandSyntaxException error) {
                throw bad("invalid block state "+spec+": "+error.getRawMessage().getString());
            }
        }

        static String id(class_2680 state) {
            return class_7923.field_41175.method_10221(state.method_26204()).toString();
        }

        private static class_2338 corner(class_2338 a,class_2338 b,boolean min) {
            return new class_2338(
                min?Math.min(a.method_10263(),b.method_10263()):Math.max(a.method_10263(),b.method_10263()),
                min?Math.min(a.method_10264(),b.method_10264()):Math.max(a.method_10264(),b.method_10264()),
                min?Math.min(a.method_10260(),b.method_10260()):Math.max(a.method_10260(),b.method_10260()));
        }

        private static class_2487 capture(class_3218 level,class_2338 from,class_2338 to) {
            for (var pos : List.of(new class_2338(from.method_10263(),from.method_10264(),to.method_10260()),
                                   new class_2338(to.method_10263(),from.method_10264(),from.method_10260()))) check(level,pos);
            var dimensions=new class_2382(to.method_10263()-from.method_10263()+1,
                to.method_10264()-from.method_10264()+1,to.method_10260()-from.method_10260()+1);
            var template=new class_3499();
            template.method_15174(level,from,dimensions,false,(class_2248)null);
            return template.method_15175(new class_2487());
        }

        private static java.nio.file.Path path(class_3218 level,String name) {
            require(name!=null && name.matches("[a-z0-9_]{1,48}"),"use a short lowercase structure name");
            return level.method_14183().method_44228(new class_2960("engineering",name),".nbt");
        }

        private static void store(class_3218 level,String name,class_2487 tag) {
            var file=path(level,name);
            try {
                Files.createDirectories(file.getParent());
                try(var output=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW)){
                    class_2507.method_10634(tag,output);
                }
            }catch(IOException error){
                throw bad("structure write failed or name already exists");
            }
        }

        private static ApiError bad(String message){
            return new ApiError(ErrorKind.BAD_ARGUMENT,message,null);
        }
        static void require(boolean ok,String message) {
            if(!ok) throw bad(message);
        }
    }
}
