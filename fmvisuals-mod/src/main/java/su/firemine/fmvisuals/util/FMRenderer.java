package su.firemine.fmvisuals.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
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
    private static final int NETWORK_R = 96;
    private static final int NETWORK_G = 170;
    private static final int NETWORK_B = 214;
    private static final String SETTINGS_ICON_ID = "__fm_settings_icon__";
    private static final String QUIT_ICON_ID = "__fm_quit_icon__";
    private static final int BUTTON_FILL = 0xA21D2127;
    private static final int BUTTON_FILL_HOVER = 0xB8272C33;
    private static final int BUTTON_EDGE = 0x5E737E8A;
    private static final int BUTTON_EDGE_HOVER = 0x9CDDE5EC;
    private static final int BUTTON_GLOW = 0x102A3138;
    private static final int COMPACT_FILL = 0xAE1A1E23;
    private static final int COMPACT_FILL_HOVER = 0xBC22272D;
    private static final int COMPACT_EDGE = 0x6A7A8691;

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
                    int alpha = (int)((1f - dist / maxDist) * 42);
                    float x1 = PX[i] * W, y1 = PY[i] * H;
                    float x2 = PX[j] * W, y2 = PY[j] * H;
                    buf.vertex(matrix, x1, y1, 0f).color(NETWORK_R, NETWORK_G, NETWORK_B, alpha).next();
                    buf.vertex(matrix, x2, y2, 0f).color(NETWORK_R, NETWORK_G, NETWORK_B, alpha).next();
                }
            }
        }
        tess.draw();

        RenderSystem.enableTexture();

        // --- draw particle dots ---
        for (int i = 0; i < N; i++) {
            int x = (int)(PX[i] * W);
            int y = (int)(PY[i] * H);
            fill(matrices, x - 2, y - 2, x + 3, y + 3, 0x10273A48);
            fill(matrices, x - 1, y - 1, x + 2, y + 2, 0xA060AAD6);
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
            drawWidgetButton(matrices, btn, tr, mouseX, mouseY);
        }
    }

    public static void drawWidgetButton(MatrixStack matrices, ClickableWidget btn,
                                        TextRenderer tr, int mouseX, int mouseY) {
        ClickableWidgetAccessor acc = (ClickableWidgetAccessor) btn;
        int bw = acc.getWidthPx();
        int bh = acc.getHeightPx();
        boolean hov = mouseX >= btn.x && mouseX < btn.x + bw
            && mouseY >= btn.y && mouseY < btn.y + bh;

        if (bw < 40) {
            fmFill(matrices, btn.x, btn.y, bw, bh, hov ? COMPACT_FILL_HOVER : COMPACT_FILL);
            fmBorder(matrices, btn.x, btn.y, bw, bh, hov ? BUTTON_EDGE_HOVER : COMPACT_EDGE);
        } else {
            fill(matrices, btn.x - 2, btn.y - 2, btn.x + bw + 2, btn.y + bh + 2, 0x0C000000);
            fmFill(matrices, btn.x, btn.y, bw, bh, hov ? BUTTON_FILL_HOVER : BUTTON_FILL);
            fmBorder(matrices, btn.x, btn.y, bw, bh, hov ? BUTTON_EDGE_HOVER : BUTTON_EDGE);
            fill(matrices, btn.x + 2, btn.y + 1, btn.x + bw - 2, btn.y + 3,
                hov ? 0x1BFFFFFF : 0x12FFFFFF);
            fill(matrices, btn.x + 2, btn.y + bh - 3, btn.x + bw - 2, btn.y + bh - 1,
                hov ? BUTTON_GLOW : 0x12000000);
        }

        Text msg = btn.getMessage();
        if (msg == null) return;

        String label = msg.getString();
        if (SETTINGS_ICON_ID.equals(label)) {
            drawSettingsIcon(matrices, btn.x, btn.y, bw, bh, hov ? 0xFFF1F4F7 : 0xFFD2D7DD);
            return;
        }
        if (QUIT_ICON_ID.equals(label)) {
            drawQuitIcon(matrices, btn.x, btn.y, bw, bh, hov ? 0xFFF1F4F7 : 0xFFD2D7DD);
            return;
        }

        int tw = tr.getWidth(msg);
        if (bw < 40 && tw > bw - 8) {
            return;
        }
        float textX = btn.x + (bw - tw) / 2.0f;
        int color = hov ? 0xFFF0F3F6 : 0xFFD2D8DE;
        tr.drawWithShadow(matrices, msg, textX, btn.y + (bh - 8) / 2.0f, color);
    }

    private static void drawSettingsIcon(MatrixStack matrices, int x, int y, int w, int h, int color) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int ring = color & 0xDFFFFFFF;

        fill(matrices, cx - 1, cy - 7, cx + 1, cy - 4, color);
        fill(matrices, cx - 1, cy + 4, cx + 1, cy + 7, color);
        fill(matrices, cx - 7, cy - 1, cx - 4, cy + 1, color);
        fill(matrices, cx + 4, cy - 1, cx + 7, cy + 1, color);
        fill(matrices, cx - 5, cy - 5, cx - 3, cy - 3, color);
        fill(matrices, cx + 3, cy - 5, cx + 5, cy - 3, color);
        fill(matrices, cx - 5, cy + 3, cx - 3, cy + 5, color);
        fill(matrices, cx + 3, cy + 3, cx + 5, cy + 5, color);
        fill(matrices, cx - 4, cy - 4, cx + 4, cy + 4, ring);
        fill(matrices, cx - 2, cy - 2, cx + 2, cy + 2, 0xFF1C2127);
    }

    private static void drawQuitIcon(MatrixStack matrices, int x, int y, int w, int h, int color) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        fill(matrices, cx - 5, cy - 6, cx - 2, cy - 3, color);
        fill(matrices, cx - 2, cy - 3, cx + 1, cy, color);
        fill(matrices, cx + 1, cy, cx + 4, cy + 3, color);
        fill(matrices, cx + 4, cy + 3, cx + 7, cy + 6, color);

        fill(matrices, cx + 4, cy - 6, cx + 7, cy - 3, color);
        fill(matrices, cx + 1, cy - 3, cx + 4, cy, color);
        fill(matrices, cx - 2, cy, cx + 1, cy + 3, color);
        fill(matrices, cx - 5, cy + 3, cx - 2, cy + 6, color);
    }
}
