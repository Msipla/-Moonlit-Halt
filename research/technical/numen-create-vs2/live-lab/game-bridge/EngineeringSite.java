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
        NumenPlugins.register("engineering", api -> api.api("site",
            "For engineering tasks use Lua print(engineering.site.info()) once for inclusive bounds and allowed blocks. " +
            "No numen. prefix. Print results; a bare Lua call hides its return. Apply cells as " +
            "{{pos={x=...,y=...,z=...},state='namespace:block',faces={'east'}}}; faces are optional shaft faces. " +
            "Use one bounded batch; fill() writes a box, check() dry-runs, ports() lists real item faces, and apply() returns warnings for unconnected kinetics. Read Ponder only for missing facts. " +
            "A mode or permission denial needs an owner action before retrying.",Site.class));
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
        public record Check(int cells, List<Reject> rejects) {}
        public record Port(String face, boolean items, boolean insert, boolean extract, boolean fluid) {}
        public record NoArgs() {}
        public record Points(@Doc("1..16 loaded coordinates in the site.") List<class_2338> positions) {}
        public record SiteInfo(class_2338 from,class_2338 to,List<String> blocks,List<String> hull,
                               List<String> test_items,int batch_limit) {}
        public record Observation(class_2338 pos,Reading reading) {}

        @Fn("Get exact authorizations, inclusive coordinates and supported block IDs in one call. Start here instead of probing the site.")
        public static SiteInfo info(ServerCall call, NoArgs args) {
            level(call);
            var blocks=new TreeSet<>(PALETTE);
            if (vessels) blocks.addAll(HULL);
            return new SiteInfo(new class_2338(bounds[0],bounds[1],bounds[2]),new class_2338(bounds[3],bounds[4],bounds[5]),
                List.copyOf(blocks),vessels?HULL.stream().sorted().toList():List.of(),
                CreateSettings.FEEDS.stream().sorted().toList(),LIMIT);
        }

        @Fn("Check up to sixteen selected positions in one live snapshot. Returns exact block states, shaft faces, RPM and stock.")
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
        public record Applied(int cells,String backup,List<ResolvedCell> resolved,List<String> warnings) {}
        public record Reading(String block,String properties,Optional<Double> rpm,Optional<Boolean> overstressed,
                              List<String> shaft_faces,Optional<String> network,Optional<class_2338> source,
                              Optional<Integer> motor_setting,Optional<String> filter,List<CreateSettings.Stock> items,
                              String state) {}
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
            if(cell.faces().isPresent() || !(state.equals("create:shaft") || state.endsWith("cogwheel"))) return cell;
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

        @Fn("Dry-run a batch without writing. Returns every cell that would be rejected and why, so a layout can be corrected before apply. No block changes, no consent prompts.")
        public static Check check(ServerCall call,Work args) {
            var level=level(call);
            require(!args.cells().isEmpty() && args.cells().size()<=LIMIT,"expected 1..64 block edits");
            var rejects=new ArrayList<Reject>();
            for(var cell:args.cells()) {
                try {
                    var edit=prepare(level,cell);
                    var actual=level.method_8320(edit.at());
                    if(actual!=edit.before()) rejects.add(new Reject(edit.at(),
                        "expected "+CreateReadings.commandState(edit.before())+" but actual "+CreateReadings.commandState(actual)));
                } catch(RuntimeException error) {
                    rejects.add(new Reject(cell.pos(),String.valueOf(error.getMessage())));
                }
            }
            return new Check(args.cells().size(),rejects);
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
            if((id(state).equals("create:shaft") || id(state).endsWith("cogwheel"))
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
                CreateReadings.commandState(state));
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
            return consent.then(x->{
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
                var warnings=new ArrayList<String>();
                for(var edit:changed) {
                    var entity=level.method_8321(edit.at());
                    if(!(entity instanceof KineticBlockEntity kinetic)) continue;
                    var state=level.method_8320(edit.at());
                    if(kinetic.getSpeed()!=0) {
                        var faces=CreateReadings.faces(level,edit.at(),state);
                        if(!faces.isEmpty()) {
                            boolean linked=false;
                            for(var face:faces) {
                                var dir=class_2350.method_10168(face);
                                if(dir!=null && level.method_8321(edit.at().method_10093(dir)) instanceof KineticBlockEntity) { linked=true; break; }
                            }
                            if(!linked) warnings.add(edit.at()+" "+CreateReadings.commandState(state)
                                +" is powered (speed="+kinetic.getSpeed()+") with faces="+faces
                                +" but no kinetic block on any of those faces; place a shaft against one of them");
                        }
                        if(warnings.size()>=8) break;
                        continue;
                    }
                    var neighbours=new StringBuilder();
                    for(var dir:class_2350.values()) {
                        var side=edit.at().method_10093(dir);
                        var sideState=level.method_8320(side);
                        if(sideState.method_26215()) continue;
                        if(!neighbours.isEmpty()) neighbours.append(' ');
                        neighbours.append(dir.method_10148()).append('=');
                        neighbours.append(CreateReadings.commandState(sideState));
                    }
                    warnings.add(edit.at()+" "+CreateReadings.commandState(state)
                        +" speed=0 network="+(kinetic.network==null?"none":kinetic.network.toString())
                        +" neighbours=["+neighbours+"]; rotate or move it so its faces meet the output face of a powered block");
                    if(warnings.size()>=8) break;
                }
                return new Applied(written,"engineering:"+backup,resolved,List.copyOf(warnings));
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
