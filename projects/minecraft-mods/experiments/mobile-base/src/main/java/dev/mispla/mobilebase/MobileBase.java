package dev.mispla.mobilebase;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MobileBase implements ModInitializer {
    static final Logger LOGGER = LoggerFactory.getLogger("mobilebase");
    static final Block CORE = new BoatCoreBlock();
    static final Block LAND_CORE = new BoatCoreBlock();
    static final Block AIR_CORE = new BoatCoreBlock();

    @Override
    public void onInitialize() {
        var id = new ResourceLocation("mobilebase", "boat_core");
        Registry.register(BuiltInRegistries.BLOCK, id, CORE);
        var item = Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(CORE, new Item.Properties()));
        var landId = new ResourceLocation("mobilebase", "land_core");
        Registry.register(BuiltInRegistries.BLOCK, landId, LAND_CORE);
        var landItem = Registry.register(BuiltInRegistries.ITEM, landId, new BlockItem(LAND_CORE, new Item.Properties()));
        var airId = new ResourceLocation("mobilebase", "air_core");
        Registry.register(BuiltInRegistries.BLOCK, airId, AIR_CORE);
        var airItem = Registry.register(BuiltInRegistries.ITEM, airId, new BlockItem(AIR_CORE, new Item.Properties()));
        var marker = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation("mobilebase", "boat_marker"), new BoatMarkerItem());
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS)
            .register(entries -> {
                entries.accept(item);
                entries.accept(landItem);
                entries.accept(airItem);
                entries.accept(marker);
            });
        UseBlockCallback.EVENT.register(BoatMarkerItem::useBlock);
        BoatMarkerItem.registerEdits();
        if (LandPlan.available()) LandDrive.register();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BoatCoreBlock.clearPreviews());
    }

    public interface SurfaceBoat extends ManagedBoat {
        void mobilebase$adapt(int wetColumns, int floaters, double engineScale);

        boolean mobilebase$isAdaptedBoatAnchored();

        void mobilebase$fitLand(LandDrive.Setup setup);

        LandDrive.Setup mobilebase$land();

        boolean mobilebase$landCruiseArmed(boolean piloting);

        void mobilebase$fitAir(int balloons, double ratedMass);
    }

    public interface ManagedBoat {
        boolean mobilebase$isManaged();
        void mobilebase$markManaged();

        static ManagedBoat of(Object attachment) {
            return (ManagedBoat) attachment;
        }
    }
}
