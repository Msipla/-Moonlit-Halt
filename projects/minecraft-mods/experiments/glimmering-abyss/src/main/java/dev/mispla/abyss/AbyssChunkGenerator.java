package dev.mispla.abyss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Registered world generator: the study is generated as real Minecraft chunks. */
public final class AbyssChunkGenerator extends ChunkGenerator {
    public static final Codec<AbyssChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(AbyssChunkGenerator::getBiomeSource),
            Codec.LONG.fieldOf("study_seed").forGetter(g -> g.seed)
    ).apply(instance, AbyssChunkGenerator::new));

    private final long seed;
    private final Map<Long, BlockState> fixtures;
    private static final BlockState NS_RAIL = Blocks.IRON_BARS.getDefaultState()
            .with(Properties.NORTH, true).with(Properties.SOUTH, true);
    private static final BlockState EW_RAIL = Blocks.IRON_BARS.getDefaultState()
            .with(Properties.EAST, true).with(Properties.WEST, true);

    public AbyssChunkGenerator(BiomeSource biomes, long seed) {
        super(biomes);
        this.seed = seed;
        Map<Long, BlockState> lamps = new HashMap<>();
        for (Terrain.Fixture f : Terrain.fixtures(seed)) {
            lamps.put(BlockPos.asLong(f.x(), f.y(), f.z()), GlimmeringAbyss.GLIMMER.getDefaultState());
        }
        this.fixtures = Map.copyOf(lamps);
    }

    public long studySeed() { return seed; }
    @Override protected Codec<? extends ChunkGenerator> getCodec() { return CODEC; }
    @Override public int getMinimumY() { return Terrain.MIN_Y; }
    @Override public int getWorldHeight() { return Terrain.HEIGHT; }
    @Override public int getSeaLevel() { return Terrain.MIN_Y; }

    public BlockState stateAt(Terrain.Column column, int y) {
        BlockState fixture = fixtures.get(BlockPos.asLong(column.x(), y, column.z()));
        if (fixture != null) return fixture;
        return switch (column.materialAt(y)) {
            case Terrain.AIR -> Blocks.AIR.getDefaultState();
            case Terrain.BEDROCK -> Blocks.BEDROCK.getDefaultState();
            case Terrain.FLOOR -> Blocks.POLISHED_DEEPSLATE.getDefaultState();
            case Terrain.RAIL_NS -> column.z() == 62 || column.z() == 77
                    ? NS_RAIL.with(Properties.EAST, column.x() < 0).with(Properties.WEST, column.x() > 0)
                    : NS_RAIL;
            case Terrain.RAIL_EW -> EW_RAIL;
            default -> Blocks.DEEPSLATE.getDefaultState();
        };
    }

    @Override public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender,
            NoiseConfig noise, StructureAccessor structures, Chunk chunk) {
        return CompletableFuture.supplyAsync(() -> {
            BlockPos.Mutable pos = new BlockPos.Mutable();
            Heightmap ocean = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
            Heightmap surface = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
            int startX = chunk.getPos().getStartX(), startZ = chunk.getPos().getStartZ();
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                Terrain.Column column = Terrain.column(startX + dx, startZ + dz, seed);
                for (int y = Terrain.MIN_Y; y < Terrain.MIN_Y + Terrain.HEIGHT; y++) {
                    BlockState state = stateAt(column, y);
                    if (!state.isAir()) {
                        chunk.setBlockState(pos.set(column.x(), y, column.z()), state, false);
                        ocean.trackUpdate(dx, y, dz, state);
                        surface.trackUpdate(dx, y, dz, state);
                    }
                }
            }
            return chunk;
        }, executor);
    }

    @Override public int getHeight(int x, int z, Heightmap.Type type, HeightLimitView world, NoiseConfig noise) {
        return Terrain.MIN_Y + Terrain.HEIGHT;
    }

    @Override public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noise) {
        BlockState[] states = new BlockState[Terrain.HEIGHT];
        Terrain.Column column = Terrain.column(x, z, seed);
        for (int i = 0; i < states.length; i++) states[i] = stateAt(column, Terrain.MIN_Y + i);
        return new VerticalBlockSample(Terrain.MIN_Y, states);
    }

    @Override public void carve(ChunkRegion r, long s, NoiseConfig n, BiomeAccess b,
            StructureAccessor a, Chunk c, GenerationStep.Carver step) {}
    @Override public void buildSurface(ChunkRegion r, StructureAccessor a, NoiseConfig n, Chunk c) {}
    @Override public void populateEntities(ChunkRegion region) {}
    @Override public void generateFeatures(StructureWorldAccess world, Chunk chunk, StructureAccessor structures) {}
    @Override public void getDebugHudText(List<String> text, NoiseConfig noise, BlockPos pos) {
        text.add("Glimmering Abyss study seed: " + seed);
    }
}
