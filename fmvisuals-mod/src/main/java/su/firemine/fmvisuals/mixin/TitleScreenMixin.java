package su.firemine.fmvisuals.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.firemine.fmvisuals.util.FMRenderer;

@Mixin(value = TitleScreen.class, priority = 1100)
public abstract class TitleScreenMixin extends Screen {

    @Shadow private String splashText;

    protected TitleScreenMixin() {
        super(new LiteralText("FMclient"));
    }

    // Remove vanilla splash text as early as possible
    @Inject(method = "init", at = @At("TAIL"))
    private void fmInit(CallbackInfo ci) {
        this.splashText = null;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void fmRenderOverlay(MatrixStack matrices, int mouseX, int mouseY,
                                  float delta, CallbackInfo ci) {
        if (this.textRenderer == null) return;

        int W = this.width, H = this.height, cx = W / 2;

        // ── 1. Solid dark background ──────────────────────────────────────
        fill(matrices, 0, 0, W, H, 0xFF030308);

        // ── 2. Particle network ───────────────────────────────────────────
        FMRenderer.drawNetwork(matrices, W, H);

        // ── 3. Subtle top/bottom accent lines ────────────────────────────
        fill(matrices, 0, 0,     W, 1,     0x33a855f7);
        fill(matrices, 0, H - 1, W, H,     0x33a855f7);

        // ── 4. Logo ───────────────────────────────────────────────────────
        int logoY = H / 4 - 8;

        matrices.push();
        matrices.translate(cx, logoY, 0);
        matrices.scale(2.5f, 2.5f, 1.0f);
        String title = "FMclient";
        int tw = this.textRenderer.getWidth(title);
        this.textRenderer.drawWithShadow(matrices, title, -tw / 2.0f, 0, 0xFFc084fc);
        matrices.pop();

        // ── 5. Buttons ────────────────────────────────────────────────────
        FMRenderer.drawButtons(matrices, this.buttons, this.textRenderer,
                               mouseX, mouseY);

        // ── 6. Footer ─────────────────────────────────────────────────────
        this.textRenderer.draw(matrices,
            "v1.2.0  \u2022  FMclient",
            4f, H - 10f, 0xFF2a2a44);
    }
}
