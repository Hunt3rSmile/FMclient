package su.firemine.fmvisuals.util;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import su.firemine.fmvisuals.mixin.ClickableWidgetAccessor;

import java.util.List;

/**
 * Plain utility class (NOT a Mixin) that holds all shared FM rendering helpers.
 * Extends DrawableHelper to access the protected static fill() method.
 */
public final class FMRenderer extends DrawableHelper {

    private FMRenderer() {}

    /** Public wrapper around the protected DrawableHelper.fill(). */
    public static void rect(MatrixStack m, int x1, int y1, int x2, int y2, int color) {
        fill(m, x1, y1, x2, y2, color);
    }

    // ── Rounded fill (radius 2px) ──────────────────────────────────────────
    public static void fmFill(MatrixStack m, int x, int y, int w, int h, int c) {
        fill(m, x + 2, y,     x + w - 2, y + h,     c);
        fill(m, x + 1, y + 1, x + w - 1, y + h - 1, c);
        fill(m, x,     y + 2, x + w,     y + h - 2, c);
    }

    // ── Rounded border ─────────────────────────────────────────────────────
    public static void fmBorder(MatrixStack m, int x, int y, int w, int h, int c) {
        fill(m, x + 2,     y,         x + w - 2,   y + 1,         c);
        fill(m, x + 2,     y + h - 1, x + w - 2,   y + h,         c);
        fill(m, x,         y + 2,     x + 1,       y + h - 2,     c);
        fill(m, x + w - 1, y + 2,     x + w,       y + h - 2,     c);
        fill(m, x + 1,     y + 1,     x + 2,       y + 2,         c);
        fill(m, x + w - 2, y + 1,     x + w - 1,   y + 2,         c);
        fill(m, x + 1,     y + h - 2, x + 2,       y + h - 1,     c);
        fill(m, x + w - 2, y + h - 2, x + w - 1,   y + h - 1,     c);
    }

    // ── God rays as expanding trapezoids ───────────────────────────────────
    public static void drawRays(MatrixStack m, int W, int H) {
        int cx = W / 2;
        int STEPS = 10;
        float[][] rays = {
            {-60f, 1f, 26f, 0x04f}, {-42f, 1f, 20f, 0x06f},
            {-26f, 2f, 36f, 0x09f}, {-15f, 2f, 24f, 0x0Cf},
            { -6f, 1f, 16f, 0x0Ff}, {  0f, 1f, 12f, 0x14f},
            {  6f, 1f, 18f, 0x0Ff}, { 15f, 2f, 28f, 0x0Cf},
            { 26f, 2f, 34f, 0x09f}, { 42f, 1f, 22f, 0x06f},
            { 60f, 1f, 24f, 0x04f}, {-75f, 1f, 14f, 0x02f},
            { 75f, 1f, 16f, 0x02f},
        };
        for (float[] r : rays) {
            double rad = Math.toRadians(r[0]);
            int color = ((int) r[3] << 24) | 0xEEF0FF;
            for (int s = 0; s < STEPS; s++) {
                int y1   = s * H / STEPS;
                int y2   = (s + 1) * H / STEPS;
                int midY = (y1 + y2) / 2;
                int centerX = cx + (int)(midY * Math.sin(rad));
                int halfW = Math.max(1, (int)(r[1] + (r[2] - r[1]) * (float) midY / H));
                fill(m, centerX - halfW, y1, centerX + halfW, y2, color);
            }
        }
        fill(m, cx - 30, 0, cx + 30, 30, 0x07EEF0FF);
        fill(m, cx - 12, 0, cx + 12, 14, 0x0CEEF0FF);
    }

    // ── Scattered static particles ─────────────────────────────────────────
    public static void drawParticles(MatrixStack m, int W, int H) {
        long s = (long) W * 7919L + (long) H * 6271L;
        for (int i = 0; i < 55; i++) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            int px = (int) Math.abs(s % W);
            s = s * 6364136223846793005L + 1442695040888963407L;
            int py = (int) Math.abs(s % H);
            int alpha = 0x08 + (int) Math.abs(s % 0x18);
            fill(m, px, py, px + 1, py + 1, (alpha << 24) | 0xFFFFFF);
        }
    }

    // ── Restyle all screen buttons ─────────────────────────────────────────
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
                    hov ? 0xFFFFFFFF : 0xFF9999CC);
            }
        }
    }
}
