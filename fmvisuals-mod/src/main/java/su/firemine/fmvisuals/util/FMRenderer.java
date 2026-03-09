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
    // Per-particle color (R, G, B)
    private static final int[] PR = new int[N];
    private static final int[] PG = new int[N];
    private static final int[] PB = new int[N];
    private static long lastNs = 0;

    private static final int[][] PALETTE = {
        {100, 180, 230},  // голубой
        {160, 100, 240},  // фиолетовый
        {230, 80, 160},   // розовый
        {80, 200, 180},   // бирюзовый
        {240, 160, 60},   // оранжевый
        {100, 220, 120},  // зелёный
        {200, 200, 100},  // жёлтый
        {120, 140, 240},  // синий
    };

    static {
        Random rng = new Random(0xFCA_BCDEL);
        for (int i = 0; i < N; i++) {
            PX[i] = rng.nextFloat();
            PY[i] = rng.nextFloat();
            double a = rng.nextDouble() * Math.PI * 2;
            float  s = 0.008f + rng.nextFloat() * 0.010f;
            PVX[i] = (float)(Math.cos(a) * s);
            PVY[i] = (float)(Math.sin(a) * s);
            int[] c = PALETTE[rng.nextInt(PALETTE.length)];
            PR[i] = c[0]; PG[i] = c[1]; PB[i] = c[2];
        }
    }

    // ── Public fill wrapper ────────────────────────────────────────────────
    public static void rect(MatrixStack m, int x1, int y1, int x2, int y2, int color) {
        fill(m, x1, y1, x2, y2, color);
    }

    public static void pill(MatrixStack m, int x, int y, int w, int h, int color) {
        fmFill(m, x, y, w, h, color);
    }

    public static void roundedFill(MatrixStack m, int x, int y, int w, int h, int color) {
        fmFill(m, x, y, w, h, color);
    }

    public static void roundedBorder(MatrixStack m, int x, int y, int w, int h, int color) {
        fmBorder(m, x, y, w, h, color);
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

        // --- draw lines between close particles (colored by endpoints) ---
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
                    int alpha = (int)((1f - dist / maxDist) * 38);
                    float x1 = PX[i] * W, y1 = PY[i] * H;
                    float x2 = PX[j] * W, y2 = PY[j] * H;
                    buf.vertex(matrix, x1, y1, 0f).color(PR[i], PG[i], PB[i], alpha).next();
                    buf.vertex(matrix, x2, y2, 0f).color(PR[j], PG[j], PB[j], alpha).next();
                }
            }
        }
        tess.draw();

        RenderSystem.enableTexture();

        // --- draw particle dots: glow + core at 3× resolution ---
        float s = 1.0f / 3.0f;
        for (int i = 0; i < N; i++) {
            float px = PX[i] * W;
            float py = PY[i] * H;
            int r = PR[i], g = PG[i], b = PB[i];

            matrices.push();
            matrices.translate(px, py, 0);
            matrices.scale(s, s, 1.0f);
            // 3× space: 1 unit = 0.33 real px

            // glow layers (real px: ~5, ~3.3, ~2)
            fillCircleLocal(matrices, 0, 0, 15, (6  << 24) | (r << 16) | (g << 8) | b);
            fillCircleLocal(matrices, 0, 0, 10, (12 << 24) | (r << 16) | (g << 8) | b);
            fillCircleLocal(matrices, 0, 0, 6,  (20 << 24) | (r << 16) | (g << 8) | b);

            // core dot (real px: ~0.7 radius — tight bright circle)
            fillCircleLocal(matrices, 0, 0, 3, (220 << 24) | (r << 16) | (g << 8) | b);

            matrices.pop();
        }
    }

    // ── Fill circle via scanline (works reliably with fill()) ──────────────
    private static void fillCircleLocal(MatrixStack m, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int halfX = (int) Math.sqrt(radius * radius - dy * dy);
            fill(m, cx - halfX, cy + dy, cx + halfX + 1, cy + dy + 1, color);
        }
    }

    // ── Smooth rounded rectangle (tessellated corners) ─────────────────────
    private static final int CORNER_SEGMENTS = 24;

    /**
     * Builds the outline of a rounded rect as an array of (x,y) pairs,
     * going clockwise: top edge → top-right arc → right edge → bottom-right arc → ...
     */
    private static float[] roundedRectOutline(float x1, float y1, float x2, float y2, float r) {
        // 4 corners × CORNER_SEGMENTS + 4 straight-edge start points
        int count = 4 * (CORNER_SEGMENTS + 1);
        float[] pts = new float[count * 2];
        int idx = 0;

        // top-left arc (from 270° down to 180°, i.e. up-left)
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            double ang = Math.toRadians(180.0 + 90.0 * i / CORNER_SEGMENTS);
            pts[idx++] = x1 + r + (float) Math.cos(ang) * r;
            pts[idx++] = y1 + r + (float) Math.sin(ang) * r;
        }
        // top-right arc
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            double ang = Math.toRadians(270.0 + 90.0 * i / CORNER_SEGMENTS);
            pts[idx++] = x2 - r + (float) Math.cos(ang) * r;
            pts[idx++] = y1 + r + (float) Math.sin(ang) * r;
        }
        // bottom-right arc
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            double ang = Math.toRadians(0.0 + 90.0 * i / CORNER_SEGMENTS);
            pts[idx++] = x2 - r + (float) Math.cos(ang) * r;
            pts[idx++] = y2 - r + (float) Math.sin(ang) * r;
        }
        // bottom-left arc
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            double ang = Math.toRadians(90.0 + 90.0 * i / CORNER_SEGMENTS);
            pts[idx++] = x1 + r + (float) Math.cos(ang) * r;
            pts[idx++] = y2 - r + (float) Math.sin(ang) * r;
        }

        return pts;
    }

    private static void fmFill(MatrixStack m, int x, int y, int w, int h, int c) {
        float r = Math.min(4.0f, Math.min(w, h) / 2.0f);
        int a = (c >> 24) & 0xFF, cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;

        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f mat = m.peek().getModel();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_TRIANGLES, VertexFormats.POSITION_COLOR);

        float cx = x + w / 2.0f, cy = y + h / 2.0f;
        float[] pts = roundedRectOutline(x, y, x + w, y + h, r);
        int verts = pts.length / 2;

        for (int i = 0; i < verts; i++) {
            int j = (i + 1) % verts;
            buf.vertex(mat, cx, cy, 0).color(cr, cg, cb, a).next();
            buf.vertex(mat, pts[i * 2], pts[i * 2 + 1], 0).color(cr, cg, cb, a).next();
            buf.vertex(mat, pts[j * 2], pts[j * 2 + 1], 0).color(cr, cg, cb, a).next();
        }

        tess.draw();
        RenderSystem.enableTexture();
    }

    private static void fmBorder(MatrixStack m, int x, int y, int w, int h, int c) {
        float r = Math.min(4.0f, Math.min(w, h) / 2.0f);
        float t = 1.0f;
        int a = (c >> 24) & 0xFF, cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;

        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f mat = m.peek().getModel();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_TRIANGLES, VertexFormats.POSITION_COLOR);

        float[] outer = roundedRectOutline(x, y, x + w, y + h, r);
        float ri = Math.max(r - t, 0);
        float[] inner = roundedRectOutline(x + t, y + t, x + w - t, y + h - t, ri);
        int verts = outer.length / 2;

        for (int i = 0; i < verts; i++) {
            int j = (i + 1) % verts;
            // two triangles per segment of the ring
            buf.vertex(mat, outer[i * 2], outer[i * 2 + 1], 0).color(cr, cg, cb, a).next();
            buf.vertex(mat, inner[i * 2], inner[i * 2 + 1], 0).color(cr, cg, cb, a).next();
            buf.vertex(mat, outer[j * 2], outer[j * 2 + 1], 0).color(cr, cg, cb, a).next();

            buf.vertex(mat, outer[j * 2], outer[j * 2 + 1], 0).color(cr, cg, cb, a).next();
            buf.vertex(mat, inner[i * 2], inner[i * 2 + 1], 0).color(cr, cg, cb, a).next();
            buf.vertex(mat, inner[j * 2], inner[j * 2 + 1], 0).color(cr, cg, cb, a).next();
        }

        tess.draw();
        RenderSystem.enableTexture();
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
            // soft shadow behind button
            fmFill(matrices, btn.x - 1, btn.y - 1, bw + 2, bh + 2, 0x18000000);
            // main body
            fmFill(matrices, btn.x, btn.y, bw, bh, hov ? BUTTON_FILL_HOVER : BUTTON_FILL);
            // inner top highlight (subtle glass effect)
            fmFill(matrices, btn.x + 1, btn.y + 1, bw - 2, 2,
                hov ? 0x22FFFFFF : 0x14FFFFFF);
            // inner bottom shadow
            fmFill(matrices, btn.x + 1, btn.y + bh - 3, bw - 2, 2,
                hov ? 0x18000000 : 0x10000000);
            // border
            fmBorder(matrices, btn.x, btn.y, bw, bh, hov ? BUTTON_EDGE_HOVER : BUTTON_EDGE);
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

    /**
     * Draws icons at 3× internal resolution using matrix scaling.
     * Each fill() unit becomes 1/3 of a real pixel — smooth sub-pixel rendering.
     */
    private static final int ICON_SCALE = 3;

    private static void drawSettingsIcon(MatrixStack matrices, int x, int y, int w, int h, int color) {
        float cx = x + w / 2.0f;
        float cy = y + h / 2.0f;
        float s = 1.0f / ICON_SCALE;

        matrices.push();
        matrices.translate(cx, cy, 0);
        matrices.scale(s, s, 1.0f);
        // 3× space: 1 unit = 0.33 real px

        float ringInner = 4.0f;   // inner edge of ring body
        float ringOuter = 7.5f;   // outer edge of ring / tooth base
        float toothTip  = 12.0f;  // tip of teeth (~4 real px total radius)
        float holeR     = 2.8f;   // center hole
        int   numTeeth  = 8;
        double sectorAng = Math.PI * 2.0 / numTeeth;
        double toothHalf = sectorAng * 0.28; // each tooth covers ~56% of sector

        int gridR = (int) toothTip + 1;

        // Scanline render the gear shape
        for (int py = -gridR; py <= gridR; py++) {
            boolean inRun = false;
            int runStart = 0;
            for (int px = -gridR; px <= gridR + 1; px++) {
                boolean filled = false;
                if (px <= gridR) {
                    float dist = (float) Math.sqrt(px * px + py * py);
                    if (dist >= ringInner && dist <= toothTip) {
                        if (dist <= ringOuter) {
                            // inside ring body — always filled
                            filled = true;
                        } else {
                            // between ringOuter and toothTip — only in tooth zones
                            double ang = Math.atan2(py, px);
                            double posInSector = ((ang % sectorAng) + sectorAng) % sectorAng;
                            double distFromCenter = Math.abs(posInSector - sectorAng / 2.0);
                            filled = distFromCenter < toothHalf;
                        }
                    }
                    // cut center hole
                    if (dist < holeR) filled = false;
                }

                if (filled && !inRun) {
                    runStart = px;
                    inRun = true;
                } else if (!filled && inRun) {
                    fill(matrices, runStart, py, px, py + 1, color);
                    inRun = false;
                }
            }
        }

        matrices.pop();
    }

    private static void drawQuitIcon(MatrixStack matrices, int x, int y, int w, int h, int color) {
        float cx = x + w / 2.0f;
        float cy = y + h / 2.0f;
        float s = 1.0f / ICON_SCALE;
        int dim = (color & 0x00FFFFFF) | 0x40000000;

        matrices.push();
        matrices.translate(cx, cy, 0);
        matrices.scale(s, s, 1.0f);

        int half = 9;  // ~3 real px — same as gear outerR
        int thick = 2;

        // "\" diagonal + AA fringe
        for (int i = -half; i <= half; i++) {
            fill(matrices, i - thick, i - thick, i + thick, i + thick, color);
            fill(matrices, i - thick - 1, i - thick, i - thick, i + thick, dim);
            fill(matrices, i + thick, i - thick, i + thick + 1, i + thick, dim);
            fill(matrices, i - thick, i - thick - 1, i + thick, i - thick, dim);
            fill(matrices, i - thick, i + thick, i + thick, i + thick + 1, dim);
        }

        // "/" diagonal + AA fringe
        for (int i = -half; i <= half; i++) {
            fill(matrices, -i - thick, i - thick, -i + thick, i + thick, color);
            fill(matrices, -i - thick - 1, i - thick, -i - thick, i + thick, dim);
            fill(matrices, -i + thick, i - thick, -i + thick + 1, i + thick, dim);
            fill(matrices, -i - thick, i - thick - 1, -i + thick, i - thick, dim);
            fill(matrices, -i - thick, i + thick, -i + thick, i + thick + 1, dim);
        }

        matrices.pop();
    }
}
