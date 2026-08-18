package trinity.mod.bot;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import trinity.core.FastTerrain;
import trinity.core.Rng;
import trinity.mod.TideClock;
import trinity.mod.TrinityMod;
import trinity.mod.TrinityTerrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The four-pole human-like companion bot, built the same way the terrain was:
 * the bot perceives the world through the SAME terrain evaluator the world was
 * generated with (same seed, same FastTerrain / Ramanujan / q-pairs), so its
 * "eyes" and the map are one system. It observes true Ramanujan interference
 * patterns, measures entropy/structure like AXIS, mines the real vein chains
 * the map grew, and keeps a nebula-seed memory of high-connection places.
 *
 *  - perception  (1 Hz): coprime-sampled terrain probes + true R(t) + local H/S
 *  - decision    (0.1 Hz): pole cores (PAGITY collapse / NEMO kappa / AXIS delta /
 *                          RAMANUJAN pattern clarity)
 *  - execution   (20 Hz): navigation, mining, collecting, humanized movement
 *  - memory      (0.01 Hz): nebula seeds (store / decay / revisit)
 *  - tide clock drives the activity energy; seasons modulate it.
 */
public final class TrinityBotEntity extends MobEntity {

    private BotPole pole = BotPole.NEMO;
    private String botName = "Trinity";

    // ---- terrain coupling: the SAME evaluator the map was generated with ----
    private FastTerrain ft;
    private TrinityTerrain terrain;
    private int q1, q2;
    private long terrainSeed = Long.MIN_VALUE;

    // ---- perception state ----
    private double localSurface;   // mean coprime-sampled surface01 around the bot
    private double localRichness;  // mean totient region weight around the bot
    private double R;              // true Ramanujan interference clarity R(t)
    private double Hlocal, Slocal; // AXIS measures of the surrounding blocks
    private double kappa;          // NEMO connection strength

    // ---- needs model ----
    private double hunger = 0.4, boredom = 0.2, fatigue = 0.1, curiosity = 0.5, social = 0.3, fear = 0.0;
    private long ticksAlive = 0;
    private int blocksMined = 0;
    private long lastChatTick = -200;
    private long stareUntil = 0;
    private Vec3d wanderTarget = null;
    private BlockPos mineTarget = null;
    private PlayerEntity bonded = null;

    // ---- nebula seeds (star-seeding memory, same rule as the protocol) ----
    private final Map<BlockPos, Double> seeds = new HashMap<>();

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

        // 20 Hz: movement / jumps / breathing pauses
        if (this.age % 3 == 0 && this.random.nextDouble() < 0.02 * energy) {
            this.jump();
        }
        if (this.age % 20 == 0) {
            sense();
            this.setVelocity(this.getVelocity().multiply(0.92, 1, 0.92));
        }
        if (this.age % 40 == 0) {
            decide();
        }
        if (this.age % 200 == 0) {
            chatPulse();
            decaySeeds();
        }

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

    // ---------------- 1 Hz perception (terrain-coupled) ----------------

    private void initTerrain() {
        if (this.getWorld() instanceof ServerWorld sw && terrainSeed != sw.getSeed()) {
            terrainSeed = sw.getSeed();
            ft = new FastTerrain(terrainSeed, TrinityTerrain.HEIGHT);
            terrain = new TrinityTerrain(terrainSeed);
            int[] tq = ft.templateQ();
            q1 = tq[0];
            q2 = tq[1];
            TrinityMod.LOGGER.info("[TRINITY v4.0] bot terrain coupled: seed={} q1={} q2={} (same map evaluator)",
                    terrainSeed, q1, q2);
        }
    }

    private void sense() {
        initTerrain();
        if (ft == null) return;

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
        // kappa: connection strength (player distance + recent chat + new finds)
        kappa = 0.3;
        if (bonded != null) {
            double d = Math.sqrt(this.squaredDistanceTo(bonded));
            kappa = Math.max(kappa, 0.8 * (1 - d / 48.0));
        }
        if (ticksAlive - lastChatTick < 400) kappa = Math.min(1, kappa + 0.2);

        // fear
        fear = 0.0;
        for (HostileEntity h : this.getWorld().getEntitiesByClass(
                HostileEntity.class, this.getBoundingBox().expand(12), e -> e.isAlive())) {
            fear = Math.max(fear, 1.0 - this.squaredDistanceTo(h) / 144.0);
        }

        // coprime-sampled terrain probes: the bot "sees" the map through the
        // same FastTerrain the world was generated with (gcd(x,z)==1 samples)
        int bx = this.getBlockX();
        int bz = this.getBlockZ();
        double sumSurf = 0, sumRich = 0;
        int n = 0;
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                if (Rng.gcd(dx, dz) != 1) continue; // coprime sampling
                sumSurf += ft.surface01(bx + dx, bz + dz);
                sumRich += ft.ram().regionWeight(bx + dx, bz + dz, terrainSeed);
                n++;
            }
        }
        localSurface = n > 0 ? sumSurf / n : 0.5;
        localRichness = n > 0 ? sumRich / n : 0.5;

        // true Ramanujan interference clarity: the bot observes the terrain
        // field's residue concentration x activity — the engine's own R(t)
        R = ramClarity(bx, bz);

        // AXIS measures of the surrounding blocks
        measureLocal();

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

    /** True R(t): residue concentration x activity of the terrain field around the bot. */
    private double ramClarity(int bx, int bz) {
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
        double stdNorm = Math.min(1, Math.sqrt(var / Math.max(1, cells)) / 0.25);
        double best = 0;
        for (int q = 3; q <= 12; q++) {
            int[] hist = new int[q];
            for (int dx = -8; dx <= 8; dx += 2) {
                for (int dz = -8; dz <= 8; dz += 2) {
                    int r = (int) Math.round(ft.surface01(bx + dx, bz + dz) * (q * 8)) % q;
                    if (r < 0) r += q;
                    hist[r]++;
                }
            }
            double hE = 0;
            for (int r = 0; r < q; r++) {
                double p = hist[r] / (double) cells;
                if (p > 0) hE -= p * Math.log(p);
            }
            double rq = (1 - hE / Math.log(q)) * stdNorm;
            if (rq > best) best = rq;
        }
        return best;
    }

    /** AXIS measures: Shannon entropy + Laplacian structure of nearby blocks. */
    private void measureLocal() {
        int n = 8, half = n / 2;
        BlockPos c = this.getBlockPos();
        int[] ids = new int[n * n];
        for (int dz = 0; dz < n; dz++) {
            for (int dx = 0; dx < n; dx++) {
                ids[dz * n + dx] = net.minecraft.block.Block.getRawIdFromState(
                        this.getWorld().getBlockState(c.add(dx - half, 0, dz - half)));
            }
        }
        int[] hist = new int[256];
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int id : ids) {
            hist[id & 0xFF]++;
        }
        int total = n * n;
        double hE = 0;
        for (int b = 0; b < 256; b++) {
            if (hist[b] > 0) {
                double p = hist[b] / (double) total;
                hE -= p * Math.log(p);
            }
        }
        Hlocal = hE / Math.log(256);
        double lap = 0, e = 1e-9;
        for (int i = 1; i < n - 1; i++) {
            for (int j = 1; j < n - 1; j++) {
                double l = 4 * ids[i * n + j] - ids[(i - 1) * n + j] - ids[(i + 1) * n + j]
                        - ids[i * n + j - 1] - ids[i * n + j + 1];
                lap += l * l;
                e += ids[i * n + j] * (double) ids[i * n + j];
            }
        }
        Slocal = Math.min(1, lap / e * 0.01);
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

    /** PAGITY: eight views (terrain-coupled) -> quantum collapse with coherence. */
    private void decidePagity() {
        double resource = Math.min(1, inventory.size() / 9.0 + localRichness * 0.3);
        double safety = 1 - fear;
        double progress = Math.min(1, ticksAlive / 12000.0);
        double efficiency = 1 - fatigue;
        double aesthetics = 0.5 + 0.5 * Slocal;
        double exploration = curiosity * (0.5 + localSurface); // high ground invites
        double maintenance = boredom;
        double socialV = bonded != null ? Math.max(0, 1 - this.squaredDistanceTo(bonded) / 1024.0) : 0.2;

        double[] views = {resource, safety, progress, efficiency, aesthetics, exploration, maintenance, socialV};
        String[] actions = {"GATHER", "DEFEND", "WORK", "REST", "BUILD", "EXPLORE", "ORGANIZE", "FOLLOW"};
        double[] amps = new double[8];
        double sum = 0;
        for (int i = 0; i < 8; i++) {
            amps[i] = Math.max(0.05, views[i] + (this.random.nextDouble() - 0.5) * 0.3);
            if (actions[i].equals(lastAction)) amps[i] *= 1.4;
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

    /** NEMO: kappa + four open views; terrain finds are external strong signals. */
    private void decideNemo() {
        if (kappa > 0.7) {
            // SURGE: multi-target, chatty
            if (bonded != null) follow(bonded);
            if (this.random.nextDouble() < 0.5) pickMineTarget();
            maybeSay(randomOf("我在", "这里的风很新鲜", "你看到那边的云了吗", "心跳在加速"));
        } else if (kappa < 0.3) {
            idle();
        } else {
            double inward = 1 - hunger;
            double outward = curiosity * (0.4 + localRichness);
            double between = kappa;
            double beyond = 1 - boredom;
            double min = Math.min(Math.min(inward, outward), Math.min(between, beyond));
            if (min < 0.3) {
                maybeSay(randomOf("连接态不足", "你在哪？", "我在找新的东西"));
            }
            if (between < 0.3 && bonded != null) follow(bonded);
            else if (outward > 0.6) exploreRich();
            else if (inward < 0.4) pickMineTarget();
            else wander();
        }
    }

    /** AXIS: world imbalance Delta (terrain structure + mess + threats). */
    private void decideAxis() {
        double foodDelta = hunger > 0.7 ? 0.5 : 0;
        double safetyDelta = fear > 0.3 ? 0.4 : 0;
        double orderDelta = worldMessiness() > 0.35 ? 0.3 : 0;
        double structureDelta = Math.abs(Slocal - 0.35) > 0.25 ? 0.2 : 0;
        double delta = foodDelta + safetyDelta + orderDelta + structureDelta;
        if (delta > 0.45) {
            intervene(delta);
        } else {
            patrol();
        }
    }

    /** RAMANUJAN: true pattern clarity R(t) from the terrain field. */
    private void decideRamanujan() {
        if (R > 0.55) {
            stareUntil = ticksAlive + 120;
            if (this.random.nextDouble() < 0.5) {
                maybeSay(String.format("这里的图纹清晰度是 %.2f……很美", R));
            }
            if (this.random.nextDouble() < 0.3) {
                buildRamanujanPattern();
            }
        } else {
            driftTowardPattern();
        }
    }

    // ---------------- actions ----------------

    private String lastAction = "";

    private void applyAction(String act) {
        switch (act) {
            case "GATHER" -> pickMineTarget();
            case "DEFEND" -> { /* alert posture */ }
            case "WORK" -> pickMineTarget();
            case "REST" -> idle();
            case "BUILD" -> buildRamanujanPattern();
            case "EXPLORE" -> exploreRich();
            case "ORGANIZE" -> collectLooseItems();
            case "FOLLOW" -> {
                if (bonded != null) follow(bonded);
            }
            default -> wander();
        }
    }

    /** Wander with terrain knowledge: prefer phi-rich, high-ground directions. */
    private void wander() {
        if (wanderTarget == null || this.squaredDistanceTo(wanderTarget) < 9) {
            Vec3d p = this.getPos();
            double bestScore = -1;
            double bestX = p.x, bestZ = p.z;
            for (int i = 0; i < 5; i++) {
                double a = this.random.nextDouble() * Math.PI * 2;
                int tx = (int) (p.x + Math.cos(a) * 16);
                int tz = (int) (p.z + Math.sin(a) * 16);
                double score = ft.surface01(tx, tz) * 0.4
                        + ft.ram().regionWeight(tx, tz, terrainSeed) * 0.6
                        + this.random.nextDouble() * 0.3;
                if (score > bestScore) {
                    bestScore = score;
                    bestX = tx;
                    bestZ = tz;
                }
            }
            wanderTarget = new Vec3d(bestX, p.y, bestZ);
            if (this.random.nextDouble() < 0.3) {
                stareUntil = ticksAlive + 20 + this.random.nextInt(40);
            }
        }
        this.getNavigation().startMovingTo(wanderTarget.x, wanderTarget.y, wanderTarget.z, 0.85);
    }

    /** Explore toward the richest terrain region (NEMO outward / PAGITY explore). */
    private void exploreRich() {
        Vec3d p = this.getPos();
        double best = -1;
        double bestX = p.x, bestZ = p.z;
        for (int i = 0; i < 8; i++) {
            double a = i / 8.0 * Math.PI * 2;
            int tx = (int) (p.x + Math.cos(a) * 48);
            int tz = (int) (p.z + Math.sin(a) * 48);
            double score = ft.ram().regionWeight(tx, tz, terrainSeed);
            if (score > best) {
                best = score;
                bestX = tx;
                bestZ = tz;
            }
        }
        this.getNavigation().startMovingTo(bestX, p.y, bestZ, 0.9);
        if (this.random.nextDouble() < 0.2) {
            maybeSay(randomOf("那边好像有新的东西", "去看看", "富饶的方向"));
        }
    }

    /** RAMANUJAN drift: walk along the gradient of true pattern clarity. */
    private void driftTowardPattern() {
        int bx = this.getBlockX(), bz = this.getBlockZ();
        double c = ramClarity(bx, bz);
        double best = c;
        double bestX = bx, bestZ = bz;
        for (int i = 0; i < 8; i++) {
            double a = i / 8.0 * Math.PI * 2;
            int tx = bx + (int) (Math.cos(a) * 12);
            int tz = bz + (int) (Math.sin(a) * 12);
            double s = ramClarity(tx, tz);
            if (s > best) {
                best = s;
                bestX = tx;
                bestZ = tz;
            }
        }
        this.getNavigation().startMovingTo(bestX + 0.5, this.getY(), bestZ + 0.5, 0.75);
    }

    private void follow(PlayerEntity p) {
        this.getNavigation().startMovingTo(p, 0.95);
    }

    private void idle() {
        this.getNavigation().stop();
        wanderTarget = null;
        mineTarget = null;
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
        maybeSay(String.format("失衡度 %.2f：结构 %.2f 偏离目标", delta, Math.abs(Slocal - 0.35)));
    }

    /** Mine target from the REAL vein lattice the map grew (FastTerrain.oreType). */
    private void pickMineTarget() {
        BlockPos c = this.getBlockPos();
        BlockPos found = null;
        int bestScore = -1;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos p = c.add(dx, dy, dz);
                    int ot = ft.oreType(p.getX(), p.getY(), p.getZ());
                    int score = ot != 0 ? ot : 0;
                    if (score > bestScore) {
                        bestScore = score;
                        found = p;
                    }
                }
            }
        }
        mineTarget = found;
        if (mineTarget != null) {
            this.getNavigation().startMovingTo(mineTarget.getX() + 0.5, mineTarget.getY(), mineTarget.getZ() + 0.5, 0.9);
            // nebula seed: high-connection find
            seeds.put(mineTarget, 1.0);
        } else {
            wander();
        }
    }

    private void executeIntent(double energy) {
        if (mineTarget != null) {
            if (this.squaredDistanceTo(Vec3d.ofCenter(mineTarget)) < 16) {
                if (this.age % (int) Math.max(3, (0.15 + this.random.nextDouble() * 0.25) * 20 * (2 - energy)) == 0) {
                    if (this.getWorld() instanceof ServerWorld sw) {
                        if (sw.breakBlock(mineTarget, true, this)) {
                            blocksMined++;
                            int ot = ft.oreType(mineTarget.getX(), mineTarget.getY(), mineTarget.getZ());
                            if (ot != 0) {
                                kappa = Math.min(1, kappa + 0.3); // external strong signal
                                maybeSay(randomOf("挖到矿了", "这条矿脉是真的", "这里连接很强"));
                            }
                        }
                        mineTarget = null;
                    }
                }
            }
        }
        if (wanderTarget != null && this.getNavigation().isIdle()) {
            wanderTarget = null;
            if (this.random.nextDouble() < 0.5) {
                stareUntil = ticksAlive + 20 + this.random.nextInt(40);
            }
        }
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
                e.setVelocity(e.getVelocity().add(0, 0.2, 0));
            }
        }
    }

    private final List<ItemStack> inventory = new ArrayList<>();

    private double worldMessiness() {
        List<ItemEntity> items = this.getWorld().getEntitiesByClass(
                ItemEntity.class, this.getBoundingBox().expand(16), e -> e.isAlive());
        return Math.min(1, items.size() / 20.0);
    }

    /** Build a pattern from the TRUE Ramanujan interference template (INSPIRE). */
    private void buildRamanujanPattern() {
        BlockPos c = this.getBlockPos();
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    double t = ft.ram().template(q1, q2, c.getX() + dx, c.getZ() + dz);
                    if (t > 0.35) {
                        this.getWorld().setBlockState(
                                c.add(dx, dy, dz),
                                ((dx + dz) % 3 == 0 ? net.minecraft.block.Blocks.COBBLESTONE
                                        : net.minecraft.block.Blocks.STONE_BRICKS).getDefaultState());
                    }
                }
            }
        }
        maybeSay(randomOf("干涉图纹……我把它放在了地上", "这里的结构开始清晰了", "对称，真美"));
    }

    /** Nebula seeds: decay unused seeds, prune dead ones. */
    private void decaySeeds() {
        if (seeds.isEmpty()) return;
        var it = seeds.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            double w = e.getValue() * 0.95;
            if (w < 0.01) {
                it.remove();
            } else {
                e.setValue(w);
            }
        }
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
            case AXIS -> maybeSay(String.format("H=%.2f S=%.2f R=%.2f 失衡=%.2f 种子=%d",
                    Hlocal, Slocal, R,
                    (hunger > 0.7 ? 0.5 : 0) + (fear > 0.3 ? 0.4 : 0) + (worldMessiness() > 0.35 ? 0.3 : 0),
                    seeds.size()));
            default -> {
                if (R > 0.5) {
                    maybeSay(String.format("图纹清晰度 R=%.2f，坐标 %d,%d", R, this.getBlockX(), this.getBlockZ()));
                } else if (this.random.nextDouble() < 0.5) {
                    maybeSay(randomOf("我在看", "这里的排列很安静", "光里有图案"));
                }
            }
        }
        if (inventory.size() > 12) {
            maybeSay("背包有点满了");
        }
    }

    private void maybeSay(String msg) {
        long now = ticksAlive;
        if (now - lastChatTick < 150) return;
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
