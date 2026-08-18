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
 * Aurora renderer — a multi-segmented light-dragon: a snake of rings that
 * undulates through the air, translucent membrane wings, and a head with
 * glowing ember eyes. Its colours flow through the hue wheel over time like
 * an aurora (bound to the client tide clock), and the wings beat with the
 * breath phase.
 */
public final class AuroraRenderer extends EntityRenderer<AuroraEntity, AuroraRenderer.AuroraRenderState> {

    public static final class AuroraRenderState extends EntityRenderState {
        public float yaw;
    }

    public AuroraRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public AuroraRenderState createRenderState() {
        return new AuroraRenderState();
    }

    @Override
    public void updateRenderState(AuroraEntity entity, AuroraRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.yaw = entity.getYaw();
    }

    @Override
    public void render(AuroraRenderState state, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        double tD = TideClock.tClient();
        float t = (float) tD;
        float breath = (float) TideClock.breath(tD);
        float ph = state.age * 0.08f; // undulation phase
        float wing = 0.5f * breath;

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

        // ---- body: 9 rings along an undulating curve, head toward +Z ----
        int segs = 9;
        float[][] centers = new float[segs][3];
        float[] radii = new float[segs];
        for (int i = 0; i < segs; i++) {
            float tt = i / (float) (segs - 1);
            centers[i][0] = (float) Math.sin(tt * 4.2 + ph) * 0.6f * (1 - tt * 0.55f);
            centers[i][1] = (float) Math.sin(tt * 5.0 + ph * 0.6f) * 0.24f * (1 - tt * 0.5f)
                    + 0.14f * (float) Math.sin(tt * 3.1);
            centers[i][2] = tt * 8.0f - 4.0f;
            radii[i] = 0.22f + 0.3f * tt;
        }
        for (int i = 0; i < segs - 1; i++) {
            float hue = (tt(i) * 0.5f + t * 0.03f) % 1f;
            float[] col = hsv(hue, 0.8f, 1f);
            ringQuad(buf, m, centers[i], centers[i + 1], radii[i], radii[i + 1], col, 0.85f, 8);
        }

        // ---- tail tip ----
        {
            float hue = (t * 0.03f) % 1f;
            float[] col = hsv(hue, 0.8f, 1f);
            float[] c0 = centers[0];
            tri(buf, m, c0[0], c0[1] - 0.15f, c0[2] - 0.5f,
                    c0[0] - 0.3f, c0[1] - 0.2f, c0[2] - 1.3f,
                    c0[0], c0[1] - 0.05f, c0[2] - 1.5f, col[0], col[1], col[2], 0.9f);
            tri(buf, m, c0[0], c0[1] - 0.15f, c0[2] - 0.5f,
                    c0[0], c0[1] - 0.05f, c0[2] - 1.5f,
                    c0[0] + 0.3f, c0[1] - 0.2f, c0[2] - 1.3f, col[0], col[1], col[2], 0.9f);
        }

        // ---- wings: from the third ring, translucent membranes, beating ----
        {
            int wi = 3;
            float[] c = centers[wi];
            float[] hueA = {(tt(wi) * 0.5f + t * 0.03f + 0.12f) % 1f};
            float[] colA = hsv(hueA[0], 0.75f, 1f);
            float al = 0.55f;
            float cw = (float) Math.cos(wing), sw = (float) Math.sin(wing);
            // wing triangle (right side): root at body, tip outward, rear trailing
            for (int side = 0; side < 2; side++) {
                int sgn = side == 0 ? 1 : -1;
                float[][] pts = {
                        {c[0] + sgn * (radii[wi] * 0.6f), c[1] + 0.1f, c[2]},
                        {c[0] + sgn * 3.4f, c[1] + 0.25f, c[2] - 1.2f},
                        {c[0] + sgn * 2.6f, c[1] - 0.15f, c[2] - 2.8f},
                        {c[0] + sgn * (radii[wi] * 0.5f), c[1] - 0.05f, c[2] - 0.6f}};
                // beat around the body axis (Z through the ring centre): rotate xy
                for (float[] p : pts) {
                    float dx = p[0] - c[0], dy = p[1] - c[1];
                    p[0] = c[0] + dx * cw - dy * sw;
                    p[1] = c[1] + dx * sw + dy * cw;
                }
                tri(buf, m, pts[0][0], pts[0][1], pts[0][2],
                        pts[1][0], pts[1][1], pts[1][2],
                        pts[2][0], pts[2][1], pts[2][2], colA[0], colA[1], colA[2], al);
                tri(buf, m, pts[0][0], pts[0][1], pts[0][2],
                        pts[2][0], pts[2][1], pts[2][2],
                        pts[3][0], pts[3][1], pts[3][2], colA[0], colA[1], colA[2], al * 0.8f);
            }
        }

        // ---- head: at the front end ----
        {
            float[] c = centers[segs - 1];
            float hx = c[0], hy = c[1] + 0.2f, hz = c[2] + 1.0f;
            float[] col = hsv((t * 0.03f + 0.55f) % 1f, 0.65f, 1f);
            // skull box
            quad(buf, m, hx - 0.24f, hy - 0.18f, hz, hx - 0.24f, hy + 0.22f, hz,
                    hx - 0.24f, hy + 0.22f, hz + 0.75f, hx - 0.24f, hy - 0.18f, hz + 0.75f,
                    col[0] * 0.9f, col[1] * 0.9f, col[2] * 0.9f, 1f);
            quad(buf, m, hx + 0.24f, hy + 0.22f, hz, hx + 0.24f, hy - 0.18f, hz,
                    hx + 0.24f, hy - 0.18f, hz + 0.75f, hx + 0.24f, hy + 0.22f, hz + 0.75f,
                    col[0] * 0.9f, col[1] * 0.9f, col[2] * 0.9f, 1f);
            quad(buf, m, hx - 0.24f, hy + 0.22f, hz, hx + 0.24f, hy + 0.22f, hz,
                    hx + 0.24f, hy + 0.22f, hz + 0.75f, hx - 0.24f, hy + 0.22f, hz + 0.75f,
                    col[0], col[1], col[2], 1f);
            quad(buf, m, hx - 0.24f, hy - 0.18f, hz + 0.75f, hx + 0.24f, hy - 0.18f, hz + 0.75f,
                    hx + 0.24f, hy - 0.18f, hz, hx - 0.24f, hy - 0.18f, hz,
                    col[0] * 0.6f, col[1] * 0.6f, col[2] * 0.6f, 1f);
            quad(buf, m, hx + 0.24f, hy - 0.18f, hz + 0.75f, hx - 0.24f, hy - 0.18f, hz + 0.75f,
                    hx - 0.24f, hy + 0.22f, hz + 0.75f, hx + 0.24f, hy + 0.22f, hz + 0.75f,
                    col[0], col[1], col[2], 1f);
            // snout
            tri(buf, m, hx - 0.18f, hy - 0.1f, hz + 0.75f, hx + 0.18f, hy - 0.1f, hz + 0.75f,
                    hx, hy + 0.05f, hz + 1.25f, col[0] * 0.8f, col[1] * 0.8f, col[2] * 0.8f, 1f);
            // horns
            tri(buf, m, hx - 0.2f, hy + 0.22f, hz + 0.1f, hx - 0.3f, hy + 0.75f, hz - 0.15f,
                    hx - 0.05f, hy + 0.22f, hz + 0.05f, 0.95f, 0.95f, 1f, 1f);
            tri(buf, m, hx + 0.2f, hy + 0.22f, hz + 0.1f, hx + 0.05f, hy + 0.22f, hz + 0.05f,
                    hx + 0.3f, hy + 0.75f, hz - 0.15f, 0.95f, 0.95f, 1f, 1f);
            // eyes: embers
            tri(buf, m, hx - 0.2f, hy + 0.05f, hz + 0.62f, hx - 0.1f, hy + 0.12f, hz + 0.66f,
                    hx - 0.1f, hy, hz + 0.68f, 1f, 0.5f, 0.12f, 1f);
            tri(buf, m, hx + 0.2f, hy + 0.05f, hz + 0.62f, hx + 0.1f, hy, hz + 0.68f,
                    hx + 0.1f, hy + 0.12f, hz + 0.66f, 1f, 0.5f, 0.12f, 1f);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private float tt(int i) {
        return i / 8.0f;
    }

    /** Quad connecting two rings (8-sided prism section). */
    private void ringQuad(BufferBuilder buf, Matrix4f m,
                          float[] c1, float[] c2, float r1, float r2,
                          float[] col, float alpha, int sides) {
        for (int s = 0; s < sides; s++) {
            double a1 = s / (double) sides * Math.PI * 2;
            double a2 = (s + 1) / (double) sides * Math.PI * 2;
            float x11 = c1[0] + (float) Math.cos(a1) * r1, y11 = c1[1] + (float) Math.sin(a1) * r1;
            float x12 = c1[0] + (float) Math.cos(a2) * r1, y12 = c1[1] + (float) Math.sin(a2) * r1;
            float x21 = c2[0] + (float) Math.cos(a1) * r2, y21 = c2[1] + (float) Math.sin(a1) * r2;
            float x22 = c2[0] + (float) Math.cos(a2) * r2, y22 = c2[1] + (float) Math.sin(a2) * r2;
            tri(buf, m, x11, y11, c1[2], x12, y12, c1[2], x22, y22, c2[2], col[0], col[1], col[2], alpha);
            tri(buf, m, x11, y11, c1[2], x22, y22, c2[2], x21, y21, c2[2], col[0], col[1], col[2], alpha);
        }
    }

    /** HSV -> RGB, all channels 0..1. */
    private static float[] hsv(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        return switch (i % 6) {
            case 0 -> new float[]{v, t, p};
            case 1 -> new float[]{q, v, p};
            case 2 -> new float[]{p, v, t};
            case 3 -> new float[]{p, q, v};
            case 4 -> new float[]{t, p, v};
            default -> new float[]{v, p, q};
        };
    }

    private static void quad(BufferBuilder buf, Matrix4f m,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float r, float g, float b, float a) {
        tri(buf, m, x1, y1, z1, x2, y2, z2, x3, y3, z3, r, g, b, a);
        tri(buf, m, x1, y1, z1, x3, y3, z3, x4, y4, z4, r, g, b, a);
    }

    private static void tri(BufferBuilder buf, Matrix4f m,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float x3, float y3, float z3, float r, float g, float b, float a) {
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
        buf.vertex(m, x3, y3, z3).color(r, g, b, a);
    }
}
