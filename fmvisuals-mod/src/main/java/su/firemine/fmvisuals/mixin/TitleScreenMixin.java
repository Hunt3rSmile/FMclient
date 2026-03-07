package su.firemine.fmvisuals.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TitleScreen.class, priority = 1100)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin() {
        super(new LiteralText("FMclient"));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void fmRenderOverlay(MatrixStack matrices, int mouseX, int mouseY,
                                  float delta, CallbackInfo ci) {
        if (this.textRenderer == null) return;

        int W = this.width, H = this.height, cx = W / 2;

        // ── 1. Full dark background — covers panorama + vanilla Minecraft logo ──
        fill(matrices, 0, 0, W, H, 0xFF060610);

        // ── 2. God rays ──────────────────────────────────────────────────────
        ScreenMixin.fmDrawRays(matrices, W, H);

        // ── 3. Particles ─────────────────────────────────────────────────────
        ScreenMixin.fmDrawParticles(matrices, W, H);

        // ── 4. Vignette ──────────────────────────────────────────────────────
        fill(matrices, 0, 0,     W, 1,     0x55a855f7);
        fill(matrices, 0, H - 1, W, H,     0x55a855f7);

        // ── 5. Logo ──────────────────────────────────────────────────────────
        int logoY = H / 4 - 10;

        matrices.push();
        matrices.translate(cx, logoY, 0);
        matrices.scale(2.5f, 2.5f, 1.0f);
        String title = "FMclient";
        int tw = this.textRenderer.getWidth(title);
        this.textRenderer.drawWithShadow(matrices, title, -tw / 2.0f, 0, 0xFFc084fc);
        matrices.pop();

        // Subtitle
        String sub = "\u0432\u044b \u0441\u0434\u0435\u043b\u0430\u043b\u0438 \u043f\u0440\u0430\u0432\u0438\u043b\u044c\u043d\u044b\u0439 \u0432\u044b\u0431\u043e\u0440.";
        this.textRenderer.draw(matrices, sub,
            cx - this.textRenderer.getWidth(sub) / 2.0f,
            logoY + 24, 0xFF444466);

        // ── 6. Buttons ───────────────────────────────────────────────────────
        ScreenMixin.fmDrawButtons(matrices, this.buttons, this.textRenderer,
                                   mouseX, mouseY);

        // ── 7. Footer ────────────────────────────────────────────────────────
        this.textRenderer.draw(matrices,
            "v1.2.0  \u2022  FMclient Launcher",
            4f, H - 10f, 0xFF1a1a30);
    }
}
