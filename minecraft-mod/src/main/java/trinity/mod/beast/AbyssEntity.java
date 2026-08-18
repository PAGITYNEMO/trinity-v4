package trinity.mod.beast;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import trinity.core.FastTerrain;
import trinity.mod.TideClock;
import trinity.mod.TrinityMod;
import trinity.mod.TrinityTerrain;

/**
 * ABYSS — the native of the water. It guards the REAL ore-vein chains the map
 * grew (FastTerrain.oreType lattice), rises with the spring tide and sinks
 * back into the deep valleys at low tide. When killed it drops the mineral of
 * the vein it was guarding — proof that the vein it sat on was real.
 *
 *  - guard : swim a lazy circuit around a vein block (oreType != 0)
 *  - rise  : when tide(t) runs high, surface toward the sea level
 *  - sink  : when the tide ebbs, dive back to the deep, low-surface01 valleys
 *  - hunt  : a player swimming nearby is treated as a vein-robber
 *  - tide  : breath makes the tendrils pulse; heartbeat makes it aggressive
 */
public final class AbyssEntity extends MobEntity {

    public enum Mode { GUARD, RISE, SINK, HUNT }

    private Mode mode = Mode.GUARD;
    private int modeTicks = 0;
    private int attackCooldown = 0;
    private long lastCryTick = -600;
    private long ticksAlive = 0;

    // ---- terrain coupling: the SAME evaluator the map was generated with ----
    private FastTerrain ft;
    private TrinityTerrain terrain;
    private long terrainSeed = Long.MIN_VALUE;

    private BlockPos guardVein = null;     // the vein block it protects
    private int guardOreType = 0;          // 1 coal / 2 iron / 3 gold / 4 diamond
    private PlayerEntity prey = null;
    private Vec3d wanderPoint = null;

    public AbyssEntity(EntityType<? extends AbyssEntity> type, World world) {
        super(type, world);
        this.setAiDisabled(false);
        this.setNoGravity(true);
        this.setAir(this.getMaxAir());
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new net.minecraft.entity.ai.pathing.SwimNavigation(this, world);
    }

    // ---------------- multi-scale tick ----------------

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        ticksAlive++;

        initTerrain();
        if (ft == null) return;

        this.setAir(this.getMaxAir()); // the deep is its home

        double t = TideClock.tServer();
        double breath = TideClock.breath(t);
        double hb = TideClock.heartbeat(t);

        if (this.age % 20 == 0) {
            sense();
        }
        if (this.age % 40 == 0) {
            decide(t, hb);
        }
        if (this.age % 300 == 0 && this.random.nextDouble() < 0.3) {
            gurgle();
        }
        if (attackCooldown > 0) attackCooldown--;
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
            guardVein = findVein(this.getBlockPos(), 10);
            if (guardVein != null) {
                guardOreType = ft.oreType(guardVein.getX(), guardVein.getY(), guardVein.getZ());
            }
            TrinityMod.LOGGER.info("[TRINITY v4.0] abyss coupled: seed={} vein={} ore={}",
                    terrainSeed, guardVein, guardOreType);
        }
    }

    /** Absolute ground height (water surface level) at (x, z). */
    private double surfaceY(int x, int z) {
        return TrinityTerrain.MIN_Y + ft.surface01(x, z) * TrinityTerrain.HEIGHT;
    }

    /** Find a real vein lattice block near p (the oreType lattice the map grew). */
    private BlockPos findVein(BlockPos p, int radius) {
        BlockPos best = null;
        int bestScore = -1;
        for (int dy = -6; dy <= 6; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos c = p.add(dx, dy, dz);
                    int ot = ft.oreType(c.getX(), c.getY(), c.getZ());
                    if (ot > bestScore) {
                        bestScore = ot;
                        best = c;
                    }
                }
            }
        }
        return best;
    }

    private void sense() {
        prey = null;
        double best = 14 * 14;
        for (PlayerEntity pl : this.getWorld().getPlayers()) {
            if (pl.isSubmergedInWater() || pl.isTouchingWater()) {
                double d = this.squaredDistanceTo(pl);
                if (d < best) {
                    best = d;
                    prey = pl;
                }
            }
        }
    }

    private void decide(double t, double hb) {
        double tide = Math.max(0, TideClock.tide(t));
        double sa = TideClock.seasonAmplitude(t);

        // a swimmer near the guarded vein is a robber
        if (prey != null && attackCooldown == 0 && this.random.nextDouble() < 0.5 + 0.5 * hb) {
            mode = Mode.HUNT;
            modeTicks = 0;
            return;
        }
        if (mode == Mode.HUNT) {
            if (prey == null || this.squaredDistanceTo(prey) > 26 * 26) {
                mode = Mode.GUARD;
                modeTicks = 0;
            }
            return;
        }
        // tide drives depth: high tide rises, ebb sinks
        if (tide > 0.4 && mode == Mode.SINK) {
            mode = Mode.RISE;
            modeTicks = 0;
        } else if (tide < 0.15 && mode != Mode.SINK && this.random.nextDouble() < 0.4) {
            mode = Mode.SINK;
            modeTicks = 0;
        } else if (mode != Mode.RISE && this.random.nextDouble() < 0.05) {
            mode = Mode.GUARD;
            modeTicks = 0;
        }
    }

    private void moveByMode(double t, double breath) {
        Vec3d vel = this.getVelocity().multiply(0.88, 0.88, 0.88);
        Vec3d target = Vec3d.ZERO;
        double speed = 0.5 * (0.6 + 0.4 * Math.max(0, TideClock.tide(t)));

        switch (mode) {
            case GUARD -> {
                if (guardVein != null) {
                    // lazy circuit around the vein, at the vein's depth
                    double a = 2 * Math.PI * 0.03 * t + (guardVein.getX() % 29) * 0.4;
                    double r = 3.5;
                    double tx = guardVein.getX() + 0.5 + Math.cos(a) * r;
                    double tz = guardVein.getZ() + 0.5 + Math.sin(a) * r;
                    Vec3d to = new Vec3d(tx, guardVein.getY() + 0.5, tz).subtract(this.getPos());
                    target = to.normalize().multiply(speed * 0.6);
                } else {
                    // no vein nearby: wander the deep valley
                    if (wanderPoint == null || this.squaredDistanceTo(wanderPoint) < 9) {
                        double a = this.random.nextDouble() * Math.PI * 2;
                        int tx = this.getBlockX() + (int) (Math.cos(a) * 12);
                        int tz = this.getBlockZ() + (int) (Math.sin(a) * 12);
                        wanderPoint = new Vec3d(tx + 0.5, surfaceY(tx, tz) - 4, tz + 0.5);
                    }
                    Vec3d to = wanderPoint.subtract(this.getPos());
                    if (to.length() > 0.1) target = to.normalize().multiply(speed * 0.5);
                }
            }
            case RISE -> {
                double targetY = TrinityTerrain.SEA_Y - 3 + 2 * Math.sin(breath * Math.PI);
                target = new Vec3d(0, (targetY - this.getY()) * 0.08, 0);
            }
            case SINK -> {
                double valley = surfaceY(this.getBlockX(), this.getBlockZ());
                double targetY = Math.min(valley, TrinityTerrain.SEA_Y - 20) - 3;
                target = new Vec3d(0, (targetY - this.getY()) * 0.06, 0);
            }
            case HUNT -> {
                if (prey != null) {
                    Vec3d to = prey.getPos().subtract(this.getPos());
                    double dist = to.length();
                    if (dist > 0.1) {
                        target = to.normalize().multiply(1.0);
                    }
                    if (dist < 2.0 && attackCooldown == 0) {
                        this.tryAttack((ServerWorld) this.getWorld(), prey);
                        attackCooldown = 50;
                    }
                }
            }
        }

        this.setVelocity(vel.add(target.multiply(0.4)));

        // face the motion
        Vec3d mv = this.getVelocity();
        if (mv.horizontalLengthSquared() > 0.001) {
            this.setYaw((float) (Math.toDegrees(Math.atan2(-mv.x, mv.z))));
            this.setPitch((float) Math.toDegrees(Math.atan2(-mv.y, mv.horizontalLength())));
        }
    }

    private void gurgle() {
        long now = ticksAlive;
        if (now - lastCryTick < 300) return;
        lastCryTick = now;
        String line;
        switch (mode) {
            case HUNT -> line = randomOf("咕噜噜噜——", "（深渊的凝视）", "矿脉是我的");
            case RISE -> line = randomOf("（随涨潮浮起）", "水面在召唤", "咕噜……");
            default -> line = randomOf("（水下的低鸣）", "咕噜", "深谷很安静");
        }
        if (this.getWorld() instanceof ServerWorld sw && sw.getServer() != null) {
            for (ServerPlayerEntity p : sw.getServer().getPlayerManager().getPlayerList()) {
                p.sendMessage(Text.literal("[深渊] " + line), false);
            }
            TrinityMod.LOGGER.info("{}", "[深渊] " + line);
        }
    }

    private String randomOf(String... options) {
        return options[this.random.nextInt(options.length)];
    }

    /** Drops the mineral of the vein it was guarding — the vein was real. */
    @Override
    public void onKilledBy(net.minecraft.entity.LivingEntity killer) {
        super.onKilledBy(killer);
        if (this.getWorld() instanceof ServerWorld sw) {
            ItemStack drop;
            switch (guardOreType) {
                case 4 -> drop = new ItemStack(Items.DIAMOND, 1);
                case 3 -> drop = new ItemStack(Items.GOLD_INGOT, 1 + sw.random.nextInt(2));
                case 2 -> drop = new ItemStack(Items.IRON_INGOT, 1 + sw.random.nextInt(2));
                case 1 -> drop = new ItemStack(Items.COAL, 1 + sw.random.nextInt(3));
                default -> {
                    return; // no vein: nothing to guard, nothing to drop
                }
            }
            sw.spawnEntity(new net.minecraft.entity.ItemEntity(sw,
                    this.getX(), this.getY(), this.getZ(), drop));
        }
    }

    // ---------------- attributes ----------------

    public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createAbyssAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 24.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.6)
                .add(EntityAttributes.ATTACK_DAMAGE, 5.0)
                .add(EntityAttributes.FOLLOW_RANGE, 32.0);
    }
}
