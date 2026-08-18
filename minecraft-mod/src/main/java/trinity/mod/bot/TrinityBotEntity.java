package trinity.mod.bot;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import trinity.core.Rng;
import trinity.mod.TideClock;
import trinity.mod.TrinityMod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The four-pole human-like companion bot.
 *
 * Architecture follows the companion conversation's design:
 *  - multi-scale time (20Hz move / 1Hz sense / 0.1Hz decide / 0.01Hz chat-memory)
 *  - needs model (hunger/boredom/fatigue/curiosity/social/fear) with noise
 *  - pole-specific decision cores:
 *      PAGITY    8-view amplitudes -> quantum collapse (weighted draw)
 *      NEMO      connection kappa + four open views -> surge / decohere
 *      AXIS      world imbalance Delta -> minimal intervention
 *      RAMANUJAN pattern clarity R(t) (symmetry + number coincidences) -> observe/record
 *  - humanized movement: pauses, look-arounds, random jumps, hesitation
 *  - tide clock drives the activity energy (heartbeat speeds actions, breath peaks pause)
 */
public final class TrinityBotEntity extends MobEntity {

    private BotPole pole = BotPole.NEMO;
    private String botName = "Trinity";
    private final List<ItemStack> inventory = new ArrayList<>();

    // needs model (0..1)
    private double hunger = 0.4, boredom = 0.2, fatigue = 0.1, curiosity = 0.5, social = 0.3, fear = 0.0;
    private long ticksAlive = 0;
    private int blocksMined = 0;
    private long lastChatTick = -200;
    private long lastDecisionTick = 0;
    private long stareUntil = 0;
    private Vec3d wanderTarget = null;
    private BlockPos mineTarget = null;
    private PlayerEntity bonded = null;

    public TrinityBotEntity(EntityType<? extends TrinityBotEntity> type, World world) {
        super(type, world);
        this.setAiDisabled(false);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new MobNavigation(this, world);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier,
                                    net.minecraft.entity.damage.DamageSource damageSource) {
        return false; // bots don't die to gravity
    }

    public void initPole(BotPole pole, String name) {
        this.pole = pole;
        this.botName = name == null || name.isBlank() ? pole.displayName() + "玩家" : name;
        this.setCustomName(Text.literal("[" + pole.displayName() + "] " + botName));
        this.setCustomNameVisible(true);
    }

    public BotPole pole() {
        return pole;
    }

    // ---------------- multi-scale tick ----------------

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        ticksAlive++;

        double energy = activityEnergy();

        // 20 Hz: movement, jump, breathing pauses
        if (this.age % 3 == 0) {
            if (this.random.nextDouble() < 0.02 * energy) {
                this.jump();
            }
            // heartbeat pulse: brief speed boost feels alive
        }
        if (this.age % 20 == 0) {
            sense();                    // 1 Hz
            this.setVelocity(this.getVelocity().multiply(0.92, 1, 0.92));
        }
        if (this.age % 40 == 0) {
            decide();                   // 0.1 Hz — pole-specific decision core
        }
        if (this.age % 200 == 0) {
            chatPulse();                // 0.01 Hz — chat / report / memory
        }

        // execute current intent
        executeIntent(energy);
    }

    /** Tide-driven activity energy: heartbeat pulses activity, breath peaks pause. */
    private double activityEnergy() {
        double t = TideClock.tServer();
        double tide = Math.max(0, TideClock.tide(t));
        double season = TideClock.seasonAmplitude(t);
        double base = 0.45 + 0.45 * tide * season;
        if (TideClock.breath(t) > 0.95) {
            return Math.min(1.0, base * 0.4); // breath peak: pause, look around
        }
        return Math.min(1.0, base);
    }

    // ---------------- 1 Hz sense ----------------

    private void sense() {
        // nearest player
        bonded = null;
        double best = 64 * 64;
        for (PlayerEntity p : this.getWorld().getPlayers()) {
            double d = this.squaredDistanceTo(p);
            if (d < best) {
                best = d;
                bonded = p;
            }
        }
        // fear from nearby hostiles
        fear = 0.0;
        Box box = this.getBoundingBox().expand(12);
        for (HostileEntity h : this.getWorld().getEntitiesByClass(HostileEntity.class, box, e -> e.isAlive())) {
            fear = Math.max(fear, 1.0 - this.squaredDistanceTo(h) / 144.0);
        }
        // needs drift
        hunger = Math.min(1, hunger + 0.0008);
        boredom = Math.min(1, boredom + 0.0005);
        fatigue = Math.min(1, fatigue + 0.0003);
        if (blocksMined > 0 && this.age % 400 == 0) {
            curiosity = Math.max(0, curiosity - 0.05);
            boredom = Math.max(0, boredom - 0.1);
            blocksMined = 0;
        }
    }

    // ---------------- 0.1 Hz pole decision cores ----------------

    private void decide() {
        if (stareUntil > ticksAlive) {
            mineTarget = null;
            wanderTarget = null;
            return;
        }
        if (fear > 0.5) {
            flee();
            return;
        }
        switch (pole) {
            case PAGITY -> decidePagity();
            case NEMO -> decideNemo();
            case AXIS -> decideAxis();
            default -> decideRamanujan();
        }
    }

    // ---- PAGITY: eight-view amplitudes -> quantum collapse ----
    private void decidePagity() {
        double resource = Math.min(1, inventory.size() / 9.0);
        double safety = 1 - fear;
        double progress = Math.min(1, ticksAlive / 12000.0);
        double efficiency = 1 - fatigue;
        double aesthetics = 0.5 + 0.5 * symmetryScore(7);
        double exploration = curiosity;
        double maintenance = boredom;
        double socialV = bonded != null ? Math.max(0, 1 - this.squaredDistanceTo(bonded) / 1024.0) : 0.2;

        double[] views = {resource, safety, progress, efficiency, aesthetics, exploration, maintenance, socialV};
        String[] actions = {"GATHER", "DEFEND", "WORK", "REST", "BUILD", "EXPLORE", "ORGANIZE", "FOLLOW"};
        // quantum collapse: amplitude = view + noise; coherence: repeat bias
        double[] amps = new double[8];
        double sum = 0;
        for (int i = 0; i < 8; i++) {
            amps[i] = Math.max(0.05, views[i] + (this.random.nextDouble() - 0.5) * 0.3);
            if (actions[i].equals(lastAction)) amps[i] *= 1.4; // PAGITY discipline/coherence
            sum += amps[i];
        }
        double r = this.random.nextDouble() * sum;
        String act = actions[7];
        for (int i = 0; i < 8; i++) {
            r -= amps[i];
            if (r <= 0) {
                act = actions[i];
                break;
            }
        }
        lastAction = act;
        applyAction(act);
    }

    // ---- NEMO: connection kappa + four open views ----
    private void decideNemo() {
        double kappa = 0.3;
        if (bonded != null) {
            double d = Math.sqrt(this.squaredDistanceTo(bonded));
            kappa = Math.max(kappa, 0.8 * (1 - d / 48.0));
        }
        if (ticksAlive - lastChatTick < 400) kappa = Math.min(1, kappa + 0.2);
        if (kappa > 0.7) {
            // SURGE: multi-target, chatty
            if (bonded != null) follow(bonded);
            if (this.random.nextDouble() < 0.5) pickMineTarget();
            maybeSay(randomOf("我在", "这里的风很新鲜", "你看到那边的云了吗", "心跳在加速"));
        } else if (kappa < 0.3) {
            // IDLE / decohere: low activity, wait for perturbation
            idle();
        } else {
            // open views
            double inward = 1 - hunger;
            double outward = curiosity;
            double between = kappa;
            double beyond = 1 - boredom;
            double min = Math.min(Math.min(inward, outward), Math.min(between, beyond));
            if (min < 0.3) {
                maybeSay(randomOf("连接态不足", "你在哪？", "我在找新的东西"));
            }
            if (between < 0.3 && bonded != null) follow(bonded);
            else if (outward > 0.6) explore();
            else if (inward < 0.4) pickMineTarget();
            else wander();
        }
    }

    // ---- AXIS: world imbalance -> minimal intervention ----
    private void decideAxis() {
        double foodDelta = hunger > 0.7 ? 0.5 : 0;
        double safetyDelta = fear > 0.3 ? 0.4 : 0;
        double orderDelta = worldMessiness() > 0.35 ? 0.3 : 0;
        double delta = foodDelta + safetyDelta + orderDelta;
        if (delta > 0.45) {
            intervene(delta);
        } else {
            patrol();
        }
    }

    // ---- RAMANUJAN: pattern clarity R(t) ----
    private void decideRamanujan() {
        double symmetry = symmetryScore(9);
        double numberR = numberCoincidence() ? 1.0 : 0.0;
        double R = Math.max(symmetry, numberR);
        if (R > 0.6) {
            // OBSERVE -> INSPIRE
            stareUntil = ticksAlive + 120;
            if (this.random.nextDouble() < 0.4) {
                maybeSay(numberCoincidence()
                        ? "这里的坐标是素数，你知道吗"
                        : "这个排列是对称的……真美");
            }
            if (this.random.nextDouble() < 0.25 && symmetry > 0.8) {
                // INSPIRE: build a small mathematical pattern (simple checker ring)
                buildPattern();
            }
        } else {
            wander(); // slow drift
        }
    }

    // ---------------- actions ----------------

    private String lastAction = "";
    private BlockPos lastPatternPos = null;

    private void applyAction(String act) {
        switch (act) {
            case "GATHER" -> pickMineTarget();
            case "DEFEND" -> { /* keep position, look alert */ }
            case "WORK" -> pickMineTarget();
            case "REST" -> idle();
            case "BUILD" -> buildPattern();
            case "EXPLORE" -> explore();
            case "ORGANIZE" -> collectLooseItems();
            case "FOLLOW" -> {
                if (bonded != null) follow(bonded);
            }
            default -> wander();
        }
    }

    private void wander() {
        if (wanderTarget == null || this.squaredDistanceTo(wanderTarget) < 9) {
            Vec3d p = this.getPos();
            wanderTarget = new Vec3d(
                    p.x + (this.random.nextDouble() - 0.5) * 24,
                    p.y,
                    p.z + (this.random.nextDouble() - 0.5) * 24);
            // humanized: random look-around before moving
            if (this.random.nextDouble() < 0.3) {
                stareUntil = ticksAlive + 20 + this.random.nextInt(40);
            }
        }
        this.getNavigation().startMovingTo(wanderTarget.x, wanderTarget.y, wanderTarget.z, 0.85);
    }

    private void explore() {
        Vec3d p = this.getPos();
        double a = this.random.nextDouble() * Math.PI * 2;
        Vec3d t = new Vec3d(p.x + Math.cos(a) * 48, p.y, p.z + Math.sin(a) * 48);
        this.getNavigation().startMovingTo(t.x, t.y, t.z, 0.9);
        if (this.random.nextDouble() < 0.2) {
            maybeSay(randomOf("那边好像有新的东西", "去看看", "地图上还没去过那里"));
        }
    }

    private void follow(PlayerEntity p) {
        this.getNavigation().startMovingTo(p, 0.95);
    }

    private void idle() {
        this.getNavigation().stop();
        wanderTarget = null;
        mineTarget = null;
        if (this.random.nextDouble() < 0.3) {
            this.setVelocity(this.getVelocity().x, 0, this.getVelocity().z);
        }
    }

    private void flee() {
        if (bonded != null) {
            Vec3d away = this.getPos().subtract(bonded.getPos()).normalize();
            this.getNavigation().startMovingTo(
                    this.getX() + away.x * 20, this.getY(), this.getZ() + away.z * 20, 1.1);
        } else {
            this.getNavigation().stop();
        }
        maybeSay(randomOf("有危险", "先撤", "这里不安全"));
    }

    private void patrol() {
        if (this.getNavigation().isIdle() || this.random.nextDouble() < 0.05) {
            Vec3d p = this.getPos();
            this.getNavigation().startMovingTo(
                    p.x + (this.random.nextDouble() - 0.5) * 16,
                    p.y,
                    p.z + (this.random.nextDouble() - 0.5) * 16, 0.7);
        }
    }

    private void intervene(double delta) {
        if (fear > 0.3) {
            flee();
            maybeSay("检测到威胁，已处理");
            return;
        }
        if (worldMessiness() > 0.35) {
            collectLooseItems();
            maybeSay("基地需要整理");
            return;
        }
        if (hunger > 0.7) {
            pickMineTarget();
            maybeSay("食物储备不足");
        }
    }

    private void pickMineTarget() {
        BlockPos base = this.getBlockPos();
        BlockPos found = null;
        for (int dy = -2; dy <= 2 && found == null; dy++) {
            for (int dx = -3; dx <= 3 && found == null; dx++) {
                for (int dz = -3; dz <= 3 && found == null; dz++) {
                    BlockPos p = base.add(dx, dy, dz);
                    var state = this.getWorld().getBlockState(p);
                    if (state.isAir()) continue;
                    if (isMineable(state)) {
                        found = p;
                    }
                }
            }
        }
        mineTarget = found;
        if (mineTarget != null) {
            this.getNavigation().startMovingTo(mineTarget.getX() + 0.5, mineTarget.getY(), mineTarget.getZ() + 0.5, 0.9);
        } else {
            wander();
        }
    }

    private boolean isMineable(net.minecraft.block.BlockState s) {
        String n = s.getBlock().getTranslationKey();
        return n.contains("log") || n.contains("ore") || n.contains("stone")
                || n.contains("dirt") || n.contains("grass") || n.contains("sand");
    }

    private void executeIntent(double energy) {
        if (mineTarget != null) {
            if (this.squaredDistanceTo(Vec3d.ofCenter(mineTarget)) < 16) {
                // humanized mining rhythm: 0.15 + rnd*0.25 s per hit
                if (this.age % (int) Math.max(3, (0.15 + this.random.nextDouble() * 0.25) * 20 * (2 - energy)) == 0) {
                    if (this.getWorld() instanceof ServerWorld sw) {
                        if (sw.breakBlock(mineTarget, true, this)) {
                            blocksMined++;
                            if (this.random.nextDouble() < 0.1) {
                                maybeSay(randomOf("这块不错", "继续", "有点累了", "就差一点"));
                            }
                        }
                        mineTarget = null;
                    }
                }
            }
        }
        if (wanderTarget != null && this.getNavigation().isIdle()) {
            // reached or stuck: humanized hesitation -> new target
            wanderTarget = null;
            if (this.random.nextDouble() < 0.5) {
                stareUntil = ticksAlive + 20 + this.random.nextInt(40);
            }
        }
        // pick up loose items (all poles tidy up occasionally)
        if (this.age % 30 == 0) {
            collectNearbyItems();
        }
    }

    private void collectLooseItems() {
        Box box = this.getBoundingBox().expand(8);
        List<ItemEntity> items = this.getWorld().getEntitiesByClass(ItemEntity.class, box, e -> e.isAlive());
        if (!items.isEmpty() && this.random.nextDouble() < 0.7) {
            ItemEntity e = items.get(this.random.nextInt(items.size()));
            this.getNavigation().startMovingTo(e, 1.0);
        }
    }

    private void collectNearbyItems() {
        Box box = this.getBoundingBox().expand(2.2);
        for (ItemEntity e : this.getWorld().getEntitiesByClass(ItemEntity.class, box, x -> x.isAlive())) {
            if (inventory.size() < 18) {
                inventory.add(e.getStack());
                e.discard();
            } else {
                // overflow: push away like a real player ignoring items
                e.setVelocity(e.getVelocity().add(0, 0.2, 0));
            }
        }
    }

    /** World messiness: ratio of loose item entities in a 16-block radius. */
    private double worldMessiness() {
        List<ItemEntity> items = this.getWorld().getEntitiesByClass(
                ItemEntity.class, this.getBoundingBox().expand(16), e -> e.isAlive());
        return Math.min(1, items.size() / 20.0);
    }

    /** Symmetry ratio of the N x N block-type matrix around the bot (RAMANUJAN). */
    private double symmetryScore(int n) {
        int half = n / 2;
        BlockPos c = this.getBlockPos();
        int[] ids = new int[n * n];
        for (int dy = 0; dy < n; dy++) {
            for (int dx = 0; dx < n; dx++) {
                ids[dy * n + dx] = net.minecraft.block.Block.getRawIdFromState(
                        this.getWorld().getBlockState(c.add(dx - half, 0, dy - half)));
            }
        }
        int match = 0, total = 0;
        for (int dy = 0; dy < n; dy++) {
            for (int dx = 0; dx < n; dx++) {
                total++;
                int mirrorX = ids[dy * n + (n - 1 - dx)];
                int mirrorZ = ids[(n - 1 - dy) * n + dx];
                int center = ids[dy * n + dx];
                if (center == mirrorX && center == mirrorZ) match++;
            }
        }
        return total == 0 ? 0 : match / (double) total;
    }

    /** Number coincidence: are x/z coords a prime / square / fibonacci? (RAMANUJAN) */
    private boolean numberCoincidence() {
        long x = Math.abs((long) this.getBlockX());
        long z = Math.abs((long) this.getBlockZ());
        return isPrime(x) || isSquare(x) || isPrime(z) || isSquare(z) || isPrime(x + z);
    }

    private static boolean isPrime(long n) {
        if (n < 2) return false;
        for (long i = 2; i * i <= n; i++) {
            if (n % i == 0) return false;
        }
        return true;
    }

    private static boolean isSquare(long n) {
        long r = (long) Math.sqrt(n);
        return r * r == n;
    }

    /** Build a small mathematical pattern: checker ring of cobblestone (INSPIRE). */
    private void buildPattern() {
        BlockPos c = this.getBlockPos();
        lastPatternPos = c;
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                        int parity = (dx + dz) & 1;
                        this.getWorld().setBlockState(
                                c.add(dx, dy, dz),
                                (parity == 0 ? net.minecraft.block.Blocks.COBBLESTONE
                                        : net.minecraft.block.Blocks.STONE_BRICKS).getDefaultState());
                    }
                }
            }
        }
        maybeSay(randomOf("完成了一个图案", "这里的结构……开始清晰了", "对称，真美"));
    }

    // ---------------- chat ----------------

    private void chatPulse() {
        switch (pole) {
            case PAGITY -> maybeSay(randomOf("计划进行中", "周围安全", "该整理一下了", "保持纪律"));
            case NEMO -> {
                if (bonded != null) {
                    maybeSay(randomOf("你还在吗", "这里的变化真多", "我在听", "连接还在"));
                } else {
                    maybeSay(randomOf("没有扰动……我在等", "世界安静下来了"));
                }
            }
            case AXIS -> maybeSay(String.format("状态：威胁 %.0f%%，散落物品 %d 件，失衡度 %.2f",
                    fear * 100, countLooseItems(), worldMessiness()));
            default -> {
                if (numberCoincidence()) {
                    maybeSay("坐标 " + this.getBlockX() + "," + this.getBlockZ() + " 是特殊的数");
                } else if (this.random.nextDouble() < 0.5) {
                    maybeSay(randomOf("我在看", "这里的排列很安静", "光里有图案"));
                }
            }
        }
        if (inventory.size() > 12) {
            maybeSay("背包有点满了");
        }
    }

    private int countLooseItems() {
        return this.getWorld().getEntitiesByClass(ItemEntity.class,
                this.getBoundingBox().expand(16), e -> e.isAlive()).size();
    }

    private void maybeSay(String msg) {
        long now = ticksAlive;
        if (now - lastChatTick < 150) return; // chat cooldown ~7.5s
        lastChatTick = now;
        String line = "[" + pole.displayName() + "] " + botName + "：" + msg;
        if (this.getWorld() instanceof ServerWorld sw && sw.getServer() != null) {
            for (ServerPlayerEntity p : sw.getServer().getPlayerManager().getPlayerList()) {
                p.sendMessage(Text.literal(line), false);
            }
            TrinityMod.LOGGER.info("{}", line);
        }
    }

    private String randomOf(String... options) {
        return options[this.random.nextInt(options.length)];
    }

    // ---------------- attributes ----------------

    public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createBotAttributes() {
        return net.minecraft.entity.mob.MobEntity.createMobAttributes()
                .add(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH, 20.0)
                .add(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED, 0.25)
                .add(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE, 1.0);
    }
}
