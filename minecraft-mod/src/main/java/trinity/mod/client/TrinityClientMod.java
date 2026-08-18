package trinity.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import trinity.mod.TideClock;
import trinity.mod.TrinityMod;
import trinity.mod.bot.TrinityBotRenderer;

/** Client-side wiring: client tide clock, seasonal sky/fog tint, crystal + bot renderers. */
public final class TrinityClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TideClock.wireClient();
        SeasonalDimensionEffects.inject();
        BlockEntityRendererFactories.register(TrinityMod.CRYSTAL_ENTITY, TideCrystalRenderer::new);
        EntityRendererRegistry.register(TrinityMod.BOT_TYPE, TrinityBotRenderer::new);
        TrinityMod.LOGGER.info("[TRINITY v4.0] client wired: tide clock, seasonal sky, crystal + bot renderers");
    }
}
