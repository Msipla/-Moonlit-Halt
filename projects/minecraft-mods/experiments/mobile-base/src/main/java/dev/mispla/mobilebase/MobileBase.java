package dev.mispla.mobilebase;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

    @Override
    public void onInitialize() {
        var id = new ResourceLocation("mobilebase", "boat_core");
        Registry.register(BuiltInRegistries.BLOCK, id, CORE);
        var item = Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(CORE, new Item.Properties()));
        var marker = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation("mobilebase", "boat_marker"), new BoatMarkerItem());
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS)
            .register(entries -> {
                entries.accept(item);
                entries.accept(marker);
            });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BoatCoreBlock.clearPreviews());
    }

    public interface SurfaceBoat extends ManagedBoat {
        void mobilebase$adapt(int wetColumns, int floaters, double engineScale);

        boolean mobilebase$isAdaptedBoatAnchored();
    }

    public interface ManagedBoat {
        boolean mobilebase$isManaged();
        void mobilebase$markManaged();

        static ManagedBoat of(Object attachment) {
            return (ManagedBoat) attachment;
        }
    }
}
