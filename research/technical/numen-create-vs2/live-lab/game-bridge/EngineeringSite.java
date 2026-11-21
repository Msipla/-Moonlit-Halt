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
import net.minecraft.class_2382;
import net.minecraft.class_2487;
import net.minecraft.class_2505;
import net.minecraft.class_2507;
import net.minecraft.class_2512;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_3499;
import net.minecraft.class_7923;

public final class EngineeringSite implements ModInitializer {
    private static int[] bounds;
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
        NumenPlugins.register("engineering", api -> api.api("site",
            "Bounded creative construction and structure blueprints.",Site.class));
    }

    public static final class Site {
        public record Cell(@Doc("Exact loaded block position within the configured creative site.") class_2338 pos,
                           @Doc("Registry ID with optional block state properties. No NBT.") String state,
                           @Doc("Exact expected old state, or omit for air.") Optional<String> expected,
                           @Doc("Required physical shaft faces. Omit axis/facing from state for automatic native orientation.") Optional<List<String>> faces,
                           @Doc("Signed creative motor setting, 1 to 256 in magnitude.") Optional<Integer> rpm,
                           @Doc("Approved item ID for a smart chute.") Optional<String> filter) {}
        public record Work(@Doc("At most 64 independent state edits.") List<Cell> cells) {}
        public record Point(class_2338 pos) {}
        public record Feed(class_2338 pos,String item,int count) {}
        public record Region(String name,class_2338 from,class_2338 to) {}
        public record Replay(String name,class_2338 at) {}
        public record Applied(int cells,String backup) {}
        public record Reading(String block,String properties,Optional<Double> rpm,Optional<Boolean> overstressed,
                              List<String> shaft_faces,Optional<String> network,Optional<class_2338> source,
                              Optional<Integer> motor_setting,Optional<String> filter,List<CreateSettings.Stock> items) {}
        private record Edit(class_2338 at,class_2680 before,class_2680 state,CreateSettings.Settings settings) {}

        @Fn("Write a verified batch in the creative site. For replacement give the exact expected existing state.")
        public static Pending<Applied> apply(ServerCall call,Work args) {
            var level=level(call);
            require(!args.cells().isEmpty() && args.cells().size()<=LIMIT,"expected 1..64 block edits");
            var edits=args.cells().stream().map(c->{
                check(level,c.pos());
                var state=parse(c.state());
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
                return new Edit(c.pos(),parse(c.expected().orElse("minecraft:air")),state,settings);
            }).toList();
            return execute(call,edits);
        }

        @Fn("Read the actual block and signed Create RPM at a loaded, authorized site cell.")
        public static Reading inspect(ServerCall call,Point args) {
            var level=level(call);
            check(level,args.pos());
            var state=level.method_8320(args.pos());
            var entity=level.method_8321(args.pos());
            var settings=CreateSettings.read(entity);
            return new Reading(id(state),class_2512.method_10686(state).method_10562("Properties").toString(),
                entity instanceof KineticBlockEntity kinetic ? Optional.of((double)kinetic.getSpeed()):Optional.empty(),
                entity instanceof KineticBlockEntity kinetic ? Optional.of(kinetic.isOverStressed()):Optional.empty(),
                CreateReadings.faces(level,args.pos(),state),
                entity instanceof KineticBlockEntity kinetic ? Optional.ofNullable(kinetic.network).map(Object::toString):Optional.empty(),
                entity instanceof KineticBlockEntity kinetic ? Optional.ofNullable(kinetic.source):Optional.empty(),
                Optional.ofNullable(settings.rpm()),Optional.ofNullable(settings.filter()),CreateSettings.inventory(entity));
        }

        @Fn("Insert 1..3 approved test items into an empty in-site input barrel. Nothing is transferred afterward.")
        public static Pending<Integer> feed(ServerCall call,Feed args) {
            var level=level(call);
            check(level,args.pos());
            require(CreateSettings.FEEDS.contains(args.item()) && args.count()>=1 && args.count()<=3,"unsupported test feed");
            return call.authorize(Action.useBlock(args.pos(),level.method_8320(args.pos()))).then(ignored->{
                var current=level(call);
                check(current,args.pos());
                if(!id(current.method_8320(args.pos())).equals("minecraft:barrel")
                    || !(current.method_8321(args.pos()) instanceof class_1263 barrel) || !barrel.method_5442())
                    throw bad("expected an empty input barrel");
                barrel.method_5447(0,CreateSettings.stack(args.item(),args.count()));
                return args.count();
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
                    require(PALETTE.contains(id(state)),"unsupported blueprint block "+id(state));
                    var at=new class_2338(args.at().method_10263()+offset.method_10600(0),
                        args.at().method_10264()+offset.method_10600(1),args.at().method_10260()+offset.method_10600(2));
                    var settings=CreateSettings.fromTag(id(state),cell.method_10562("nbt"));
                    require(settings.allowedFor(id(state)),"unsupported blueprint settings");
                    edits.add(new Edit(at,parse("minecraft:air"),state,settings));
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
                var min=edits.get(0).at();
                var max=min;
                for(var edit:edits){
                    min=corner(min,edit.at(),true);
                    max=corner(max,edit.at(),false);
                }
                String backup="backup_"+UUID.randomUUID().toString().replace("-","");
                store(level,backup,capture(level,min,max));
                int written=0;
                var changed=new ArrayList<Edit>();
                try {
                    for(var edit:edits) {
                        if(edit.state()==edit.before()) continue;
                        changed.add(edit);
                        require(level.method_8652(edit.at(),edit.state(),3),"server rejected a state at "+edit.at());
                        written++;
                    }
                    for(var edit:edits) require(level.method_8320(edit.at())==edit.state(),"neighbor update changed "+edit.at());
                    for(var edit:edits) require(CreateSettings.apply(level.method_8321(edit.at()),edit.settings()),
                        "device rejected configuration at "+edit.at());
                } catch(RuntimeException error) {
                    for(int i=changed.size()-1;i>=0;i--){
                        var edit=changed.get(i);
                        level.method_8652(edit.at(),edit.before(),3);
                    }
                    throw bad("batch was aborted and original states restored. Full backup engineering:"+backup);
                }
                for(var edit:changed){
                    if(edit.state().method_26215()) PlacedBlocks.of(level).forget(edit.at());
                    else PlacedBlocks.placedBy(level,edit.at(),call.her());
                }
                return new Applied(written,"engineering:"+backup);
            });
        }

        private static void preflight(class_3218 level,List<Edit> edits) {
            require(!edits.isEmpty()&&edits.size()<=LIMIT,"expected 1..64 block edits");
            var seen=new HashSet<class_2338>();
            for(var edit:edits) {
                check(level,edit.at());
                require(seen.add(edit.at()),"duplicate cell "+edit.at());
                require(level.method_8320(edit.at())==edit.before(),"unexpected original state at "+edit.at());
            }
        }

        private static class_3218 level(ServerCall call) {
            var owner=call.her().resolveOwnerPlayer();
            var level=call.her().method_51469();
            require(call.her().method_7337() && owner!=null && owner.method_7337() && owner.method_51469()==level,
                "creative companion and connected creative owner in the same dimension required");
            require(level.method_27983().equals(class_1937.field_25179),"the engineering site is in the overworld");
            return level;
        }

        private static void check(class_3218 level,class_2338 p) {
            int[] point={p.method_10263(),p.method_10264(),p.method_10260()};
            for(int i=0;i<3;i++) require(point[i]>=bounds[i] && point[i]<=bounds[i+3],"outside the configured site");
            require(level.method_37118(p),"site chunk must already be loaded");
        }

        private static class_2680 parse(String spec) {
            require(spec!=null && spec.length()<220 && !spec.contains("{"),"expected a block state without NBT");
            try {
                var state=class_2259.method_41957(class_7923.field_41175.method_46771(),spec,false).comp_622();
                require(PALETTE.contains(id(state)),"unsupported site block "+id(state));
                return state;
            } catch(CommandSyntaxException error) {
                throw bad("invalid block state "+spec);
            }
        }

        private static String id(class_2680 state) {
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
        private static void require(boolean ok,String message) {
            if(!ok) throw bad(message);
        }
    }
}
