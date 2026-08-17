package trinity.mod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The tide clock: the protocol's breath/heartbeat oscillations (v4.0 section
 * 2.3) bound to the actual server tick, plus a seasonal phase. All time
 * functions are PURE (parameterized by t in seconds) so the same rhythm can be
 * evaluated against the server clock, the client clock, or a per-crystal
 * phase offset — the renderer, the crystal block, the weather and the F3 debug
 * screen all share one definition of the tide.
 *
 *   breath(t)    = sin(2*pi*0.15*t + 0.5*seasonPhase)   ~ 0.15 Hz, phase drifts
 *                                                         through the year
 *   heartbeat(t) = max(0, sin(2*pi*1.2*t))^8            ~ 1.2 Hz (period ~0.83 s)
 *   season       = 8 minecraft days per season (新芽/涨潮/落叶/冰封)
 *   tide(t)      = (0.4*breath + 0.6*heartbeat) * seasonAmplitude
 *
 * seasonAmplitude = 0.7 + 0.3*sin(seasonPhase): the tide runs strong at 涨潮,
 * calm at 冰封. The client clock is only present in a client context; on a
 * dedicated server the client clock stays frozen at 0.
 */
public final class TideClock {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrinityMod.MOD_ID + "-tide");
    private static long serverTicks = Long.MIN_VALUE;
    private static long clientTicks = Long.MIN_VALUE;
    private static int lastSeason = -1;

    private TideClock() {
    }

    // ---------------- clocks ----------------

    public static void serverTick() {
        serverTicks++;
        int s = seasonIndex(tServer());
        if (s != lastSeason) {
            lastSeason = s;
            if (s == 0) {
                LOGGER.info("[TRINITY v4.0] season -> {} (minecraft day {})",
                        seasonName(tServer()), String.format("%.1f", days(tServer())));
            }
        }
    }

    public static void clientTick() {
        clientTicks++;
    }

    public static double tServer() {
        return serverTicks == Long.MIN_VALUE ? 0 : serverTicks / 20.0;
    }

    public static double tClient() {
        return clientTicks == Long.MIN_VALUE ? 0 : clientTicks / 20.0;
    }

    public static boolean running() {
        return serverTicks != Long.MIN_VALUE;
    }

    // ---------------- pure tide functions (t in seconds) ----------------

    public static double days(double t) {
        return t / 480000.0; // 20 ticks/s * 24000 ticks/day
    }

    public static double seasonPhase(double t) {
        return 2 * Math.PI * (days(t) / 8);
    }

    public static int seasonIndex(double t) {
        int s = (int) Math.floor(seasonPhase(t) / (Math.PI / 2)) % 4;
        return s < 0 ? s + 4 : s;
    }

    public static String seasonName(double t) {
        return switch (seasonIndex(t)) {
            case 0 -> "新芽";
            case 1 -> "涨潮";
            case 2 -> "落叶";
            default -> "冰封";
        };
    }

    /** 0.7..1.0 — the tide runs strongest at 涨潮, calmest at 冰封. */
    public static double seasonAmplitude(double t) {
        return 0.7 + 0.3 * Math.sin(seasonPhase(t));
    }

    public static double breath(double t) {
        return Math.sin(2 * Math.PI * 0.15 * t + 0.5 * seasonPhase(t));
    }

    public static double heartbeat(double t) {
        double s = Math.sin(2 * Math.PI * 1.2 * t);
        return Math.pow(Math.max(0, s), 8);
    }

    /** (0.4*breath + 0.6*heartbeat) * seasonAmplitude. */
    public static double tide(double t) {
        return (0.4 * breath(t) + 0.6 * heartbeat(t)) * seasonAmplitude(t);
    }

    // ---------------- wiring ----------------

    public static void wire() {
        if (running()) return;
        ServerTickEvents.END_SERVER_TICK.register(server -> serverTick());
        lastSeason = seasonIndex(tServer()); // initialize before the first tick
        LOGGER.info("[TRINITY v4.0] tide clock bound to server tick (breath 0.15Hz / heartbeat 1.2Hz / seasons 8d)");
    }

    public static void wireClient() {
        if (clientTicks == Long.MIN_VALUE) {
            ClientTickEvents.END_CLIENT_TICK.register(client -> clientTick());
        }
    }
}
