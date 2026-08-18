package trinity.mod.bot;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import trinity.core.FastTerrain;
import trinity.mod.TrinityMod;
import trinity.mod.TrinityTerrain;
import trinity.mod.beast.AbyssEntity;
import trinity.mod.beast.AbyssKingEntity;
import trinity.mod.beast.AuroraEntity;
import trinity.mod.beast.SkyraEntity;

import java.util.List;

/** /trinity bot summon <pole> [name] | list | remove — the four-pole companion.
 *  /trinity sky summon|remove, /trinity abyss summon|remove — the natives. */
public final class TrinityBotCommand {

    private TrinityBotCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("trinity")
                    .then(CommandManager.literal("bot")
                            .then(CommandManager.literal("summon")
                                    .then(CommandManager.argument("pole", StringArgumentType.word())
                                            .executes(ctx -> summon(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "pole"), null))
                                            .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                                    .executes(ctx -> summon(ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "pole"),
                                                            StringArgumentType.getString(ctx, "name"))))))
                            .then(CommandManager.literal("list")
                                    .executes(ctx -> list(ctx.getSource())))
                            .then(CommandManager.literal("remove")
                                    .executes(ctx -> remove(ctx.getSource()))))
                    .then(CommandManager.literal("sky")
                            .then(CommandManager.literal("summon")
                                    .executes(ctx -> summonSky(ctx.getSource(), null, null))
                                    .then(CommandManager.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                            .then(CommandManager.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                    .executes(ctx -> summonSky(ctx.getSource(),
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z"))))))
                            .then(CommandManager.literal("remove")
                                    .executes(ctx -> removeType(ctx.getSource(), "skyra"))))
                    .then(CommandManager.literal("abyss")
                            .then(CommandManager.literal("summon")
                                    .executes(ctx -> summonAbyss(ctx.getSource(), null, null))
                                    .then(CommandManager.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                            .then(CommandManager.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                    .executes(ctx -> summonAbyss(ctx.getSource(),
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z"))))))
                            .then(CommandManager.literal("remove")
                                    .executes(ctx -> removeType(ctx.getSource(), "abyss"))))
                    .then(CommandManager.literal("boss")
                            .then(CommandManager.literal("aurora")
                                    .then(CommandManager.literal("summon")
                                            .executes(ctx -> summonBoss(ctx.getSource(), "aurora", null, null))
                                            .then(CommandManager.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                    .then(CommandManager.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                            .executes(ctx -> summonBoss(ctx.getSource(), "aurora",
                                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z"))))))
                                    .then(CommandManager.literal("remove")
                                            .executes(ctx -> removeBoss(ctx.getSource(), "aurora"))))
                            .then(CommandManager.literal("abyss")
                                    .then(CommandManager.literal("summon")
                                            .executes(ctx -> summonBoss(ctx.getSource(), "abyss", null, null))
                                            .then(CommandManager.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                    .then(CommandManager.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                            .executes(ctx -> summonBoss(ctx.getSource(), "abyss",
                                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z"))))))
                                    .then(CommandManager.literal("remove")
                                            .executes(ctx -> removeBoss(ctx.getSource(), "abyss"))))));
        });
    }

    private static int summon(ServerCommandSource source, String poleName, String name) throws CommandSyntaxException {
        BotPole pole = BotPole.parse(poleName);
        if (pole == null) {
            source.sendError(Text.literal("未知极：PAGITY / NEMO / AXIS / RAMANUJAN"));
            return 0;
        }
        ServerWorld world = source.getWorld();
        TrinityBotEntity bot = new TrinityBotEntity(TrinityMod.BOT_TYPE, world);
        bot.initPole(pole, name);
        ServerPlayerEntity player = source.getPlayer();
        Vec3d pos;
        if (player != null) {
            pos = player.getPos().add(player.getRotationVector().multiply(3));
        } else {
            int sx = world.getSpawnPos().getX();
            int sz = world.getSpawnPos().getZ();
            pos = new Vec3d(sx + 0.5, world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, sx, sz) + 1, sz + 0.5);
        }
        bot.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
        world.spawnEntity(bot);
        source.sendFeedback(() -> Text.literal("[" + pole.displayName() + "] "
                + bot.getCustomName().getString() + " 已加入世界"), true);
        return 1;
    }

    private static int list(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        List<? extends TrinityBotEntity> bots = world.getEntitiesByType(TrinityMod.BOT_TYPE, e -> e.isAlive());
        if (bots.isEmpty()) {
            source.sendFeedback(() -> Text.literal("当前没有 bot"), false);
            return 0;
        }
        for (TrinityBotEntity b : bots) {
            String n = b.getCustomName() == null ? "?" : b.getCustomName().getString();
            source.sendFeedback(() -> Text.literal("· " + n + " @" + b.getBlockX() + "," + b.getBlockY() + "," + b.getBlockZ()),
                    false);
        }
        return bots.size();
    }

    private static int remove(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        List<? extends TrinityBotEntity> bots = world.getEntitiesByType(TrinityMod.BOT_TYPE, e -> e.isAlive());
        for (TrinityBotEntity b : bots) {
            b.discard();
        }
        source.sendFeedback(() -> Text.literal("已移除 " + bots.size() + " 个 bot"), true);
        return bots.size();
    }

    // ---------------- skyra / abyss natives ----------------

    private static int summonSky(ServerCommandSource source, Integer x, Integer z) {
        ServerWorld world = source.getWorld();
        Vec3d pos = basePos(source);
        if (x != null && z != null) {
            pos = new Vec3d(x + 0.5, pos.y, z + 0.5);
        }
        double y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,
                (int) pos.x, (int) pos.z) + 28 + world.random.nextDouble() * 10;
        SkyraEntity e = new SkyraEntity(TrinityMod.SKYRA_TYPE, world);
        e.refreshPositionAndAngles(pos.x, y, pos.z, 0, 0);
        world.spawnEntity(e);
        source.sendFeedback(() -> Text.literal("[天穹] 一只 skyra 升入天空（它会巡游山脊、俯冲领空闯入者）"), true);
        return 1;
    }

    private static int summonAbyss(ServerCommandSource source, Integer x, Integer z) {
        ServerWorld world = source.getWorld();
        Vec3d pos = basePos(source);
        if (x != null && z != null) {
            pos = new Vec3d(x + 0.5, pos.y, z + 0.5);
        }
        // find the nearest deep valley (water) around the source
        FastTerrain ft = new FastTerrain(world.getSeed(), TrinityTerrain.HEIGHT);
        int bx = (int) pos.x, bz = (int) pos.z;
        int bestX = bx, bestZ = bz;
        double best = 1.0;
        for (int dx = -24; dx <= 24; dx += 3) {
            for (int dz = -24; dz <= 24; dz += 3) {
                double s01 = ft.surface01(bx + dx, bz + dz);
                if (s01 < best) {
                    best = s01;
                    bestX = bx + dx;
                    bestZ = bz + dz;
                }
            }
        }
        if (best > 0.22) {
            source.sendError(Text.literal("附近没有深水区（地表低于海平面才有深渊）"));
            return 0;
        }
        double surf = TrinityTerrain.MIN_Y + best * TrinityTerrain.HEIGHT;
        double y = surf + 2 + world.random.nextDouble() * 3;
        AbyssEntity e = new AbyssEntity(TrinityMod.ABYSS_TYPE, world);
        e.refreshPositionAndAngles(bestX + 0.5, y, bestZ + 0.5, 0, 0);
        world.spawnEntity(e);
        source.sendFeedback(() -> Text.literal("[深渊] 一只 abyss 从深谷浮起（它守护矿脉链、随潮汐升降）"), true);
        return 1;
    }

    private static int removeType(ServerCommandSource source, String kind) {
        ServerWorld world = source.getWorld();
        if (kind.equals("skyra")) {
            List<? extends SkyraEntity> all = world.getEntitiesByType(TrinityMod.SKYRA_TYPE, e -> e.isAlive());
            for (SkyraEntity e : all) e.discard();
            source.sendFeedback(() -> Text.literal("已放归天空 " + all.size() + " 只 skyra"), true);
            return all.size();
        }
        List<? extends AbyssEntity> all = world.getEntitiesByType(TrinityMod.ABYSS_TYPE, e -> e.isAlive());
        for (AbyssEntity e : all) e.discard();
        source.sendFeedback(() -> Text.literal("已沉入深渊 " + all.size() + " 只 abyss"), true);
        return all.size();
    }

    private static Vec3d basePos(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            return player.getPos().add(player.getRotationVector().multiply(3));
        }
        int sx = source.getWorld().getSpawnPos().getX();
        int sz = source.getWorld().getSpawnPos().getZ();
        return new Vec3d(sx + 0.5, source.getWorld().getTopY(
                net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, sx, sz) + 1, sz + 0.5);
    }

    // ---------------- bosses ----------------

    private static int summonBoss(ServerCommandSource source, String kind, Integer x, Integer z) {
        ServerWorld world = source.getWorld();
        Vec3d pos = basePos(source);
        if (x != null && z != null) {
            pos = new Vec3d(x + 0.5, pos.y, z + 0.5);
        }
        if (kind.equals("aurora")) {
            AuroraEntity e = new AuroraEntity(TrinityMod.AURORA_TYPE, world);
            double y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,
                    (int) pos.x, (int) pos.z) + 30 + world.random.nextDouble() * 8;
            e.refreshPositionAndAngles(pos.x, y, pos.z, 0, 0);
            world.spawnEntity(e);
            source.sendFeedback(() -> Text.literal("[天穹] 极光之龙降临——它盘旋在天空，等待战斗"), true);
        } else {
            AbyssKingEntity e = new AbyssKingEntity(TrinityMod.ABYSS_KING_TYPE, world);
            FastTerrain ft = new FastTerrain(world.getSeed(), TrinityTerrain.HEIGHT);
            int bx = (int) pos.x, bz = (int) pos.z;
            int bestX = bx, bestZ = bz;
            double best = 1.0;
            for (int dx = -32; dx <= 32; dx += 3) {
                for (int dz = -32; dz <= 32; dz += 3) {
                    double s01 = ft.surface01(bx + dx, bz + dz);
                    if (s01 < best) {
                        best = s01;
                        bestX = bx + dx;
                        bestZ = bz + dz;
                    }
                }
            }
            if (best > 0.25) {
                source.sendError(Text.literal("附近没有足够深的水域容纳深渊之心（请到海边或深水区）"));
                return 0;
            }
            double surf = TrinityTerrain.MIN_Y + best * TrinityTerrain.HEIGHT;
            e.refreshPositionAndAngles(bestX + 0.5, surf + 4, bestZ + 0.5, 0, 0);
            world.spawnEntity(e);
            source.sendFeedback(() -> Text.literal("[深渊] 深渊之心从深谷浮起——潮汐开始震颤"), true);
        }
        return 1;
    }

    private static int removeBoss(ServerCommandSource source, String kind) {
        ServerWorld world = source.getWorld();
        if (kind.equals("aurora")) {
            List<? extends AuroraEntity> all = world.getEntitiesByType(TrinityMod.AURORA_TYPE, e -> e.isAlive());
            for (AuroraEntity e : all) e.discard();
            source.sendFeedback(() -> Text.literal("已遣散 " + all.size() + " 条极光之龙"), true);
            return all.size();
        }
        List<? extends AbyssKingEntity> all = world.getEntitiesByType(TrinityMod.ABYSS_KING_TYPE, e -> e.isAlive());
        for (AbyssKingEntity e : all) e.discard();
        source.sendFeedback(() -> Text.literal("已遣返 " + all.size() + " 颗深渊之心"), true);
        return all.size();
    }
}
