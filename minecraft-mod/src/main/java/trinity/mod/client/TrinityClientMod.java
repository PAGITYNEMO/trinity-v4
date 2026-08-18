package trinity.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import trinity.mod.TideClock;
import trinity.mod.TrinityMod;
import trinity.mod.beast.AbyssKingRenderer;
import trinity.mod.beast.AbyssRenderer;
import trinity.mod.beast.AuroraRenderer;
import trinity.mod.beast.SkyraRenderer;
import trinity.mod.bot.TrinityBotRenderer;

/** Client-side wiring: client tide clock, seasonal sky/fog tint, crystal + bot renderers. */
public final class TrinityClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TideClock.wireClient();
        SeasonalDimensionEffects.inject();
        BlockEntityRendererFactories.register(TrinityMod.CRYSTAL_ENTITY, TideCrystalRenderer::new);
        EntityRendererRegistry.register(TrinityMod.BOT_TYPE, TrinityBotRenderer::new);
        EntityRendererRegistry.register(TrinityMod.SKYRA_TYPE, SkyraRenderer::new);
        EntityRendererRegistry.register(TrinityMod.ABYSS_TYPE, AbyssRenderer::new);
        EntityRendererRegistry.register(TrinityMod.AURORA_TYPE, AuroraRenderer::new);
        EntityRendererRegistry.register(TrinityMod.ABYSS_KING_TYPE, AbyssKingRenderer::new);
        TrinityMod.LOGGER.info("[TRINITY v4.0] client wired: tide clock, seasonal sky, crystal + bot + skyra + abyss + aurora + abyss_king renderers");
    }
}
