package trinity.mod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import trinity.core.Rng;

/**
 * Seasonal weather policy. Every 1200 server ticks (1 minute) the pulse rolls
 * rain with a season-dependent probability (涨潮 is the rainy season, 冰封 is
 * dry), and thunder rolls higher during 涨潮. The cadence is a self-counted
 * tick counter (guaranteed 1:1 with the server tick loop); world time is only
 * used as the deterministic RNG input.
 */
public final class WeatherPulse {
    private WeatherPulse() {
    }

    private static final long SALT = 0x5EA7A53L; // "WEATHER"
    private static int sinceRoll = 0;

    public static void wire() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerWorld ow = server.getOverworld();
            sinceRoll++;
            if (sinceRoll % 600 == 0) {
                TrinityMod.LOGGER.info("[TRINITY v4.0] weather heartbeat: tick={} worldTime={}",
                        sinceRoll, ow.getTime());
            }
            if (sinceRoll < 1200) {
                return; // roll once per minute
            }
            sinceRoll = 0;

            long t = ow.getTime();
            double roll = Rng.hash01(t, SALT, 1);
            if (ow.isRaining()) {
                TrinityMod.LOGGER.info("[TRINITY v4.0] weather roll: raining already (worldTime {})", t);
                return;
            }
            double now = TideClock.tServer();
            double rainChance = switch (TideClock.seasonIndex(now)) {
                case 0 -> 0.35; // 新芽 — showers
                case 1 -> 0.70; // 涨潮 — the rainy season
                case 2 -> 0.50; // 落叶 — drizzle
                default -> 0.15; // 冰封 — dry
            };
            if (roll >= rainChance) {
                TrinityMod.LOGGER.info("[TRINITY v4.0] weather roll: r={} clear (season {}, worldTime {})",
                        String.format("%.3f", roll), TideClock.seasonName(now), t);
                return;
            }
            int rainTicks = 1200 + (int) (Rng.hash01(t, SALT, 2) * 4800);
            boolean thunder = Rng.hash01(t, SALT, 3) < (TideClock.seasonIndex(now) == 1 ? 0.40 : 0.15);
            ow.setWeather(0, rainTicks, true, thunder);
            TrinityMod.LOGGER.info("[TRINITY v4.0] weather: rain {}s thunder={} (season {})",
                    rainTicks / 20, thunder, TideClock.seasonName(now));
        });
        TrinityMod.LOGGER.info("[TRINITY v4.0] weather pulse armed (seasonal rain, roll per minute)");
    }
}
