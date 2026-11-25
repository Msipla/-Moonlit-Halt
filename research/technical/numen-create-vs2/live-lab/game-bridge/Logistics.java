package lab;

import com.dwinovo.numen.api.sdk.Doc;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_3218;
import net.minecraft.class_7923;

public final class Logistics {
    private static final String HOPPER="minecraft:hopper";
    private static final String CHUTE="create:chute";
    private static final String SMART=CreateSettings.CHUTE;
    private static final Set<String> ENDPOINTS=Set.of("minecraft:barrel","create:millstone","create:depot");
    private static final int MAX_STEPS=8;
    private static final com.google.gson.Gson JSON=new com.google.gson.Gson();
    private static final Set<String> MACHINES=Set.of(HOPPER,CHUTE,SMART,"minecraft:barrel","create:millstone",
        "create:depot","create:mechanical_press","create:encased_fan","create:creative_motor");
    private static final class_2350 DOWN=class_2350.method_10168("down");
    private static final List<class_2350> MOVES=List.of("down","east","west","south","north").stream()
        .map(class_2350::method_10168).toList();

    public record Link(@Doc("Source storage: barrel, millstone or depot; reserve the cell below it.") class_2338 from,
                       @Doc("Destination storage: barrel, millstone or depot, below the source.") class_2338 to,
                       @Doc("Item registry ID for this link (existing item).") String item,
                       @Doc("True: put one item-filtered smart chute directly below the source. Omit for unsorted transfer.") Optional<Boolean> filter,
                       @Doc("Optional exact intermediate cells, from below source to before destination; omit to find a route.")
                       Optional<List<class_2338>> via) {}
    public record Request(@Doc("One to four model-selected links.") Optional<List<Link>> links,
                          @Doc("Optional planned cells, including empty-site endpoints, using apply syntax. Not placed by this tool.")
                          Optional<List<EngineeringSite.Site.Cell>> cells,
                          @Doc("Optional reserved cells such as shafts, water, airflow and walking space.")
                          Optional<List<class_2338>> avoid) {}
    public record Connection(class_2338 from,class_2338 to,String status,List<class_2338> via,String note) {}
    public record Result(List<EngineeringSite.Site.Cell> cells,List<Connection> links,EngineeringSite.Site.Check check) {}
    public record RecipeQuery(@Doc("Exact live Create recipe type, e.g. create:milling, create:pressing or create:splashing.") String recipe_type,
                              @Doc("Exact input item ID, including items matched by native ingredient tags.") String input) {}
    public record Product(String item,int count,double chance) {}
    public record Recipe(String id,int ticks,List<Product> results) {}
    public record AirQuery(@Doc("Existing fan position.") class_2338 fan,@Doc("Existing depot position.") class_2338 depot) {}
    public record Air(String status,String face,boolean pushing,double reach,boolean washing) {}
    public record Device(List<Integer> at,String state) {}

    private record Step(class_2338 pos,EngineeringSite.Site.Cell cell) {}
    private record Search(class_2338 pos,List<Step> path) {}

    static Result compile(class_3218 level,Request args,Map<class_2338,class_2680> overlay,
                          Map<class_2338,String> pendingFilters,EngineeringSite.Site.Check check) {
        var avoid=new HashSet<>(args.avoid().orElse(List.of()));
        var endpoints=new HashSet<class_2338>();
        for(var link:args.links().orElseThrow()) {
            EngineeringSite.Site.check(level,link.from());
            EngineeringSite.Site.check(level,link.to());
            endpoints.add(link.from());
            endpoints.add(link.to());
        }
        avoid.addAll(endpoints);
        var connections=new ArrayList<Connection>();
        var cells=new ArrayList<EngineeringSite.Site.Cell>();
        for(var link:args.links().orElseThrow()) {
            String issue=validate(level,overlay,link);
            if(issue!=null) {
                connections.add(new Connection(link.from(),link.to(),"blocked",List.of(),issue));
                continue;
            }
            List<Step> steps=link.via().isPresent()
                ? exact(level,overlay,pendingFilters,avoid,link,link.via().get())
                : find(level,overlay,pendingFilters,avoid,link);
            if(steps.isEmpty()) {
                var below=link.from().method_10074();
                String state=EngineeringSite.Site.inSiteCell(level,below)
                    ? CreateReadings.commandState(state(level,overlay,below)):"out_of_site";
                connections.add(new Connection(link.from(),link.to(),"blocked",List.of(),
                    "No compatible route of up to eight cells. Check outlet below source ("+below+" "+state+") and the facing, filter or reservations along via."));
                continue;
            }
            var path=steps.stream().map(Step::pos).toList();
            var edits=steps.stream().map(Step::cell).filter(java.util.Objects::nonNull).toList();
            cells.addAll(edits);
            avoid.addAll(path);
            connections.add(new Connection(link.from(),link.to(),edits.isEmpty()?"existing":"candidate",path,
                "Transfer geometry only. Check power, available slots, actual products and repeat feed."));
        }
        return new Result(List.copyOf(cells),List.copyOf(connections),check);
    }

    private static String validate(class_3218 level,Map<class_2338,class_2680> overlay,Link link) {
        if(item(link.item()).isEmpty()) return "Unknown or empty item ID";
        if(link.filter().orElse(false) && !new CreateSettings.Settings(null,link.item()).allowedFor(SMART)) return "Item is outside the smart chute filter allowlist";
        if(link.from().method_10264()<=link.to().method_10264()) return "Gravity intake needs the source above the destination";
        if(!ENDPOINTS.contains(EngineeringSite.Site.id(state(level,overlay,link.from()))) ||
           !ENDPOINTS.contains(EngineeringSite.Site.id(state(level,overlay,link.to())))) return "Endpoints must be barrel, millstone or depot; include planned endpoint cells";
        if(!overlay.containsKey(link.from())) {
            var storage=ItemStorage.SIDED.find(level,link.from(),DOWN);
            if(storage==null || !storage.supportsExtraction()) return "Existing source does not expose downward extraction";
        }
        return null;
    }

    private static List<Step> exact(class_3218 level,Map<class_2338,class_2680> overlay,Map<class_2338,String> filters,
                                    Set<class_2338> avoid,Link link,List<class_2338> path) {
        if(path.isEmpty() || path.size()>MAX_STEPS || !path.get(0).equals(link.from().method_10074()) ||
            new HashSet<>(path).size()!=path.size()) return List.of();
        var steps=new ArrayList<Step>();
        for(int i=0;i<path.size();i++) {
            var at=path.get(i);
            if(!available(level,avoid,at,link)) return List.of();
            var next=i+1==path.size()?link.to():path.get(i+1);
            var direction=MOVES.stream().filter(dir->at.method_10093(dir).equals(next)).findFirst();
            if(direction.isEmpty()) return List.of();
            var step=step(level,overlay,filters,link,at,direction.get(),i==0,next);
            if(step==null) return List.of();
            steps.add(step);
        }
        return steps;
    }

    private static List<Step> find(class_3218 level,Map<class_2338,class_2680> overlay,Map<class_2338,String> filters,
                                   Set<class_2338> avoid,Link link) {
        var start=link.from().method_10074();
        if(!available(level,avoid,start,link)) return List.of();
        var queue=new ArrayDeque<Search>();
        var visited=new HashSet<class_2338>();
        queue.add(new Search(start,List.of()));
        visited.add(start);
        while(!queue.isEmpty()) {
            var node=queue.remove();
            for(var direction:MOVES) {
                var next=node.pos().method_10093(direction);
                boolean end=next.equals(link.to());
                if(!end && (visited.contains(next) || node.path().size()+1>=MAX_STEPS || !available(level,avoid,next,link))) continue;
                var useful=step(level,overlay,filters,link,node.pos(),direction,node.path().isEmpty(),next);
                if(useful==null) continue;
                var steps=new ArrayList<>(node.path());
                steps.add(useful);
                if(end) return steps;
                visited.add(next);
                queue.add(new Search(next,steps));
            }
        }
        return List.of();
    }

    private static boolean available(class_3218 level,Set<class_2338> avoid,class_2338 at,Link link) {
        return !avoid.contains(at) && at.method_10264()>=link.to().method_10264() && EngineeringSite.Site.inSiteCell(level,at);
    }

    private static Step step(class_3218 level,Map<class_2338,class_2680> overlay,Map<class_2338,String> filters,
                             Link link,class_2338 at,class_2350 direction,boolean first,class_2338 next) {
        var block=state(level,overlay,at);
        var id=EngineeringSite.Site.id(block);
        String face=direction.method_10151();
        String filter=first && link.filter().orElse(false)?link.item():null;
        if(filter!=null && direction!=DOWN) return null;
        if(next.equals(link.to()) && !overlay.containsKey(next)) {
            var input=ItemStorage.SIDED.find(level,next,direction.method_10153());
            if(input==null || !input.supportsInsertion()) return null;
        }
        if(block.method_26215()) {
            if(overlay.containsKey(at) || level.method_49803(at)) return null;
            String wanted=filter!=null?SMART+"[powered=false]":HOPPER+"[facing="+face+"]";
            return new Step(at,new EngineeringSite.Site.Cell(at,wanted,Optional.empty(),Optional.empty(),Optional.empty(),Optional.ofNullable(filter)));
        }
        String command=CreateReadings.commandState(block);
        if(command.contains("enabled=false") || command.contains("powered=true")) return null;
        if(id.equals(SMART) && direction==DOWN) {
            String actual=filters.containsKey(at)?filters.get(at):CreateSettings.read(level.method_8321(at)).filter();
            return (actual==null?filter==null:actual.equals(link.item()))?new Step(at,null):null;
        }
        if(filter!=null) return null;
        if(id.equals(CHUTE) && direction==DOWN && command.contains("facing=down")) return new Step(at,null);
        if(id.equals(HOPPER) && command.contains("facing="+face)) return new Step(at,null);
        return null;
    }

    private static class_2680 state(class_3218 level,Map<class_2338,class_2680> overlay,class_2338 at) {
        var proposed=overlay.get(at);
        return proposed==null?level.method_8320(at):proposed;
    }

    private static Optional<class_1799> item(String id) {
        if(id.length()>128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return Optional.empty();
        var stack=CreateSettings.stack(id,1);
        return stack.method_7960()?Optional.empty():Optional.of(stack);
    }

    static String overview(class_3218 level,class_2338 min,class_2338 max) {
        var devices=new ArrayList<Device>();
        int total=0;
        for(int dx=0;dx<=max.method_10263()-min.method_10263();dx++)
            for(int dz=0;dz<=max.method_10260()-min.method_10260();dz++)
                for(int dy=0;dy<=max.method_10264()-min.method_10264();dy++) {
                    int x=min.method_10263()+dx, y=min.method_10264()+dy, z=min.method_10260()+dz;
                    var at=new class_2338(x,y,z);
                    if(!EngineeringSite.Site.inSiteCell(level,at)) continue;
                    var block=level.method_8320(at);
                    if(!MACHINES.contains(EngineeringSite.Site.id(block))) continue;
                    if(++total<=24) devices.add(new Device(List.of(x,y,z),CreateReadings.commandState(block)));
                }
        return JSON.toJson(Map.of("devices",devices,"total",total,"shown",devices.size()));
    }

    static String recipe(class_3218 level,RecipeQuery query) {
        var sample=item(query.input());
        if(sample.isEmpty() || !query.recipe_type().startsWith("create:")) return "[]";
        var found=level.method_8433().method_8126().stream()
            .filter(ProcessingRecipe.class::isInstance).map(recipe->(ProcessingRecipe<?>)recipe)
            .filter(recipe->class_7923.field_41188.method_10221(recipe.method_17716()).toString().equals(query.recipe_type()))
            .filter(recipe->recipe.method_8117().stream().anyMatch(ingredient->ingredient.test(sample.get())))
            .sorted(java.util.Comparator.comparing(recipe->recipe.method_8114().toString())).limit(6)
            .map(recipe->new Recipe(recipe.method_8114().toString(),recipe.getProcessingDuration(),
                recipe.getRollableResults().stream().limit(8).map(product->{
                    var stack=product.getStack();
                    return new Product(class_7923.field_41178.method_10221(stack.method_7909()).toString(),stack.method_7947(),product.getChance());
                }).toList())).toList();
        return JSON.toJson(found);
    }

    static Air airflow(class_3218 level,AirQuery query) {
        EngineeringSite.Site.check(level,query.fan());
        EngineeringSite.Site.check(level,query.depot());
        if(!(level.method_8321(query.fan()) instanceof EncasedFanBlockEntity fan) ||
           !EngineeringSite.Site.id(level.method_8320(query.depot())).equals("create:depot")) return new Air("missing_fan_or_depot","",false,0,false);
        var air=fan.getAirCurrent();
        String status=CreateReadings.powerStatus(level,query.fan());
        var face=fan.getAirflowOriginSide();
        if(!status.equals("running") || air.direction!=face || air.maxDistance<=0)
            return new Air(status.equals("running")?"no_settled_airflow":status,face.method_10151(),false,0,false);
        var position=query.fan();
        for(int distance=1;distance<=(air.pushing?(int)air.maxDistance+1:(int)air.maxDistance);distance++) {
            position=position.method_10093(face);
            if(!position.equals(query.depot()) && !(face!=DOWN && !face.method_10151().equals("up") && position.method_10074().equals(query.depot()))) continue;
            var type=FanProcessingType.getAt(level,query.depot());
            if(type==null) type=air.getTypeAt(distance-1);
            boolean washing=type instanceof com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes.SplashingType;
            return new Air(washing?"washing_reaches_depot":"air_reaches_depot",face.method_10151(),air.pushing,air.maxDistance,washing);
        }
        return new Air("depot_outside_current",face.method_10151(),air.pushing,air.maxDistance,false);
    }
}
