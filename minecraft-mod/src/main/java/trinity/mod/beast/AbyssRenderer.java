package trinity.mod.beast;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import trinity.mod.TideClock;

/**
 * Abyss renderer — a filled, translucent deep-sea veil: a soft dome with a
 * glowing core, whose brightness follows the tide and whose tendrils pulse
 * with the breath phase. The eye in the centre glows brighter when the
 * heartbeat runs, so the fauna of the deep glows with the same clock as the
 * crystals.
 */
public final class AbyssRenderer extends EntityRenderer<AbyssEntity, AbyssRenderer.AbyssRenderState> {

    public static final class AbyssRenderState extends EntityRenderState {
        public float yaw;
    }

    public AbyssRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public AbyssRenderState createRenderState() {
        return new AbyssRenderState();
    }

    @Override
    public void updateRenderState(AbyssEntity entity, AbyssRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.yaw = entity.getYaw();
    }

    @Override
    public void render(AbyssRenderState state, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        double t = TideClock.tClient();
        double tide = Math.max(0, TideClock.tide(t));
        double breath = TideClock.breath(t);
        double hb = TideClock.heartbeat(t);

        float lum = (float) (0.4 + 0.6 * tide);
        float r = 0.18f * lum, g = 0.5f * lum, b = 0.9f * lum;

        // NOTE: EntityRenderDispatcher already applied the entity position to
        // the matrices; only rotate locally.
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-state.yaw));
        Matrix4f m = matrices.peek().getPositionMatrix();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        BufferBuilder buf = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        float h = 0.85f, rad = 0.55f;
        // ---- dome: filled hemisphere (two layers: outer veil + inner glow) ----
        dome(buf, m, rad, h, r, g, b, 0.38f, 8, 5, 0.16f);
        dome(buf, m, rad * 0.62f, h * 0.62f, r * 1.3f, g * 1.15f, b * 1.0f, 0.22f, 6, 4, 0.12f);

        // ---- tendrils: narrow tapering ribbons, pulsing with the breath ----
        int n = 6;
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2 + 0.26;
            float bx = (float) (Math.cos(a) * rad * 0.8);
            float bz = (float) (Math.sin(a) * rad * 0.8);
            double ph = breath * Math.PI + i * 1.7;
            float len = 0.55f + 0.4f * (float) (0.5 + 0.5 * Math.sin(ph));
            float sway = (float) Math.sin(ph * 0.7) * 0.2f;
            float ta = 0.16f * (float) (0.5 + 0.5 * tide);
            tendril(buf, m, bx, bz, sway, len, r, g, b, ta);
        }

        // ---- the eye: glows with the heartbeat ----
        float er = 0.5f + 0.5f * (float) hb;
        float ey = h * 0.55f;
        float es = 0.10f * (1f + 0.3f * (float) hb);
        tri(buf, m, 0, ey + es, 0, es, ey, 0, 0, ey, es, 0.85f * er, 0.95f * er, 1f * er, 0.95f);
        tri(buf, m, 0, ey - es, 0, 0, ey, es, es, ey, 0, 0.85f * er, 0.95f * er, 1f * er, 0.95f);
        tri(buf, m, 0, ey + es, 0, 0, ey, -es, -es, ey, 0, 0.85f * er, 0.95f * er, 1f * er, 0.95f);
        tri(buf, m, 0, ey - es, 0, -es, ey, 0, 0, ey, -es, 0.85f * er, 0.95f * er, 1f * er, 0.95f);

        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private void dome(BufferBuilder buf, Matrix4f m, float rad, float h,
                      float r, float g, float b, float alpha, int segs, int rings, float yOff) {
        for (int lat = 0; lat < rings; lat++) {
            double a1 = lat / (double) rings * Math.PI / 2;
            double a2 = (lat + 1) / (double) rings * Math.PI / 2;
            float y1 = h * (float) Math.cos(a1) - yOff;
            float y2 = h * (float) Math.cos(a2) - yOff;
            float rr1 = rad * (float) Math.sin(a1);
            float rr2 = rad * (float) Math.sin(a2);
            for (int s = 0; s < segs; s++) {
                double t1 = s / (double) segs * Math.PI * 2;
                double t2 = (s + 1) / (double) segs * Math.PI * 2;
                float x11 = (float) (Math.cos(t1) * rr1), z11 = (float) (Math.sin(t1) * rr1);
                float x12 = (float) (Math.cos(t2) * rr1), z12 = (float) (Math.sin(t2) * rr1);
                float x21 = (float) (Math.cos(t1) * rr2), z21 = (float) (Math.sin(t1) * rr2);
                float x22 = (float) (Math.cos(t2) * rr2), z22 = (float) (Math.sin(t2) * rr2);
                tri(buf, m, x11, y1, z11, x12, y1, z12, x22, y2, z22, r, g, b, alpha);
                tri(buf, m, x11, y1, z11, x22, y2, z22, x21, y2, z21, r, g, b, alpha);
            }
        }
    }

    private void tendril(BufferBuilder buf, Matrix4f m,
                         float bx, float bz, float sway, float len,
                         float r, float g, float b, float a) {
        float w = 0.045f;
        float x1 = bx + sway, x2 = bx - sway;
        float y0 = -0.04f, y1 = -len;
        tri(buf, m, x1 - w, y0, bz, x1 + w, y0, bz, x1, y1, bz, r, g, b, a);
        tri(buf, m, x1 + w, y0, bz, x1 - w, y0, bz, x1, y1, bz, r, g, b, a * 0.5f);
    }

    private static void tri(BufferBuilder buf, Matrix4f m,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float x3, float y3, float z3, float r, float g, float b, float a) {
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
        buf.vertex(m, x3, y3, z3).color(r, g, b, a);
    }
}
