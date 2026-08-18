package trinity.mod.bot;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Identifier;

/**
 * Renders the bot with the vanilla player model and Steve's skin — it should
 * look exactly like another player standing in the world.
 */
public final class TrinityBotRenderer extends MobEntityRenderer<TrinityBotEntity, PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

    public TrinityBotRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public PlayerEntityRenderState createRenderState() {
        return new PlayerEntityRenderState();
    }

    @Override
    public Identifier getTexture(PlayerEntityRenderState state) {
        return TEXTURE;
    }
}
