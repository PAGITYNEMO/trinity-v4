package trinity.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trinity.mod.bot.TrinityBotCommand;
import trinity.mod.bot.TrinityBotEntity;

/**
 * TRINITY v4.0 entry point.
 *
 * Registers:
 *  - the four-pole chunk generator codec (root registry); the world preset is
 *    a datapack JSON (data/trinity-noise/worldgen/world_preset/trinity.json)
 *  - the Trinity Crystal block + item + block entity (light-curtain made
 *    visible; light pulses with the server tide clock, the renderer animates
 *    from the client clock with per-crystal phase and seasonal color)
 *  - the four-pole companion bot entity (PAGITY/NEMO/AXIS/RAMANUJAN players)
 *  - the tide clock (server tick) and the seasonal weather policy
 */
public final class TrinityMod implements ModInitializer {
    public static final String MOD_ID = "trinity-noise";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static TrinityCrystalBlock CRYSTAL;
    public static BlockEntityType<TideCrystalBlockEntity> CRYSTAL_ENTITY;
    public static EntityType<TrinityBotEntity> BOT_TYPE;

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(Registries.CHUNK_GENERATOR, id("trinity"), TrinityChunkGenerator.CODEC);
        LOGGER.info("[TRINITY v4.0] four-pole chunk generator registered: {} "
                + "(world preset: data/trinity-noise/worldgen/world_preset/trinity.json)", id("trinity"));

        CRYSTAL = new TrinityCrystalBlock(
                AbstractBlock.Settings.create()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, id("trinity_crystal")))
                        .strength(2.0f, 6.0f)
                        .requiresTool()
                        .nonOpaque()
                        .luminance(state -> 7 + state.get(TrinityCrystalBlock.PULSE)));
        Registry.register(Registries.BLOCK, id("trinity_crystal"), CRYSTAL);
        Registry.register(Registries.ITEM, id("trinity_crystal"),
                new BlockItem(CRYSTAL, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, id("trinity_crystal")))));
        CRYSTAL_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("tide_crystal"),
                FabricBlockEntityTypeBuilder.create(TideCrystalBlockEntity::new, CRYSTAL).build());
        LOGGER.info("[TRINITY v4.0] trinity crystal + block entity registered: {} "
                + "(/give @s trinity-noise:trinity_crystal)", id("trinity_crystal"));

        // Four-pole companion bot
        BOT_TYPE = Registry.register(Registries.ENTITY_TYPE, id("trinity_bot"),
                EntityType.Builder.create(TrinityBotEntity::new, SpawnGroup.MISC)
                        .dimensions(0.6f, 1.8f)
                        .maxTrackingRange(64)
                        .trackingTickInterval(2)
                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, id("trinity_bot"))));
        FabricDefaultAttributeRegistry.register(BOT_TYPE, TrinityBotEntity.createBotAttributes());
        TrinityBotCommand.register();
        LOGGER.info("[TRINITY v4.0] trinity bot registered: {} "
                + "(/trinity bot summon PAGITY|NEMO|AXIS|RAMANUJAN)", id("trinity_bot"));

        TideClock.wire();
        WeatherPulse.wire();
    }
}
