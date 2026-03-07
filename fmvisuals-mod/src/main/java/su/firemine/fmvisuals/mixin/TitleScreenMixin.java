package su.firemine.fmvisuals.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin() {
        super(new LiteralText("FMclient"));
    }

    // ── Rounded fill (radius ~2px) ─────────────────────────────────────────
    private void fmFill(MatrixStack m, int x, int y, int w, int h, int c) {
        fill(m, x + 2, y,     x + w - 2, y + h,     c);
        fill(m, x + 1, y + 1, x + w - 1, y + h - 1, c);
        fill(m, x,     y + 2, x + w,     y + h - 2, c);
    }

    // ── Rounded border ─────────────────────────────────────────────────────
    private void fmBorder(MatrixStack m, int x, int y, int w, int h, int c) {
        fill(m, x + 2, y,         x + w - 2, y + 1,         c); // top
        fill(m, x + 2, y + h - 1, x + w - 2, y + h,         c); // bottom
        fill(m, x,     y + 2,     x + 1,     y + h - 2,     c); // left
        fill(m, x + w - 1, y + 2, x + w,     y + h - 2,     c); // right
        fill(m, x + 1,     y + 1, x + 2,     y + 2,         c); // corner TL
        fill(m, x + w - 2, y + 1, x + w - 1, y + 2,         c); // corner TR
        fill(m, x + 1,     y + h - 2, x + 2, y + h - 1,     c); // corner BL
        fill(m, x + w - 2, y + h - 2, x + w - 1, y + h - 1, c); // corner BR
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void fmRenderOverlay(MatrixStack matrices, int mouseX, int mouseY,
                                  float delta, CallbackInfo ci) {
        // Guard: textRenderer may be null during early resource reload
        if (this.textRenderer == null) return;

        int W = this.width, H = this.height, cx = W / 2;

        // ── 1. Cover EVERYTHING vanilla drew (panorama, logo texture, etc.) ──
        fill(matrices, 0, 0, W, H, 0xFF07070F);

        // Faint purple vignette edges
        fill(matrices, 0, 0,     W, 1,     0x50a855f7);
        fill(matrices, 0, 1,     W, 2,     0x20a855f7);
        fill(matrices, 0, H - 2, W, H - 1, 0x20a855f7);
        fill(matrices, 0, H - 1, W, H,     0x50a855f7);

        // ── 2. Logo ──────────────────────────────────────────────────────────
        int logoY = H / 4 - 18;

        // Soft glow behind logo text
        fill(matrices, cx - 110, logoY - 8, cx + 110, logoY + 50, 0x08a855f7);

        // "FMclient" scaled ×3
        matrices.push();
        matrices.translate(cx, logoY + 2, 0);
        matrices.scale(3.0f, 3.0f, 1.0f);
        String title = "FMclient";
        int tw = this.textRenderer.getWidth(title);
        this.textRenderer.drawWithShadow(matrices, title, -tw / 2.0f, 0, 0xFFc084fc);
        matrices.pop();

        // Subtitle
        String sub = "Minecraft Launcher";
        this.textRenderer.draw(matrices, sub,
            cx - this.textRenderer.getWidth(sub) / 2.0f,
            logoY + 33, 0xFF3a3a66);

        // Thin accent line
        fill(matrices, cx - 50, logoY + 43, cx + 50, logoY + 44, 0x55a855f7);

        // ── 3. Buttons ───────────────────────────────────────────────────────
        for (ClickableWidget btn : this.buttons) {
            if (!btn.visible) continue;

            ClickableWidgetAccessor acc = (ClickableWidgetAccessor) btn;
            int bw = acc.getWidthPx(), bh = acc.getHeightPx();

            // Skip tiny icon buttons (language / accessibility)
            if (bw < 40) continue;

            boolean hov = mouseX >= btn.x && mouseX < btn.x + bw
                       && mouseY >= btn.y && mouseY < btn.y + bh;

            // Background
            fmFill(matrices, btn.x, btn.y, bw, bh,
                hov ? 0xCC180638 : 0xCC0b0222);

            // Border (brighter on hover)
            fmBorder(matrices, btn.x, btn.y, bw, bh,
                hov ? 0xFFa855f7 : 0xFF3d1468);

            // Top-edge shimmer on hover
            if (hov) {
                fill(matrices, btn.x + 2, btn.y + 1,
                     btn.x + bw - 2, btn.y + 2, 0x18ffffff);
            }

            // Label text
            Text msg = btn.getMessage();
            if (msg != null) {
                int mtw = this.textRenderer.getWidth(msg);
                this.textRenderer.drawWithShadow(matrices, msg,
                    btn.x + (bw - mtw) / 2.0f,
                    btn.y + (bh - 8) / 2.0f,
                    hov ? 0xFFFFFFFF : 0xFF8888BB);
            }
        }

        // ── 4. Footer ────────────────────────────────────────────────────────
        this.textRenderer.draw(matrices,
            "v1.2.0  \u2022  LSHIFT \u2014 \u0432\u0438\u0437\u0443\u0430\u043b\u044c\u043d\u044b\u0435 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438",
            4f, H - 10f, 0xFF181830);
    }
}
