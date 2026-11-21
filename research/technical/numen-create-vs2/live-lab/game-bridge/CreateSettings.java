package lab;

import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.logistics.chute.SmartChuteBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.class_1799;
import net.minecraft.class_2487;
import net.minecraft.class_2586;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

final class CreateSettings {
    private static final Set<String> FILTERS=Set.of("minecraft:wheat","minecraft:wheat_seeds","create:wheat_flour",
        "create:dough","minecraft:cobblestone","minecraft:gravel","minecraft:flint");
    static final Set<String> FEEDS=Set.of("minecraft:wheat","minecraft:cobblestone");
    static final String MOTOR="create:creative_motor";
    static final String CHUTE="create:smart_chute";
    static final Settings EMPTY=new Settings(null,null);

    record Settings(Integer rpm,String filter) {
        boolean present() { return rpm!=null || filter!=null; }
        boolean allowedFor(String id) {
            return (rpm==null || MOTOR.equals(id) && rpm!=0 && Math.abs((long)rpm)<=256)
                && (filter==null || CHUTE.equals(id) && FILTERS.contains(filter));
        }
    }

    static class_1799 stack(String item,int count) {
        return new class_1799(class_7923.field_41178.method_10223(new class_2960(item)),count);
    }

    static Settings read(class_2586 entity) {
        if (entity instanceof CreativeMotorBlockEntity motor) return new Settings(motor.generatedSpeed.getValue(),null);
        if (entity instanceof SmartChuteBlockEntity chute) {
            var filter=chute.getBehaviour(FilteringBehaviour.TYPE).getFilter();
            if(!filter.method_7960()) return new Settings(null,class_7923.field_41178.method_10221(filter.method_7909()).toString());
        }
        return EMPTY;
    }

    static boolean apply(class_2586 entity,Settings settings) {
        if(settings.rpm()!=null) {
            if(!(entity instanceof CreativeMotorBlockEntity motor)) return false;
            motor.generatedSpeed.setValue(settings.rpm());
        }
        if(settings.filter()!=null) {
            if(!(entity instanceof SmartChuteBlockEntity chute)) return false;
            if(!chute.getBehaviour(FilteringBehaviour.TYPE).setFilter(stack(settings.filter(),1))) return false;
        }
        return true;
    }

    // Parse just the permitted scalar settings from a vanilla template, ignoring all other stored data.
    static Settings fromTag(String id,class_2487 tag) {
        if(MOTOR.equals(id) && tag.method_10573("ScrollValue",3)) return new Settings(tag.method_10550("ScrollValue"),null);
        if(CHUTE.equals(id) && tag.method_10573("Filter",10)) {
            var item=tag.method_10562("Filter").method_10558("id");
            if(!item.isEmpty() && !item.equals("minecraft:air")) return new Settings(null,item);
        }
        return EMPTY;
    }

    static void sanitize(class_2487 cell,String block) {
        var old=cell.method_10562("nbt");
        cell.method_10551("nbt");
        var configuration=fromTag(block,old);
        if(!configuration.present() || !configuration.allowedFor(block)) return;
        var tag=new class_2487();
        tag.method_10582("id",old.method_10558("id"));
        if(configuration.rpm()!=null) tag.method_10569("ScrollValue",configuration.rpm());
        if(configuration.filter()!=null) tag.method_10566("Filter",stack(configuration.filter(),1).method_7953(new class_2487()));
        cell.method_10566("nbt",tag);
    }

    public record Stock(String item,int count,String inventory) {}

    static List<Stock> inventory(class_2586 entity) {
        if(entity==null) return List.of();
        var nbt=entity.method_38243();
        var result=new ArrayList<Stock>();
        addItems(result,nbt,"container");
        for(var name:List.of("InputInventory","OutputInventory","OutputBuffer")) {
            addItems(result,nbt.method_10562(name),name);
        }
        for(var item:List.of(nbt.method_10562("HeldItem").method_10562("Item"),nbt.method_10562("Item"))) {
            addItem(result,item,"held");
        }
        return result;
    }

    private static void addItems(List<Stock> result,class_2487 tag,String label) {
        var items=tag.method_10554("Items",10);
        for(int i=0;i<items.size();i++) addItem(result,items.method_10602(i),label);
    }

    private static void addItem(List<Stock> result,class_2487 tag,String label) {
        var item=tag.method_10558("id");
        int count=tag.method_10550("Count");
        if(!item.isEmpty() && count>0) result.add(new Stock(item,count,label));
    }
}
