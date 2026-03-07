package su.firemine.fmvisuals.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.opengl.GL11;
import su.firemine.fmvisuals.mixin.ClickableWidgetAccessor;

import java.util.List;
import java.util.Random;

public final class FMRenderer extends DrawableHelper {

    private FMRenderer() {}

    // ── Particle network state ─────────────────────────────────────────────
    private static final int N = 60;
    private static final float[] PX  = new float[N];
    private static final float[] PY  = new float[N];
    private static final float[] PVX = new float[N];
    private static final float[] PVY = new float[N];
    private static long lastNs = 0;

    static {
        Random rng = new Random(0xFCA_BCDEL);
        for (int i = 0; i < N; i++) {
            PX[i] = rng.nextFloat();
            PY[i] = rng.nextFloat();
            double a = rng.nextDouble() * Math.PI * 2;
            float  s = 0.025f + rng.nextFloat() * 0.025f; // normalized units/sec
            PVX[i] = (float)(Math.cos(a) * s);
            PVY[i] = (float)(Math.sin(a) * s);
        }
    }

    // ── Public fill wrapper ────────────────────────────────────────────────
    public static void rect(MatrixStack m, int x1, int y1, int x2, int y2, int color) {
        fill(m, x1, y1, x2, y2, color);
    }

    // ── Particle network: update + draw ───────────────────────────────────
    public static void drawNetwork(MatrixStack matrices, int W, int H) {
        // --- update positions ---
        long now = System.nanoTime();
        float dt = (lastNs == 0) ? 0f : Math.min((now - lastNs) / 1_000_000_000f, 0.1f);
        lastNs = now;

        for (int i = 0; i < N; i++) {
            PX[i] += PVX[i] * dt;
            PY[i] += PVY[i] * dt;
            if (PX[i] < 0f) { PX[i] = 0f; PVX[i] =  Math.abs(PVX[i]); }
            if (PX[i] > 1f) { PX[i] = 1f; PVX[i] = -Math.abs(PVX[i]); }
            if (PY[i] < 0f) { PY[i] = 0f; PVY[i] =  Math.abs(PVY[i]); }
            if (PY[i] > 1f) { PY[i] = 1f; PVY[i] = -Math.abs(PVY[i]); }
        }

        // --- draw lines between close particles ---
        float maxDist = W * 0.18f;

        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(1.0f);

        Matrix4f matrix = matrices.peek().getModel();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf  = tess.getBuffer();
        buf.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                float dx   = (PX[i] - PX[j]) * W;
                float dy   = (PY[i] - PY[j]) * H;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < maxDist) {
                    int alpha = (int)((1f - dist / maxDist) * 70);
                    float x1 = PX[i] * W, y1 = PY[i] * H;
                    float x2 = PX[j] * W, y2 = PY[j] * H;
                    buf.vertex(matrix, x1, y1, 0f).color(168, 85, 247, alpha).next();
                    buf.vertex(matrix, x2, y2, 0f).color(168, 85, 247, alpha).next();
                }
            }
        }
        tess.draw();

        RenderSystem.enableTexture();

        // --- draw particle dots ---
        for (int i = 0; i < N; i++) {
            int x = (int)(PX[i] * W);
            int y = (int)(PY[i] * H);
            fill(matrices, x - 1, y - 1, x + 2, y + 2, 0x88a855f7);
        }
    }

    // ── Rounded fill (radius 2px) ──────────────────────────────────────────
    private static void fmFill(MatrixStack m, int x, int y, int w, int h, int c) {
        fill(m, x + 2, y,     x + w - 2, y + h,     c);
        fill(m, x + 1, y + 1, x + w - 1, y + h - 1, c);
        fill(m, x,     y + 2, x + w,     y + h - 2, c);
    }

    // ── Rounded border ─────────────────────────────────────────────────────
    private static void fmBorder(MatrixStack m, int x, int y, int w, int h, int c) {
        fill(m, x + 2,     y,         x + w - 2,   y + 1,         c);
        fill(m, x + 2,     y + h - 1, x + w - 2,   y + h,         c);
        fill(m, x,         y + 2,     x + 1,       y + h - 2,     c);
        fill(m, x + w - 1, y + 2,     x + w,       y + h - 2,     c);
        fill(m, x + 1,     y + 1,     x + 2,       y + 2,         c);
        fill(m, x + w - 2, y + 1,     x + w - 1,   y + 2,         c);
        fill(m, x + 1,     y + h - 2, x + 2,       y + h - 1,     c);
        fill(m, x + w - 2, y + h - 2, x + w - 1,   y + h - 1,     c);
    }

    // ── Restyle buttons ────────────────────────────────────────────────────
    public static void drawButtons(MatrixStack matrices, List<ClickableWidget> buttons,
                                    TextRenderer tr, int mouseX, int mouseY) {
        for (ClickableWidget btn : buttons) {
            if (!btn.visible) continue;
            ClickableWidgetAccessor acc = (ClickableWidgetAccessor) btn;
            int bw = acc.getWidthPx(), bh = acc.getHeightPx();
            if (bw < 40) continue;

            boolean hov = mouseX >= btn.x && mouseX < btn.x + bw
                       && mouseY >= btn.y && mouseY < btn.y + bh;

            fmFill(matrices, btn.x, btn.y, bw, bh, hov ? 0xBB180638 : 0xAA0b0222);
            fmBorder(matrices, btn.x, btn.y, bw, bh, hov ? 0xFFa855f7 : 0xFF3d1468);
            if (hov)
                fill(matrices, btn.x + 2, btn.y + 1,
                     btn.x + bw - 2, btn.y + 2, 0x18ffffff);

            Text msg = btn.getMessage();
            if (msg != null) {
                int tw = tr.getWidth(msg);
                tr.drawWithShadow(matrices, msg,
                    btn.x + (bw - tw) / 2.0f,
                    btn.y + (bh - 8) / 2.0f,
                    hov ? 0xFFFFFFFF : 0xFFCCAAFF);
            }
        }
    }
}
