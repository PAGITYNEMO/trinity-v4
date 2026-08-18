package trinity.mod.beast;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import trinity.mod.TrinityMod;

/**
 * TrinityBossEntity — the shared skeleton of the world's bosses (the sky's
 * Aurora and the deep's Abyss King). They carry a server boss bar, run a
 * three-phase health progression (66% / 33%), always keep the nearest player
 * as their target, and follow the same multi-scale heartbeat the bots and the
 * beasts use. Subclasses implement bossTick() and the death/loot behaviour.
 */
public abstract class TrinityBossEntity extends MobEntity {

    protected ServerBossBar bossBar;
    protected PlayerEntity target;
    protected long ticksAlive = 0;
    protected int attackCooldown = 0;
    protected int phase = 1;

    protected TrinityBossEntity(EntityType<? extends TrinityBossEntity> type, World world) {
        super(type, world);
        this.setAiDisabled(false);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new MobNavigation(this, world);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false; // bosses do not die to gravity
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        ticksAlive++;

        initBossBar();
        if (this.age % 20 == 0) {
            senseTarget();
        }
        updatePhase();
        updateBar();
        if (attackCooldown > 0) attackCooldown--;
        bossTick();
    }

    // ---------------- boss bar ----------------

    private void initBossBar() {
        if (bossBar == null) {
            bossBar = new ServerBossBar(Text.literal(bossName()), BossBar.Color.PURPLE, BossBar.Style.PROGRESS);
            for (PlayerEntity pe : this.getWorld().getPlayers()) {
                if (pe instanceof ServerPlayerEntity sp) {
                    bossBar.addPlayer(sp);
                }
            }
            TrinityMod.LOGGER.info("[TRINITY v4.0] boss bar raised: {} @ {}",
                    bossName(), this.getBlockPos());
        } else if (this.age % 40 == 0) {
            for (PlayerEntity pe : this.getWorld().getPlayers()) {
                if (pe instanceof ServerPlayerEntity sp && !bossBar.getPlayers().contains(sp)) {
                    bossBar.addPlayer(sp);
                }
            }
        }
    }

    private void updateBar() {
        if (bossBar != null) {
            bossBar.setPercent(this.getHealth() / this.getMaxHealth());
        }
    }

    private void updatePhase() {
        double hp = this.getHealth() / this.getMaxHealth();
        int ph = hp > 0.66 ? 1 : hp > 0.33 ? 2 : 3;
        if (ph != phase) {
            phase = ph;
            onPhaseChange();
        }
    }

    /** Called when the boss crosses 66% / 33% health. */
    protected void onPhaseChange() {
        BossBar.Color c = switch (phase) {
            case 2 -> BossBar.Color.BLUE;
            case 3 -> BossBar.Color.RED;
            default -> BossBar.Color.PURPLE;
        };
        bossBar.setColor(c);
        bossBar.setName(Text.literal(bossName() + " · 阶段 " + phase));
        broadcast(bossName() + (phase == 3 ? " 狂暴了！" : " 进入了新的阶段"));
    }

    private void senseTarget() {
        target = null;
        double best = 64 * 64;
        for (PlayerEntity p : this.getWorld().getPlayers()) {
            double d = this.squaredDistanceTo(p);
            if (d < best) {
                best = d;
                target = p;
            }
        }
    }

    /** The current attack rhythm (ticks between attacks), tuned by phase. */
    protected int attackInterval() {
        return switch (phase) {
            case 2 -> 55;
            case 3 -> 30;
            default -> 80;
        };
    }

    /** Subclass: one beat of the boss's own brain, called every tick. */
    protected abstract void bossTick();

    /** Display name used for the boss bar and messages. */
    protected abstract String bossName();

    // ---------------- helpers ----------------

    protected void broadcast(String msg) {
        if (this.getWorld() instanceof ServerWorld sw && sw.getServer() != null) {
            for (ServerPlayerEntity p : sw.getServer().getPlayerManager().getPlayerList()) {
                p.sendMessage(Text.literal("[" + bossName() + "] " + msg), false);
            }
            TrinityMod.LOGGER.info("{}", "[" + bossName() + "] " + msg);
        }
    }

    protected void particles(ParticleEffect type, double dx, double dy, double dz,
                             int count, double sx, double sy, double sz, double speed) {
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(type, this.getX() + dx, this.getY() + dy, this.getZ() + dz,
                    count, sx, sy, sz, speed);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (bossBar != null) {
            bossBar.clearPlayers();
        }
        super.remove(reason);
    }
}
