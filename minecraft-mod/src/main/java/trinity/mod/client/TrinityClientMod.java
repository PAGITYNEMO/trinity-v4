package trinity.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import trinity.mod.TideClock;
import trinity.mod.TrinityMod;

/** Client-side wiring: client tide clock, seasonal sky/fog tint, crystal renderer. */
public final class TrinityClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TideClock.wireClient();
        SeasonalDimensionEffects.inject();
        BlockEntityRendererFactories.register(TrinityMod.CRYSTAL_ENTITY, TideCrystalRenderer::new);
        TrinityMod.LOGGER.info("[TRINITY v4.0] client wired: tide clock, seasonal sky, crystal renderer");
    }
}
