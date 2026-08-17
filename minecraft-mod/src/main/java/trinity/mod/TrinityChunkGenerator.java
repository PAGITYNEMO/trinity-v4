package trinity.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.tick.OrderedTick;
import trinity.core.Rng;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The four-pole chunk generator: every block in the world comes from the
 * TRINITY v4.0 3D density body (bias skeleton + fBm3 external noise +
 * light-curtain Ramanujan interference patterns + carved caves + ore seams),
 * evaluated point-wise per chunk.
 */
public final class TrinityChunkGenerator extends ChunkGenerator {

    public static final MapCodec<TrinityChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
                    Codec.LONG.fieldOf("seed").forGetter(g -> g.seed)
            ).apply(instance, TrinityChunkGenerator::new));

    private final long seed;
    private TrinityTerrain terrain;
    private long terrainSeed = Long.MIN_VALUE;
    private static long loggedChunks;

    public TrinityChunkGenerator(BiomeSource biomeSource, long seed) {
        super(biomeSource);
        this.seed = seed;
    }

    /** World seed is derived from the NoiseConfig's random deriver (stable per world). */
    private TrinityTerrain terrain(NoiseConfig random) {
        long s = seed != 0 ? seed
                : random.getOrCreateRandomDeriver(TrinityMod.id("seed"))
                        .split(TrinityMod.id("terrain")).nextLong();
        if (terrainSeed != s) {
            terrain = new TrinityTerrain(s);
            terrainSeed = s;
        }
        return terrain;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public int getWorldHeight() {
        return TrinityTerrain.HEIGHT;
    }

    @Override
    public int getMinimumY() {
        return TrinityTerrain.MIN_Y;
    }

    @Override
    public int getSeaLevel() {
        return TrinityTerrain.SEA_Y;
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig random) {
        return terrain(random).surface(x, z);
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig random) {
        TrinityTerrain t = terrain(random);
        int surf = t.surface(x, z);
        boolean rich = t.chunkRich(x >> 4, z >> 4);
        BlockState[] states = new BlockState[TrinityTerrain.HEIGHT];
        for (int i = 0; i < TrinityTerrain.HEIGHT; i++) {
            states[i] = blockFor(t, x, TrinityTerrain.MIN_Y + i, z, rich, surf);
        }
        return new VerticalBlockSample(TrinityTerrain.MIN_Y, states);
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig random,
                                                  StructureAccessor structures, Chunk chunk) {
        TrinityTerrain t = terrain(random);
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        boolean rich = t.chunkRich(cx, cz);
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        List<BlockPos> crystals = new ArrayList<>();
        int crystalsPlaced = 0;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = startX + dx;
                int z = startZ + dz;
                int surf = t.surface(x, z);
                for (int y = TrinityTerrain.MIN_Y; y < TrinityTerrain.MIN_Y + TrinityTerrain.HEIGHT; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState st = blockFor(t, x, y, z, rich, surf);
                    if (st.getBlock() == TrinityMod.CRYSTAL) {
                        crystals.add(pos);
                        crystalsPlaced++;
                    }
                    chunk.setBlockState(pos, st, false);
                }
            }
        }
        // Crystals placed during generation need their heartbeat scheduled.
        if (!crystals.isEmpty()) {
            var scheduler = chunk.getBlockTickScheduler();
            for (BlockPos p : crystals) {
                scheduler.scheduleTick(OrderedTick.create(TrinityMod.CRYSTAL, p));
            }
        }
        if (loggedChunks++ < 3) {
            TrinityMod.LOGGER.info("[TRINITY] generating chunk ({}, {}), observer={}, seed={}, crystals={}",
                    cx, cz, rich, t.seed(), crystalsPlaced);
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig random, Chunk chunk) {
        // The density body already sculpted the surface.
    }

    @Override
    public void carve(ChunkRegion region, long seed, NoiseConfig random, BiomeAccess biomes,
                      StructureAccessor structures, Chunk chunk) {
        // Caves come from the density body itself.
    }

    @Override
    public void populateEntities(ChunkRegion region) {
    }

    @Override
    public void appendDebugHudText(List<String> text, NoiseConfig random, BlockPos pos) {
        text.add("TRINITY v4.0 four-pole noise generator");
        double t = TideClock.tClient();
        text.add(String.format("tide: b=%+.2f h=%.2f (t=%.0fs)",
                TideClock.breath(t), TideClock.heartbeat(t), t));
        text.add(String.format("season: %s amp=%.2f day=%.1f",
                TideClock.seasonName(t), TideClock.seasonAmplitude(t), TideClock.days(t)));
    }

    private BlockState blockFor(TrinityTerrain t, int x, int y, int z, boolean rich, int surf) {
        if (y > surf) {
            return y <= TrinityTerrain.SEA_Y ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
        }
        double d = t.density(x, y, z, rich, surf);
        if (d < TrinityTerrain.T_CAVE && y <= surf - 1) {
            return crystalHere(t, x, y, z, rich, surf)
                    ? TrinityMod.CRYSTAL.getDefaultState()
                    : Blocks.CAVE_AIR.getDefaultState();
        }
        if (d > TrinityTerrain.T_SOLID) {
            if (y == surf) {
                return y <= TrinityTerrain.SEA_Y ? Blocks.SAND.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
            }
            if (y == surf - 1) {
                return Blocks.DIRT.getDefaultState();
            }
            int ot = t.oreType(x, y, z);
            if (ot != 0) {
                return oreBlock(ot, y);
            }
            return y < TrinityTerrain.DEEPSLATE_Y ? Blocks.DEEPSLATE.getDefaultState() : Blocks.STONE.getDefaultState();
        }
        return y <= TrinityTerrain.SEA_Y ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
    }

    private BlockState oreBlock(int type, int y) {
        boolean deep = y < TrinityTerrain.DEEPSLATE_Y;
        switch (type) {
            case 1: return Blocks.COAL_ORE.getDefaultState();
            case 2: return Blocks.IRON_ORE.getDefaultState();
            case 3: return Blocks.GOLD_ORE.getDefaultState();
            default:
                if (deep) return Blocks.DEEPSLATE_DIAMOND_ORE.getDefaultState();
                return Blocks.DIAMOND_ORE.getDefaultState();
        }
    }

    /**
     * Natural crystal growth: cave cells become crystals with a depth-weighted
     * probability, only when sitting on solid ground (floor attachment) —
     * deeper caves grow richer crystals.
     */
    private boolean crystalHere(TrinityTerrain t, int x, int y, int z, boolean rich, int surf) {
        double y01 = (y - TrinityTerrain.MIN_Y) / (double) TrinityTerrain.HEIGHT;
        double depth = 1 - y01; // 1 at the bottom, 0 at the top
        double p = 0.010 * (0.4 + 1.6 * depth);
        if (Rng.hash3(x, y, z, t.seed() + 7777) >= p) {
            return false;
        }
        // floor attachment: solid rock below
        return t.density(x, y - 1, z, rich, surf) > TrinityTerrain.T_SOLID;
    }
}
