package trinity.mod.beast;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import trinity.core.FastTerrain;
import trinity.core.Rng;
import trinity.mod.TideClock;
import trinity.mod.TrinityMod;
import trinity.mod.TrinityTerrain;

/**
 * SKYRA — the native of the sky (the same way the Wither feels like the
 * original inhabitant of this world). Built from the SAME terrain evaluator
 * the map was generated with (same seed, same FastTerrain / q-pairs), so a
 * skyra patrols the ridges its own world grew.
 *
 * Behaviour (multi-scale, same philosophy as the four-pole bots):
 *  - patrol    : glide along high-surface01 ridges, 24..48 blocks above them;
 *                prefers phi-rich regions (regionWeight)
 *  - hover     : at breath peaks the skyra spreads its wings and hangs still
 *                over the interference patterns (R(t)-rich ground)
 *  - dive      : when a player enters its airspace it folds its wings and
 *                dives; heartbeat peaks make it dive more eagerly
 *  - climb     : pull up and return to cruise altitude
 *  - voice     : occasional sky-language cries, bound to the tide
 *  - loot      : carries a shard of the light-curtain (trinity_crystal)
 */
public final class SkyraEntity extends MobEntity {

    public enum Mode { PATROL, HOVER, DIVE, CLIMB }

    private Mode mode = Mode.PATROL;
    private int modeTicks = 0;
    private int attackCooldown = 0;
    private long lastCryTick = -600;
    private long ticksAlive = 0;

    // ---- terrain coupling: the SAME evaluator the map was generated with ----
    private FastTerrain ft;
    private TrinityTerrain terrain;
    private int q1, q2;
    private long terrainSeed = Long.MIN_VALUE;

    private PlayerEntity intruder = null;   // nearest player inside the airspace
    private Vec3d diveTarget = null;
    private double cruiseY = 90;

    public SkyraEntity(EntityType<? extends SkyraEntity> type, World world) {
        super(type, world);
        this.setAiDisabled(false);
        this.setNoGravity(true);
        this.setInvulnerable(false);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new net.minecraft.entity.ai.pathing.BirdNavigation(this, world);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier,
                                    net.minecraft.entity.damage.DamageSource damageSource) {
        return false; // skyra never falls
    }

    // ---------------- multi-scale tick ----------------

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        ticksAlive++;

        initTerrain();
        if (ft == null) return;

        double t = TideClock.tServer();
        double breath = TideClock.breath(t);
        double hb = TideClock.heartbeat(t);

        // 1 Hz sense: who is inside my airspace?
        if (this.age % 20 == 0) {
            sense(t);
        }
        // 0.5 Hz decide
        if (this.age % 40 == 0) {
            decide(t, hb);
        }
        // voice (0.2 Hz chance)
        if (this.age % 300 == 0 && this.random.nextDouble() < 0.35) {
            cry(breath);
        }
        if (attackCooldown > 0) attackCooldown--;

        // heartbeat coupling: dive cooldown recharges faster during pulses
        if (hb > 0.5 && attackCooldown > 0 && this.age % 5 == 0) {
            attackCooldown--;
        }

        moveByMode(t, breath);
        modeTicks++;
    }

    private void initTerrain() {
        if (this.getWorld() instanceof ServerWorld sw && terrainSeed != sw.getSeed()) {
            terrainSeed = sw.getSeed();
            ft = new FastTerrain(terrainSeed, TrinityTerrain.HEIGHT);
            terrain = new TrinityTerrain(terrainSeed);
            int[] tq = ft.templateQ();
            q1 = tq[0];
            q2 = tq[1];
            cruiseY = surfaceY(this.getBlockX(), this.getBlockZ()) + 28;
            TrinityMod.LOGGER.info("[TRINITY v4.0] skyra coupled: seed={} q1={} q2={} cruiseY={}",
                    terrainSeed, q1, q2, String.format("%.1f", cruiseY));
        }
    }

    /** Absolute ground height of the terrain field at (x, z). */
    private double surfaceY(int x, int z) {
        return TrinityTerrain.MIN_Y + ft.surface01(x, z) * TrinityTerrain.HEIGHT;
    }

    /** Interference clarity R(t) of the ground below — same measure as the bots. */
    private double groundClarity(int bx, int bz) {
        double mean = 0;
        int cells = 0;
        for (int dx = -8; dx <= 8; dx += 2) {
            for (int dz = -8; dz <= 8; dz += 2) {
                mean += ft.surface01(bx + dx, bz + dz);
                cells++;
            }
        }
        mean /= Math.max(1, cells);
        double var = 0;
        for (int dx = -8; dx <= 8; dx += 2) {
            for (int dz = -8; dz <= 8; dz += 2) {
                double d = ft.surface01(bx + dx, bz + dz) - mean;
                var += d * d;
            }
        }
        return Math.min(1, Math.sqrt(var / Math.max(1, cells)) / 0.25);
    }

    private void sense(double t) {
        intruder = null;
        double best = 20 * 20; // airspace radius
        for (PlayerEntity p : this.getWorld().getPlayers()) {
            double dx = p.getX() - this.getX();
            double dz = p.getZ() - this.getZ();
            double d2 = dx * dx + dz * dz;
            // airspace: horizontally close and below my altitude band
            if (d2 < best && p.getY() < this.getY() + 8 && p.getY() > this.getY() - 48) {
                best = d2;
                intruder = p;
            }
        }
    }

    private void decide(double t, double hb) {
        double sa = TideClock.seasonAmplitude(t);
        switch (mode) {
            case DIVE -> {
                // dive ends: pull up
                mode = Mode.CLIMB;
                modeTicks = 0;
                return;
            }
            case CLIMB -> {
                if (this.getY() >= cruiseY - 6) {
                    mode = Mode.PATROL;
                    modeTicks = 0;
                }
                return;
            }
            default -> {
                // fall through to airspace check
            }
        }
        if (intruder != null && attackCooldown == 0) {
            // dive: heartbeat pulses make skyra more eager
            double eagerness = 0.35 + 0.65 * (0.3 + 0.7 * hb) * sa;
            if (this.random.nextDouble() < eagerness) {
                diveTarget = intruder.getPos().add(0, 0.5, 0);
                mode = Mode.DIVE;
                modeTicks = 0;
                return;
            }
        }
        if (TideClock.breath(t) > 0.93) {
            mode = Mode.HOVER; // breath peak: spread wings, hang still
            modeTicks = 0;
        } else {
            mode = Mode.PATROL;
            modeTicks = 0;
        }
    }

    private void moveByMode(double t, double breath) {
        int bx = this.getBlockX(), bz = this.getBlockZ();
        double ground = surfaceY(bx, bz);
        double cruise = ground + 24 + 8 * Math.sin(2 * Math.PI * 0.05 * t + (q1 % 17) * 0.37);

        Vec3d vel = this.getVelocity().multiply(0.9, 0.9, 0.9);
        Vec3d target = Vec3d.ZERO;

        switch (mode) {
            case PATROL -> {
                // glide along the ridge: direction rotates slowly, prefers
                // phi-rich ground (same regionWeight the world used)
                double a = 2 * Math.PI * 0.02 * t + (q2 % 23) * 0.61;
                double speed = 0.45 * (0.7 + 0.3 * TideClock.seasonAmplitude(t));
                double richness = ft.ram().regionWeight(bx, bz, terrainSeed);
                double steer = (richness - 0.5) * 2.0; // -1..1 toward rich ground
                target = new Vec3d(Math.cos(a) * speed + steer * 0.1,
                        (cruise - this.getY()) * 0.05,
                        Math.sin(a) * speed);
            }
            case HOVER -> {
                // spread wings: drift almost nothing, gentle breath bob
                target = new Vec3d(0,
                        Math.sin(breath * Math.PI) * 0.12 - (this.getY() - cruise) * 0.03,
                        0);
            }
            case DIVE -> {
                if (diveTarget != null) {
                    Vec3d to = diveTarget.subtract(this.getPos());
                    double dist = to.length();
                    if (dist < 0.001) {
                        target = Vec3d.ZERO;
                    } else {
                        // fold wings: fast descent with prediction of the player
                        Vec3d predict = intruder != null
                                ? intruder.getVelocity().multiply(0.4, 0.4, 0.4)
                                : Vec3d.ZERO;
                        to = diveTarget.add(predict).subtract(this.getPos());
                        target = to.normalize().multiply(1.5);
                    }
                    // strike on contact
                    if (dist < 2.2 && attackCooldown == 0 && intruder != null) {
                        this.tryAttack((ServerWorld) this.getWorld(), intruder);
                        attackCooldown = 60;
                    }
                }
            }
            case CLIMB -> {
                double targetY = cruise + 14;
                target = new Vec3d(0, (targetY - this.getY()) * 0.12, 0);
            }
        }

        // apply: lerp current velocity toward target
        this.setVelocity(vel.add(target.multiply(0.35)));

        // face the motion
        Vec3d mv = this.getVelocity();
        if (mv.horizontalLengthSquared() > 0.001) {
            this.setYaw((float) (Math.toDegrees(Math.atan2(-mv.x, mv.z))));
            this.setPitch((float) Math.toDegrees(Math.atan2(-mv.y, mv.horizontalLength())));
        }
    }

    private void cry(double breath) {
        long now = ticksAlive;
        if (now - lastCryTick < 300) return;
        lastCryTick = now;
        String line;
        switch (mode) {
            case DIVE -> line = randomOf("嘶————", "（俯冲）天空是我的", "闯入者！");
            case HOVER -> line = randomOf("（悬停）这里的图纹在呼吸", "山脊之下……有风", "（滑翔）");
            default -> line = randomOf("嘶——", "（盘旋）我的领地", "风里有新的气流");
        }
        if (this.getWorld() instanceof ServerWorld sw && sw.getServer() != null) {
            for (ServerPlayerEntity p : sw.getServer().getPlayerManager().getPlayerList()) {
                p.sendMessage(Text.literal("[天穹] " + line), false);
            }
            TrinityMod.LOGGER.info("{}", "[天穹] " + line);
        }
    }

    private String randomOf(String... options) {
        return options[this.random.nextInt(options.length)];
    }

    @Override
    public void onKilledBy(net.minecraft.entity.LivingEntity killer) {
        super.onKilledBy(killer);
        if (this.getWorld() instanceof ServerWorld sw) {
            double R = groundClarity(this.getBlockX(), this.getBlockZ());
            if (sw.random.nextDouble() < 0.25 + 0.35 * R) {
                sw.spawnEntity(new net.minecraft.entity.ItemEntity(sw,
                        this.getX(), this.getY(), this.getZ(),
                        new ItemStack(TrinityMod.CRYSTAL.asItem(), 1)));
            }
        }
    }

    // ---------------- attributes ----------------

    public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createSkyraAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 30.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.45)
                .add(EntityAttributes.ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.FOLLOW_RANGE, 48.0);
    }
}
