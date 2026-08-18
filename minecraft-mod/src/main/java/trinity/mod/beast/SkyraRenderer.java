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
import trinity.mod.client.SeasonalDimensionEffects;

/**
 * Skyra renderer — a filled, stylised wyvern: a solid body with translucent
 * membrane wings whose beat follows the breath phase (0.15 Hz) and whose
 * membrane colour follows the season, so the sky fauna breathes with the same
 * clock as the light-curtain crystals. Pseudo-lit: top faces brighter than
 * the underside.
 */
public final class SkyraRenderer extends EntityRenderer<SkyraEntity, SkyraRenderer.SkyraRenderState> {

    public static final class SkyraRenderState extends EntityRenderState {
        public float yaw;
    }

    public SkyraRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public SkyraRenderState createRenderState() {
        return new SkyraRenderState();
    }

    @Override
    public void updateRenderState(SkyraEntity entity, SkyraRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.yaw = entity.getYaw();
    }

    @Override
    public void render(SkyraRenderState state, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        double t = TideClock.tClient();
        float breath = (float) TideClock.breath(t);
        float[] col = SeasonalDimensionEffects.seasonColor(t);
        float wing = 0.55f * breath; // wing beat bound to the breath phase

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

        // ---- body: tapered box from tail (0,-0.16,-0.3) to head (0,0.16,0.9) ----
        quad(buf, m,
                0.13f, 0.16f, 0.9f,  0.13f, 0.16f, -0.3f,
                0.13f, -0.16f, -0.3f, 0.13f, -0.16f, 0.9f,
                0.32f, 0.38f, 0.5f, 0.9f);          // +X side
        quad(buf, m,
                -0.13f, -0.16f, 0.9f, -0.13f, -0.16f, -0.3f,
                -0.13f, 0.16f, -0.3f, -0.13f, 0.16f, 0.9f,
                0.32f, 0.38f, 0.5f, 0.9f);          // -X side
        quad(buf, m,
                -0.13f, 0.16f, 0.9f, 0.13f, 0.16f, 0.9f,
                0.13f, 0.16f, -0.3f, -0.13f, 0.16f, -0.3f,
                0.44f, 0.52f, 0.66f, 1.0f);         // top (lit)
        quad(buf, m,
                -0.13f, -0.16f, -0.3f, 0.13f, -0.16f, -0.3f,
                0.13f, -0.16f, 0.9f, -0.13f, -0.16f, 0.9f,
                0.22f, 0.27f, 0.38f, 1.0f);         // belly (dark)
        quad(buf, m,
                -0.13f, -0.16f, 0.9f, 0.13f, -0.16f, 0.9f,
                0.13f, 0.16f, 0.9f, -0.13f, 0.16f, 0.9f,
                0.4f, 0.47f, 0.6f, 1.0f);           // front (head end)
        quad(buf, m,
                0.13f, -0.16f, -0.3f, -0.13f, -0.16f, -0.3f,
                -0.13f, 0.16f, -0.3f, 0.13f, 0.16f, -0.3f,
                0.25f, 0.3f, 0.42f, 1.0f);          // back (tail end)

        // ---- head: small box + beak ----
        quad(buf, m, 0.09f, 0.09f, 1.14f, 0.09f, 0.09f, 0.9f,
                0.09f, -0.05f, 0.9f, 0.09f, -0.05f, 1.14f, 0.44f, 0.52f, 0.66f, 1f);
        quad(buf, m, -0.09f, -0.05f, 1.14f, -0.09f, -0.05f, 0.9f,
                -0.09f, 0.09f, 0.9f, -0.09f, 0.09f, 1.14f, 0.44f, 0.52f, 0.66f, 1f);
        quad(buf, m, -0.09f, 0.09f, 1.14f, 0.09f, 0.09f, 1.14f,
                0.09f, 0.09f, 0.9f, -0.09f, 0.09f, 0.9f, 0.5f, 0.6f, 0.75f, 1f);
        quad(buf, m, -0.09f, -0.05f, 0.9f, 0.09f, -0.05f, 0.9f,
                0.09f, -0.05f, 1.14f, -0.09f, -0.05f, 1.14f, 0.25f, 0.3f, 0.42f, 1f);
        // beak (darker, pointing forward)
        tri(buf, m, 0.0f, 0.0f, 1.14f, 0.06f, -0.02f, 1.3f, -0.06f, -0.02f, 1.3f,
                0.5f, 0.42f, 0.3f, 1f);

        // ---- tail: thin taper with fork ----
        quad(buf, m, 0.05f, -0.06f, -0.25f, 0.05f, -0.06f, -0.8f,
                -0.05f, -0.06f, -0.8f, -0.05f, -0.06f, -0.25f,
                0.3f, 0.36f, 0.48f, 1f);            // top
        quad(buf, m, -0.05f, -0.12f, -0.8f, 0.05f, -0.12f, -0.8f,
                0.05f, -0.12f, -0.25f, -0.05f, -0.12f, -0.25f,
                0.2f, 0.24f, 0.34f, 1f);            // bottom
        quad(buf, m, 0.05f, -0.06f, -0.25f, 0.05f, -0.12f, -0.25f,
                0.05f, -0.12f, -0.8f, 0.05f, -0.06f, -0.8f,
                0.3f, 0.36f, 0.48f, 1f);            // +X
        quad(buf, m, -0.05f, -0.06f, -0.8f, -0.05f, -0.12f, -0.8f,
                -0.05f, -0.12f, -0.25f, -0.05f, -0.06f, -0.25f,
                0.3f, 0.36f, 0.48f, 1f);            // -X
        tri(buf, m, 0.0f, -0.09f, -0.8f, -0.14f, -0.09f, -1.0f, 0.0f, -0.09f, -1.15f,
                0.34f, 0.4f, 0.52f, 1f);
        tri(buf, m, 0.0f, -0.09f, -0.8f, 0.0f, -0.09f, -1.15f, 0.14f, -0.09f, -1.0f,
                0.34f, 0.4f, 0.52f, 1f);

        // ---- wings: translucent membranes, beating around the X axis ----
        drawWing(buf, m, wing, col, 1);
        drawWing(buf, m, wing, col, -1);
        tri(buf, m, 0.09f, 0.07f, 1.1f, 0.11f, 0.1f, 1.13f, 0.13f, 0.05f, 1.11f,
                1f, 0.55f, 0.15f, 1f);
        tri(buf, m, -0.09f, 0.07f, 1.1f, -0.13f, 0.05f, 1.11f, -0.11f, 0.1f, 1.13f,
                1f, 0.55f, 0.15f, 1f);

        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    /** One translucent wing: shoulder -> tip -> trailing edge, rotated around X by wing. */
    private void drawWing(BufferBuilder buf, Matrix4f m, float wing, float[] col, int mirror) {
        float s = (float) Math.sin(wing), c = (float) Math.cos(wing);
        // triangle 1 (leading edge): shoulder, tip, mid-back (x mirrored for the other side)
        float[][] a = rotX(new float[][]{
                {0.14f * mirror, 0.06f, 0.2f}, {2.1f * mirror, 0.12f, 0.1f}, {0.6f * mirror, -0.02f, -0.1f}}, s, c);
        // triangle 2 (trailing edge): tip, back, shoulder-back
        float[][] b = rotX(new float[][]{
                {2.1f * mirror, 0.12f, 0.1f}, {0.55f * mirror, -0.1f, -0.22f}, {0.14f * mirror, 0.06f, 0.2f}}, s, c);
        float al = 0.62f;
        tri(buf, m, a[0][0], a[0][1], a[0][2], a[1][0], a[1][1], a[1][2], a[2][0], a[2][1], a[2][2],
                col[0], col[1], col[2], al);
        tri(buf, m, b[0][0], b[0][1], b[0][2], b[1][0], b[1][1], b[1][2], b[2][0], b[2][1], b[2][2],
                col[0] * 0.85f, col[1] * 0.85f, col[2] * 0.85f, al * 0.8f);
    }

    private static float[][] rotX(float[][] p, float s, float c) {
        for (float[] v : p) {
            float y = v[1] * c - v[2] * s;
            float z = v[1] * s + v[2] * c;
            v[1] = y;
            v[2] = z;
        }
        return p;
    }

    /** A quad as two triangles (the buffer runs in TRIANGLES mode). */
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
