package trinity.mod.beast;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import trinity.mod.TideClock;
import trinity.mod.TrinityMod;

/**
 * AURORA — the lord of the sky, the boss-scale native that the skyra serve.
 * A multi-segmented light-dragon that circles the player, breathes seasonal
 * aurora beams, dives like a meteor, and calls skyra to its side. It draws
 * strength from the interference patterns: in phase 2 it regenerates over
 * high-R(t) ground, and in phase 3 it goes into a full aurora barrage.
 *
 * Phases (boss bar):
 *  1  : circle + breath beams + occasional dive
 *  2  : + summons 2 skyra, regenerates over pattern-rich ground (R(t))
 *  3  : berserk: faster rhythm, multi-direction aurora barrage, heavy dives
 */
public final class AuroraEntity extends TrinityBossEntity {

    private enum Mode { CIRCLE, BREATH, DIVE, SUMMON }

    private Mode mode = Mode.CIRCLE;
    private int modeUntil = 0;
    private Vec3d diveTarget = null;
    private double circleAngle = 0;
    private long lastCry = -200;

    public AuroraEntity(EntityType<? extends AuroraEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    @Override
    protected String bossName() {
        return "极光之龙";
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        if (modeUntil > 0 && this.age >= modeUntil) {
            mode = Mode.CIRCLE;
        }
    }

    @Override
    protected void bossTick() {
        if (this.age % 5 == 0) {
            // aurora trail
            particles(ParticleTypes.END_ROD, 0, 0, -3.5, 1, 0.05, 0.05, 0.05, 0.01);
        }
        moveByMode();
        if (mode == Mode.BREATH && this.age % 8 == 0) {
            breathDamage();
        }
        if (mode == Mode.DIVE && this.age % 4 == 0) {
            diveStrike();
        }
        if (this.age % attackInterval() == 0) {
            decide();
        }
        // phase 3: constant aurora barrage
        if (phase == 3 && this.age % 24 == 0) {
            barrage();
        }
        // phase 2: regenerate over pattern-rich ground (R(t) of the terrain)
        if (phase == 2 && this.age % 20 == 0 && target != null) {
            double r = groundClarity(this.getBlockX(), this.getBlockZ());
            if (r > 0.5 && this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0f);
                particles(ParticleTypes.END_ROD, 0, 1, 0, 3, 0.4, 0.4, 0.4, 0.02);
            }
        }
        if (this.age % 240 == 0) {
            roar();
        }
    }

    /** True R(t) of the terrain field below — the engine's own clarity measure. */
    private double groundClarity(int bx, int bz) {
        trinity.core.FastTerrain ft = new trinity.core.FastTerrain(
                ((ServerWorld) this.getWorld()).getSeed(), trinity.mod.TrinityTerrain.HEIGHT);
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

    private void decide() {
        if (target == null) return;
        double t = TideClock.tServer();
        double hb = TideClock.heartbeat(t);
        double r = this.random.nextDouble();
        switch (mode) {
            case DIVE, BREATH, SUMMON -> {
                return; // finish the current attack first
            }
            default -> {
                // fall through
            }
        }
        if (r < 0.35 || (phase == 3 && r < 0.55)) {
            mode = Mode.BREATH;
            modeUntil = this.age + 50 + this.random.nextInt(40);
            roar();
        } else if (r < 0.65) {
            mode = Mode.DIVE;
            modeUntil = this.age + 45;
            diveTarget = target.getPos().add(0, 1, 0);
            roar();
        } else if (phase >= 2 && r < 0.85) {
            mode = Mode.SUMMON;
            modeUntil = this.age + 30;
            summonSkyra();
        } else {
            mode = Mode.CIRCLE;
        }
        // heartbeat: pulses make the dragon eager
        if (hb > 0.5 && this.random.nextDouble() < 0.4) {
            mode = Mode.DIVE;
            modeUntil = this.age + 45;
            diveTarget = target.getPos().add(0, 1, 0);
        }
    }

    private void moveByMode() {
        Vec3d vel = this.getVelocity().multiply(0.9, 0.9, 0.9);
        Vec3d target = Vec3d.ZERO;
        switch (mode) {
            case CIRCLE -> {
                if (this.target != null) {
                    circleAngle += 0.035;
                    Vec3d c = this.target.getPos().add(
                            Math.cos(circleAngle) * 15,
                            14 + 4 * Math.sin(circleAngle * 0.5),
                            Math.sin(circleAngle) * 15);
                    Vec3d to = c.subtract(this.getPos());
                    target = to.normalize().multiply(0.65);
                    // tangent drift for a natural orbit
                    target = target.add(new Vec3d(-to.z, 0, to.x).normalize().multiply(0.35));
                }
            }
            case BREATH -> {
                // hold position, gentle sway
                target = new Vec3d(Math.sin(this.age * 0.02) * 0.3, 0, Math.cos(this.age * 0.02) * 0.3);
            }
            case DIVE -> {
                if (diveTarget != null) {
                    Vec3d to = diveTarget.subtract(this.getPos());
                    double dist = to.length();
                    if (dist > 0.1) {
                        target = to.normalize().multiply(phase == 3 ? 2.0 : 1.5);
                    }
                }
            }
            case SUMMON -> {
                target = new Vec3d(0, -0.6, 0); // sink slightly while calling
            }
        }
        this.setVelocity(vel.add(target.multiply(0.4)));
        Vec3d mv = this.getVelocity();
        if (mv.horizontalLengthSquared() > 0.001) {
            this.setYaw((float) (Math.toDegrees(Math.atan2(-mv.x, mv.z))));
            this.setPitch((float) Math.toDegrees(Math.atan2(-mv.y, mv.horizontalLength())));
        }
    }

    /** Aurora beam: particles from the mouth toward the target + damage the beam line. */
    private void breathDamage() {
        if (target == null) return;
        Vec3d mouth = this.getPos().add(
                new Vec3d(-Math.sin(Math.toRadians(this.getYaw())) * 3.5, 1.2,
                        Math.cos(Math.toRadians(this.getYaw())) * 3.5));
        Vec3d dir = target.getPos().add(0, 1, 0).subtract(mouth).normalize();
        for (int i = 0; i < 6; i++) {
            Vec3d p = mouth.add(dir.multiply(i * 2.0 + this.random.nextDouble() * 1.5));
            particles(ParticleTypes.DRAGON_BREATH, p.x - this.getX(), p.y - this.getY(), p.z - this.getZ(),
                    1, 0.08, 0.08, 0.08, 0.02);
        }
        // beam damage: players within 1.6 blocks of the beam line
        if (this.age % 10 == 0) {
            float dmg = phase == 3 ? 8 : 6;
            for (PlayerEntity p : this.getWorld().getPlayers()) {
                Vec3d to = p.getPos().add(0, 1, 0).subtract(mouth);
                double along = to.dotProduct(dir);
                if (along < 0 || along > 12) continue;
                Vec3d proj = mouth.add(dir.multiply(along));
                if (proj.squaredDistanceTo(p.getPos().add(0, 1, 0)) < 1.6 * 1.6) {
                    p.damage((ServerWorld) this.getWorld(), this.getWorld().getDamageSources().mobAttack(this), dmg);
                }
            }
        }
    }

    private void diveStrike() {
        if (target == null) return;
        if (this.squaredDistanceTo(target) < 3.5 * 3.5) {
            target.damage((ServerWorld) this.getWorld(), this.getWorld().getDamageSources().mobAttack(this),
                    phase == 3 ? 14 : 10);
            particles(ParticleTypes.EXPLOSION, 0, 0, 0, 6, 1.2, 1.2, 1.2, 0.2);
            particles(ParticleTypes.END_ROD, 0, 0, 0, 30, 2.0, 2.0, 2.0, 0.3);
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, 0.8f, 0L);
            mode = Mode.CIRCLE;
            modeUntil = 0;
            diveTarget = null;
        }
    }

    private void summonSkyra() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        for (int i = 0; i < 2; i++) {
            SkyraEntity s = new SkyraEntity(TrinityMod.SKYRA_TYPE, sw);
            double a = this.random.nextDouble() * Math.PI * 2;
            s.refreshPositionAndAngles(this.getX() + Math.cos(a) * 10, this.getY() - 12,
                    this.getZ() + Math.sin(a) * 10, 0, 0);
            sw.spawnEntity(s);
        }
        particles(ParticleTypes.DRAGON_BREATH, 0, -6, 0, 40, 4, 1, 4, 0.1);
        broadcast("天空的子民，听我号令！");
    }

    /** Phase 3: aurora barrage — beams in eight directions around the dragon. */
    private void barrage() {
        if (target == null) return;
        for (int i = 0; i < 8; i++) {
            double a = i / 8.0 * Math.PI * 2 + this.age * 0.01;
            Vec3d dir = new Vec3d(Math.cos(a), 0.15, Math.sin(a)).normalize();
            for (int j = 0; j < 5; j++) {
                Vec3d p = this.getPos().add(dir.multiply(j * 2.5));
                particles(ParticleTypes.DRAGON_BREATH, p.x - this.getX(), p.y - this.getY(), p.z - this.getZ(),
                        1, 0.1, 0.1, 0.1, 0.02);
            }
        }
        // splash damage near players
        float dmg = 5;
        for (PlayerEntity p : this.getWorld().getPlayers()) {
            if (this.squaredDistanceTo(p) < 24 * 24) {
                p.damage((ServerWorld) this.getWorld(), this.getWorld().getDamageSources().mobAttack(this), dmg);
            }
        }
    }

    private void roar() {
        long now = ticksAlive;
        if (now - lastCry < 120) return;
        lastCry = now;
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2.0f, 0.6f, 0L);
    }

    @Override
    public void onKilledBy(LivingEntity killer) {
        super.onKilledBy(killer);
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.createExplosion(this, this.getX(), this.getY(), this.getZ(),
                    3.0f, World.ExplosionSourceType.NONE);
            particles(ParticleTypes.END_ROD, 0, 0, 0, 200, 6, 6, 6, 0.4);
            particles(ParticleTypes.DRAGON_BREATH, 0, 0, 0, 120, 5, 5, 5, 0.2);
            // loot: the light-curtain's hoard + the Aurora Core
            for (int i = 0; i < 10 + sw.random.nextInt(7); i++) {
                sw.spawnEntity(new ItemEntity(sw, this.getX(), this.getY(), this.getZ(),
                        new ItemStack(TrinityMod.CRYSTAL.asItem(), 1)));
            }
            sw.spawnEntity(new ItemEntity(sw, this.getX(), this.getY(), this.getZ(),
                    new ItemStack(TrinityMod.AURORA_CORE, 1)));
            broadcast("极光消散了……天空重归寂静。你带走了极光之核。");
        }
    }

    // ---------------- attributes ----------------

    public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createAuroraAttributes() {
        return net.minecraft.entity.mob.MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 300.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.7)
                .add(EntityAttributes.ATTACK_DAMAGE, 10.0)
                .add(EntityAttributes.FOLLOW_RANGE, 96.0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0);
    }
}
