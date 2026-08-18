package trinity.mod.beast;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import trinity.core.FastTerrain;
import trinity.mod.TideClock;
import trinity.mod.TrinityChunkGenerator;
import trinity.mod.TrinityMod;
import trinity.mod.TrinityTerrain;

/**
 * Natural spawning for the skyra and the abyss — only inside TRINITY worlds
 * (chunk generator check), only around players, and only on the world's own
 * terrain logic:
 *
 *  - skyra appear over the high, phi-rich ridges (surface01 high +
 *    regionWeight high), never too close to the player; the season amplitude
 *    gates how often they come out
 *  - abyss rise out of the deep valleys (surface01 < sea threshold = water),
 *    gated by the tide itself: when the tide runs low they stay hidden
 *  - population caps keep the world alive without flooding it
 */
public final class BeastSpawning {

    private BeastSpawning() {
    }

    public static void wire() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 40 != 0) return;
            double t = TideClock.tServer();
            double sa = TideClock.seasonAmplitude(t);
            double tide = Math.max(0, TideClock.tide(t));
            for (ServerWorld world : server.getWorlds()) {
                if (!(world.getChunkManager().getChunkGenerator() instanceof TrinityChunkGenerator)) {
                    continue; // TRINITY worlds only
                }
                if (world.getPlayers().isEmpty()) continue;
                long seed = world.getSeed();
                FastTerrain ft = new FastTerrain(seed, TrinityTerrain.HEIGHT);

                int skyraTotal = world.getEntitiesByType(TrinityMod.SKYRA_TYPE, e -> e.isAlive()).size();
                int abyssTotal = world.getEntitiesByType(TrinityMod.ABYSS_TYPE, e -> e.isAlive()).size();
                int capSky = world.getPlayers().size() * 4;
                int capAbyss = world.getPlayers().size() * 3;

                for (ServerPlayerEntity p : world.getPlayers()) {
                    if (skyraTotal < capSky && world.random.nextDouble() < 0.06 * sa) {
                        if (spawnSkyra(world, p, ft)) skyraTotal++;
                    }
                    if (abyssTotal < capAbyss && world.random.nextDouble() < 0.05 * tide) {
                        if (spawnAbyss(world, p, ft)) abyssTotal++;
                    }
                }
            }
        });
        TrinityMod.LOGGER.info("[TRINITY v4.0] beast spawning wired: skyra over ridges, abyss in the deep (TRINITY worlds only)");
    }

    private static boolean spawnSkyra(ServerWorld world, ServerPlayerEntity p, FastTerrain ft) {
        // pick a spot 40..72 blocks out, preferring high, phi-rich ground
        double best = -1;
        int bx = 0, bz = 0;
        for (int i = 0; i < 6; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            int d = 40 + world.random.nextInt(32);
            int tx = p.getBlockX() + (int) (Math.cos(a) * d);
            int tz = p.getBlockZ() + (int) (Math.sin(a) * d);
            double score = ft.surface01(tx, tz) * 0.5
                    + ft.ram().regionWeight(tx, tz, world.getSeed()) * 0.5
                    + world.random.nextDouble() * 0.2;
            if (score > best) {
                best = score;
                bx = tx;
                bz = tz;
            }
        }
        double surf = TrinityTerrain.MIN_Y + ft.surface01(bx, bz) * TrinityTerrain.HEIGHT;
        double y = surf + 20 + world.random.nextDouble() * 12;
        // sky above the point must be open
        if (world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, bx, bz) > y) {
            return false;
        }
        SkyraEntity e = new SkyraEntity(TrinityMod.SKYRA_TYPE, world);
        e.refreshPositionAndAngles(bx + 0.5, y, bz + 0.5, world.random.nextFloat() * 360, 0);
        world.spawnEntity(e);
        return true;
    }

    private static boolean spawnAbyss(ServerWorld world, ServerPlayerEntity p, FastTerrain ft) {
        // deep valleys (surface01 well below the sea threshold) = water
        double best = 1.0;
        int bx = 0, bz = 0;
        boolean found = false;
        for (int i = 0; i < 8; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            int d = 24 + world.random.nextInt(40);
            int tx = p.getBlockX() + (int) (Math.cos(a) * d);
            int tz = p.getBlockZ() + (int) (Math.sin(a) * d);
            double s01 = ft.surface01(tx, tz);
            if (s01 < best) {
                best = s01;
                bx = tx;
                bz = tz;
                found = true;
            }
        }
        if (!found || best > 0.22) return false; // needs real water
        double surf = TrinityTerrain.MIN_Y + best * TrinityTerrain.HEIGHT;
        double y = surf + 2 + world.random.nextDouble() * 4;
        AbyssEntity e = new AbyssEntity(TrinityMod.ABYSS_TYPE, world);
        e.refreshPositionAndAngles(bx + 0.5, y, bz + 0.5, world.random.nextFloat() * 360, 0);
        world.spawnEntity(e);
        return true;
    }
}
