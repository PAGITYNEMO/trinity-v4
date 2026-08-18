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
import trinity.mod.TrinityMod;

import java.util.List;

/** /trinity bot summon <pole> [name] | list | remove — the four-pole companion. */
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
                                    .executes(ctx -> remove(ctx.getSource())))));
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
}
