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
 * Abyss King renderer — a colossal translucent veil: a pulsing dome with a
 * glowing core and a ring of thick tendrils that swing with the breath, each
 * on its own phase. The whole body brightens with the tide and the central
 * eye flares with every heartbeat.
 */
public final class AbyssKingRenderer extends EntityRenderer<AbyssKingEntity, AbyssKingRenderer.AbyssKingRenderState> {

    public static final class AbyssKingRenderState extends EntityRenderState {
        public float yaw;
    }

    public AbyssKingRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public AbyssKingRenderState createRenderState() {
        return new AbyssKingRenderState();
    }

    @Override
    public void updateRenderState(AbyssKingEntity entity, AbyssKingRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.yaw = entity.getYaw();
    }

    @Override
    public void render(AbyssKingRenderState state, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        double t = TideClock.tClient();
        double tide = Math.max(0, TideClock.tide(t));
        double breath = TideClock.breath(t);
        double hb = TideClock.heartbeat(t);

        float lum = (float) (0.45 + 0.55 * tide);
        float r = 0.12f * lum, g = 0.42f * lum, b = 0.9f * lum;
        float pulse = 1f + 0.05f * (float) tide; // body swells with the tide

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

        float rad = 2.3f * pulse, h = 1.9f * pulse;

        // ---- outer dome ----
        dome(buf, m, rad, h, r, g, b, 0.42f, 10, 7, 0.2f);
        // ---- inner glow ----
        dome(buf, m, rad * 0.6f, h * 0.6f, r * 1.4f, g * 1.2f, b, 0.25f, 8, 5, 0.15f);

        // ---- tendrils: thick curved tubes, each with its own breath phase ----
        int n = 9;
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2 + 0.3;
            double ph = breath * Math.PI + i * 1.31;
            float len = 2.2f + 0.7f * (float) (0.5 + 0.5 * Math.sin(ph));
            float sway = (float) Math.sin(ph * 0.6) * 0.7f;
            float baseX = (float) (Math.cos(a) * rad * 0.72);
            float baseZ = (float) (Math.sin(a) * rad * 0.72);
            float[][] pts = {
                    {baseX, -0.3f, baseZ},
                    {baseX + sway * 0.3f, -0.3f - len * 0.3f, baseZ + sway * 0.2f},
                    {baseX + sway * 0.7f, -0.3f - len * 0.65f, baseZ + sway * 0.45f},
                    {baseX + sway, -0.3f - len, baseZ + sway * 0.6f}};
            float rw = 0.34f, r0 = 0.22f;
            tube(buf, m, pts, new float[]{rw, rw * 0.75f, r0, r0 * 0.5f}, r, g, b, 0.4f, 6);
        }

        // ---- the eye: flares with every heartbeat ----
        float er = 0.45f + 0.55f * (float) hb;
        float ey = h * 0.5f;
        float es = 0.42f * (1f + 0.25f * (float) hb);
        tri(buf, m, 0, ey + es, rad * 0.9f, es * 0.8f, ey, rad * 0.9f, 0, ey, rad * 0.9f + es,
                0.9f * er, 1f * er, 1f * er, 0.95f);
        tri(buf, m, 0, ey - es, rad * 0.9f, 0, ey, rad * 0.9f + es, es * 0.8f, ey, rad * 0.9f,
                0.9f * er, 1f * er, 1f * er, 0.95f);
        tri(buf, m, 0, ey + es, rad * 0.9f, 0, ey, rad * 0.9f - es, -es * 0.8f, ey, rad * 0.9f,
                0.9f * er, 1f * er, 1f * er, 0.95f);
        tri(buf, m, 0, ey - es, rad * 0.9f, -es * 0.8f, ey, rad * 0.9f, 0, ey, rad * 0.9f - es,
                0.9f * er, 1f * er, 1f * er, 0.95f);

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

    /** A tapered polygonal tube along the given centreline points. */
    private void tube(BufferBuilder buf, Matrix4f m, float[][] pts, float[] radii,
                      float r, float g, float b, float alpha, int sides) {
        for (int i = 0; i < pts.length - 1; i++) {
            float[] p1 = pts[i], p2 = pts[i + 1];
            float r1 = radii[i], r2 = radii[i + 1];
            for (int s = 0; s < sides; s++) {
                double a1 = s / (double) sides * Math.PI * 2;
                double a2 = (s + 1) / (double) sides * Math.PI * 2;
                float x11 = p1[0] + (float) Math.cos(a1) * r1, y11 = p1[1] + (float) Math.sin(a1) * r1;
                float x12 = p1[0] + (float) Math.cos(a2) * r1, y12 = p1[1] + (float) Math.sin(a2) * r1;
                float x21 = p2[0] + (float) Math.cos(a1) * r2, y21 = p2[1] + (float) Math.sin(a1) * r2;
                float x22 = p2[0] + (float) Math.cos(a2) * r2, y22 = p2[1] + (float) Math.sin(a2) * r2;
                tri(buf, m, x11, y11, p1[2], x12, y12, p1[2], x22, y22, p2[2], r, g, b, alpha);
                tri(buf, m, x11, y11, p1[2], x22, y22, p2[2], x21, y21, p2[2], r, g, b, alpha);
            }
        }
    }

    private static void tri(BufferBuilder buf, Matrix4f m,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float x3, float y3, float z3, float r, float g, float b, float a) {
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
        buf.vertex(m, x3, y3, z3).color(r, g, b, a);
    }
}
