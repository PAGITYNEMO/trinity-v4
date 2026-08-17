package trinity.mod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import trinity.mod.TideCrystalBlockEntity;

/**
 * Renders the Trinity Crystal as a glowing wireframe octahedron whose:
 *  - size pulses with the local tide (heartbeat x season amplitude)
 *  - vertical bob follows the breath
 *  - rotation follows the breath phase
 *  - color follows the season (new leaf green / spring-tide blue / autumn
 *    orange / frost pale)
 * Each crystal's phase offset makes the cave breathe as a travelling wave.
 */
public final class TideCrystalRenderer implements BlockEntityRenderer<TideCrystalBlockEntity> {

    public TideCrystalRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(TideCrystalBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        double tide = Math.max(0, be.localTide());
        double breath = be.localBreath();
        float[] col = SeasonalDimensionEffects.seasonColor(be.localSeason());

        matrices.push();
        matrices.translate(0.5, 0.5 + 0.25 * Math.sin(breath * Math.PI), 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) (breath * Math.PI * 0.5)));
        Matrix4f m = matrices.peek().getPositionMatrix();

        float r = 0.28f + (float) (0.55 * tide);
        float h = r * 1.7f;
        float[][] v = {
                {0, h, 0}, {r, 0, 0}, {0, 0, r}, {-r, 0, 0}, {0, 0, -r}, {0, -h, 0}};
        int[][] edges = {
                {0, 1}, {0, 2}, {0, 3}, {0, 4},
                {5, 1}, {5, 2}, {5, 3}, {5, 4},
                {1, 2}, {2, 3}, {3, 4}, {4, 1}};

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();

        BufferBuilder buf = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        float alpha = (float) (0.35 + 0.65 * tide);
        for (int[] e : edges) {
            buf.vertex(m, v[e[0]][0], v[e[0]][1], v[e[0]][2]).color(col[0], col[1], col[2], alpha);
            buf.vertex(m, v[e[1]][0], v[e[1]][1], v[e[1]][2]).color(col[0], col[1], col[2], alpha);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }
}
