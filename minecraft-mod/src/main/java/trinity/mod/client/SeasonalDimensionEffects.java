package trinity.mod.client;

import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import trinity.mod.TideClock;
import trinity.mod.TrinityMod;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Seasonal dimension effects: the sky and fog color are blended toward the
 * current season's tint (新芽 green / 涨潮 ocean blue / 落叶 orange / 冰封
 * pale blue-white), evaluated against the client clock.
 *
 * Vanilla resolves dimension effects through a private static identifier map
 * (DimensionEffects.BY_IDENTIFIER). We inject our effects into that map with
 * reflection at client init — no mixin, no ASM; if the field ever disappears
 * the injection degrades gracefully to vanilla colors.
 */
public final class SeasonalDimensionEffects extends DimensionEffects.Overworld {

    /** Season tints: 新芽 / 涨潮 / 落叶 / 冰封 (linear RGB). */
    private static final float[][] SEASONS = {
            {0.30f, 0.72f, 0.42f},
            {0.28f, 0.52f, 0.85f},
            {0.85f, 0.55f, 0.28f},
            {0.72f, 0.78f, 0.95f},
    };

    public SeasonalDimensionEffects() {
        super();
    }

    /** Blend between adjacent seasons by fractional phase. */
    public static float[] seasonColor(double t) {
        double phase = TideClock.seasonPhase(t);
        int i = (int) Math.floor(phase / (Math.PI / 2)) % 4;
        if (i < 0) i += 4;
        double frac = (phase % (Math.PI / 2)) / (Math.PI / 2);
        float[] a = SEASONS[i];
        float[] b = SEASONS[(i + 1) % 4];
        float[] out = new float[3];
        for (int k = 0; k < 3; k++) {
            out[k] = (float) (a[k] + (b[k] - a[k]) * frac);
        }
        return out;
    }

    @Override
    public int getSkyColor(float skyAngle) {
        int base = super.getSkyColor(skyAngle);
        float[] tint = seasonColor(TideClock.tClient());
        float r = ((base >> 16) & 0xFF) / 255f;
        float g = ((base >> 8) & 0xFF) / 255f;
        float b = (base & 0xFF) / 255f;
        final float k = 0.20f; // subtle temperature shift
        r += (tint[0] - r) * k;
        g += (tint[1] - g) * k;
        b += (tint[2] - b) * k;
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    @Override
    public Vec3d adjustFogColor(Vec3d fog, float distance) {
        Vec3d base = super.adjustFogColor(fog, distance);
        float[] tint = seasonColor(TideClock.tClient());
        final float k = 0.25f;
        return new Vec3d(
                base.x + (tint[0] - base.x) * k,
                base.y + (tint[1] - base.y) * k,
                base.z + (tint[2] - base.z) * k);
    }

    /**
     * Inject into the private identifier map. Production Minecraft runs
     * intermediary names, so the class is resolved through Fabric's mapping
     * resolver (works in dev AND production), and the static map field is
     * found by type (the only static Map field on DimensionEffects) so no
     * remapped field name is needed. Runs at client init, before any world
     * resolves its dimension effects.
     */
    public static void inject() {
        try {
            // Production runtime uses intermediary names; the resolver maps from
            // the OFFICIAL (Mojang) name — the only namespace present in both
            // dev and production — to whatever the runtime needs.
            String runtime = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getMappingResolver()
                    .mapClassName("official", "net.minecraft.client.renderer.DimensionSpecialEffects");
            Class<?> cls = Class.forName(runtime);
            for (Field f : cls.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Identifier, DimensionEffects> map = (Map<Identifier, DimensionEffects>) f.get(null);
                map.put(TrinityMod.id("seasonal"), new SeasonalDimensionEffects());
                TrinityMod.LOGGER.info("[TRINITY v4.0] seasonal sky/fog tint injected (runtime class {})", runtime);
                return;
            }
            TrinityMod.LOGGER.warn("[TRINITY v4.0] seasonal sky tint: no static map field on {}", runtime);
        } catch (Exception e) {
            TrinityMod.LOGGER.warn("[TRINITY v4.0] seasonal sky tint unavailable: {}", e.toString());
        }
    }
}
