package trinity.mod.beast;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import trinity.mod.TideClock;
import trinity.mod.TrinityMod;
import trinity.mod.TrinityTerrain;

/**
 * ABYSS KING — the heart of the deep, the boss-scale native the abyss serve.
 * A colossal veil that drifts through the water, lashes out with sweeping
 * tendrils, charges like a current, and erupts veins from the sea floor. It
 * is bound to the tide itself: when the tide runs high it fights twice as
 * fast, and it calls abyss guards to its side in phase 2.
 *
 * Phases (boss bar):
 *  1  : drift + tendril sweeps + charges
 *  2  : + summons 2 abyss guards; tide makes it faster
 *  3  : berserk: wide eruptions, fast rhythm
 */
public final class AbyssKingEntity extends TrinityBossEntity {

    private enum Mode { DRIFT, SWEEP, CHARGE, ERUPT }

    private Mode mode = Mode.DRIFT;
    private int modeUntil = 0;
    private Vec3d chargeTarget = null;
    private long lastCry = -200;

    public AbyssKingEntity(EntityType<? extends AbyssKingEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.setAir(this.getMaxAir());
    }

    @Override
    protected String bossName() {
        return "深渊之心";
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        this.setAir(this.getMaxAir());
        if (modeUntil > 0 && this.age >= modeUntil) {
            mode = Mode.DRIFT;
        }
    }

    @Override
    protected int attackInterval() {
        // the tide itself sets the rhythm: high tide = furious
        double tide = Math.max(0, TideClock.tide(TideClock.tServer()));
        int base = switch (phase) {
            case 2 -> 50;
            case 3 -> 28;
            default -> 70;
        };
        return tide > 0.45 ? base / 2 : base;
    }

    @Override
    protected void bossTick() {
        if (this.age % 10 == 0) {
            particles(ParticleTypes.BUBBLE, 0, -1, 0, 3, 0.6, 0.4, 0.6, 0.05);
        }
        moveByMode();
        if (mode == Mode.SWEEP && this.age % 12 == 0) {
            sweepDamage();
        }
        if (mode == Mode.CHARGE && this.age % 5 == 0) {
            chargeStrike();
        }
        if (this.age % attackInterval() == 0) {
            decide();
        }
        if (phase == 3 && this.age % 30 == 0) {
            erupt(target == null ? this.getPos() : target.getPos());
        }
        if (this.age % 240 == 0) {
            moan();
        }
    }

    private void decide() {
        if (target == null) return;
        double r = this.random.nextDouble();
        switch (mode) {
            case SWEEP, CHARGE, ERUPT -> {
                return;
            }
            default -> {
                // fall through
            }
        }
        double dist = this.squaredDistanceTo(target);
        if (dist < 9 * 9 && r < 0.5) {
            mode = Mode.SWEEP;
            modeUntil = this.age + 36;
            moan();
        } else if (r < 0.75) {
            mode = Mode.CHARGE;
            modeUntil = this.age + 40;
            chargeTarget = target.getPos().add(0, 2, 0);
        } else if (phase >= 2 && r < 0.9) {
            mode = Mode.ERUPT;
            modeUntil = this.age + 30;
            erupt(target.getPos());
            summonAbyss();
        } else {
            mode = Mode.DRIFT;
        }
    }

    private void moveByMode() {
        Vec3d vel = this.getVelocity().multiply(0.88, 0.88, 0.88);
        Vec3d target = Vec3d.ZERO;
        switch (mode) {
            case DRIFT -> {
                if (this.target != null) {
                    Vec3d to = this.target.getPos().subtract(this.getPos());
                    // stay in the water: keep depth between 3 and 14 below sea
                    double depthY = TrinityTerrain.SEA_Y - 6 + 5 * Math.sin(this.age * 0.02);
                    target = new Vec3d(to.x * 0.02, (depthY - this.getY()) * 0.03, to.z * 0.02);
                    if (to.horizontalLength() > 12) {
                        Vec3d h = new Vec3d(to.x, 0, to.z).normalize();
                        target = target.add(h.multiply(0.4));
                    }
                }
            }
            case SWEEP -> {
                target = Vec3d.ZERO; // hold still, tendrils do the work
            }
            case CHARGE -> {
                if (chargeTarget != null) {
                    Vec3d to = chargeTarget.subtract(this.getPos());
                    double d = to.length();
                    if (d > 0.1) {
                        target = to.normalize().multiply(1.5);
                    }
                }
            }
            case ERUPT -> {
                target = new Vec3d(0, 0.8, 0); // rise while the floor erupts
            }
        }
        this.setVelocity(vel.add(target.multiply(0.4)));
        Vec3d mv = this.getVelocity();
        if (mv.horizontalLengthSquared() > 0.001) {
            this.setYaw((float) (Math.toDegrees(Math.atan2(-mv.x, mv.z))));
        }
    }

    /** Tendril sweep: everyone within 8 blocks takes a lash. */
    private void sweepDamage() {
        float dmg = phase == 3 ? 11 : 8;
        for (PlayerEntity p : this.getWorld().getPlayers()) {
            if (this.squaredDistanceTo(p) < 8 * 8) {
                p.damage((ServerWorld) this.getWorld(), this.getWorld().getDamageSources().mobAttack(this), dmg);
                particles(ParticleTypes.BUBBLE, p.getX() - this.getX(), p.getY() - this.getY(),
                        p.getZ() - this.getZ(), 8, 0.5, 0.5, 0.5, 0.1);
            }
        }
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.2f, 0.8f, 0L);
    }

    private void chargeStrike() {
        if (target != null && this.squaredDistanceTo(target) < 4.5 * 4.5) {
            target.damage((ServerWorld) this.getWorld(), this.getWorld().getDamageSources().mobAttack(this),
                    phase == 3 ? 13 : 10);
            particles(ParticleTypes.BUBBLE, 0, 0, 0, 20, 1.5, 1.5, 1.5, 0.2);
            particles(ParticleTypes.EXPLOSION, 0, 0, 0, 4, 0.8, 0.8, 0.8, 0.1);
            mode = Mode.DRIFT;
            modeUntil = 0;
            chargeTarget = null;
        }
    }

    /** Vein eruption: particles burst from the sea floor and damage nearby players. */
    private void erupt(Vec3d at) {
        ServerWorld sw = this.getWorld() instanceof ServerWorld s ? s : null;
        if (sw == null) return;
        float dmg = phase == 3 ? 9 : 6;
        int bx = (int) at.x, bz = (int) at.z;
        for (int i = 0; i < 16; i++) {
            double a = this.random.nextDouble() * Math.PI * 2;
            double rr = 2 + this.random.nextDouble() * 5;
            double px = bx + Math.cos(a) * rr;
            double pz = bz + Math.sin(a) * rr;
            double py = TrinityTerrain.MIN_Y + new trinity.core.FastTerrain(
                    sw.getSeed(), TrinityTerrain.HEIGHT).surface01((int) px, (int) pz) * TrinityTerrain.HEIGHT;
            sw.spawnParticles(ParticleTypes.FIREWORK, px, py + 1, pz, 4, 0.3, 0.8, 0.3, 0.15);
            sw.spawnParticles(ParticleTypes.BUBBLE, px, py + 2, pz, 6, 0.4, 0.5, 0.4, 0.1);
        }
        for (PlayerEntity p : this.getWorld().getPlayers()) {
            if (this.squaredDistanceTo(p) < 10 * 10) {
                p.damage((ServerWorld) this.getWorld(), this.getWorld().getDamageSources().mobAttack(this), dmg);
            }
        }
    }

    private void summonAbyss() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        for (int i = 0; i < 2; i++) {
            AbyssEntity a = new AbyssEntity(TrinityMod.ABYSS_TYPE, sw);
            double ang = this.random.nextDouble() * Math.PI * 2;
            a.refreshPositionAndAngles(this.getX() + Math.cos(ang) * 9, this.getY() - 4,
                    this.getZ() + Math.sin(ang) * 9, 0, 0);
            sw.spawnEntity(a);
        }
        particles(ParticleTypes.BUBBLE, 0, -4, 0, 50, 3, 1, 3, 0.15);
        broadcast("深谷的子民，醒来！");
    }

    private void moan() {
        long now = ticksAlive;
        if (now - lastCry < 120) return;
        lastCry = now;
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.5f, 0L);
    }

    @Override
    public void onKilledBy(LivingEntity killer) {
        super.onKilledBy(killer);
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.createExplosion(this, this.getX(), this.getY(), this.getZ(),
                    3.0f, World.ExplosionSourceType.NONE);
            particles(ParticleTypes.BUBBLE, 0, 0, 0, 200, 6, 6, 6, 0.3);
            particles(ParticleTypes.FIREWORK, 0, 0, 0, 80, 4, 4, 4, 0.2);
            // loot: the veins it guarded, and the Abyss Core
            for (int i = 0; i < 6 + sw.random.nextInt(4); i++) {
                sw.spawnEntity(new ItemEntity(sw, this.getX(), this.getY(), this.getZ(),
                        new ItemStack(Items.DIAMOND, 1)));
            }
            for (int i = 0; i < 8 + sw.random.nextInt(5); i++) {
                sw.spawnEntity(new ItemEntity(sw, this.getX(), this.getY(), this.getZ(),
                        new ItemStack(Items.GOLD_INGOT, 1)));
            }
            sw.spawnEntity(new ItemEntity(sw, this.getX(), this.getY(), this.getZ(),
                    new ItemStack(TrinityMod.ABYSS_CORE, 1)));
            broadcast("深渊之心沉没了……潮汐重归平静。你带走了深渊之核。");
        }
    }

    // ---------------- attributes ----------------

    public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createAbyssKingAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 350.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.6)
                .add(EntityAttributes.ATTACK_DAMAGE, 12.0)
                .add(EntityAttributes.FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0);
    }
}
