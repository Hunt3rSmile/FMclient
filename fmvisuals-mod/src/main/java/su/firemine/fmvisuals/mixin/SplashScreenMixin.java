package su.firemine.fmvisuals.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.SplashScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.firemine.fmvisuals.util.FMRenderer;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Mixin(SplashScreen.class)
public abstract class SplashScreenMixin extends Overlay {
    @Unique private static final int C_BG = 0xFF030308;
    @Unique private static final int C_PANEL = 0xE111151A;
    @Unique private static final int C_PANEL_INNER = 0xEE151A21;
    @Unique private static final int C_TEXT = 0xFFF4F7FA;
    @Unique private static final int C_TEXT_MUTED = 0xFF91A0AE;
    @Unique private static final int C_TEXT_FAINT = 0xFF687481;
    @Unique private static final int C_TRACK = 0x8A353C49;
    @Unique private static final int C_FILL = 0xFF7F8A99;
    @Unique private static final int C_FILL_2 = 0xFFD8E0EA;
    @Unique private static final Map<String, SplashTextTexture> FM_TEXT_CACHE = new HashMap<>();
    @Unique private static int fmTextTextureCounter = 0;

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;
    @Shadow @Final private boolean reloading;
    @Shadow @Final private Consumer<Optional<Throwable>> exceptionHandler;
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;
    @Shadow private long prepareCompleteTime;

    @Unique private static final Identifier FM_LOGO = new Identifier("fmvisuals", "textures/gui/flame2.png");
    @Unique private static boolean fmLogoRegistered = false;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fmRender(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int W = this.client.getWindow().getScaledWidth();
        int H = this.client.getWindow().getScaledHeight();
        long now = Util.getMeasuringTimeMs();

        // ── load texture directly from classpath (resource manager not ready yet) ──
        if (!fmLogoRegistered) {
            try {
                InputStream stream = SplashScreenMixin.class.getResourceAsStream(
                        "/assets/fmvisuals/textures/gui/flame2.png");
                if (stream != null) {
                    NativeImage image = NativeImage.read(stream);
                    stream.close();
                    this.client.getTextureManager().registerTexture(
                            FM_LOGO, new NativeImageBackedTexture(image));
                }
            } catch (Exception ignored) {}
            fmLogoRegistered = true;
        }

        // ── update progress smoothly ──
        float reloadProgress = this.reload.getProgress();
        this.progress = this.progress * 0.95f + reloadProgress * 0.05f;

        // ── handle completion ──
        boolean complete = this.reload.isComplete();
        if (complete && this.reloadCompleteTime == -1L) {
            this.reloadCompleteTime = now;
        }
        if (complete && this.prepareCompleteTime == -1L && this.reload.isPrepareStageComplete()) {
            this.prepareCompleteTime = now;
        }

        // ── fade-out alpha ──
        float fadeOut = 1.0f;
        if (this.reloadCompleteTime != -1L) {
            fadeOut = 1.0f - Math.min(1.0f, (now - this.reloadCompleteTime) / 1000.0f);
        }

        float prog = Math.min(1.0f, this.progress);
        float pulse = (float) (Math.sin(now / 520.0) * 0.5 + 0.5);
        float breathe = (float) (Math.sin(now / 900.0) * 0.5 + 0.5);
        float cardAlpha = Math.max(0.0f, Math.min(1.0f, fadeOut));
        // ── background ──
        DrawableHelper.fill(matrices, 0, 0, W, H, C_BG);
        FMRenderer.drawNetwork(matrices, W, H);
        DrawableHelper.fill(matrices, 0, 0, W, H, fmAlpha(cardAlpha * 0.36f, 0x030308));

        int panelW = Math.min(356, Math.max(250, W - 34));
        int panelH = 244;
        int panelX = (W - panelW) / 2;
        int panelY = (H - panelH) / 2 - 8;

        FMRenderer.roundedFill(matrices, panelX - 10, panelY - 10, panelW + 20, panelH + 20,
                fmAlpha(cardAlpha * 0.12f, 0x000000));
        FMRenderer.roundedFill(matrices, panelX, panelY, panelW, panelH,
                fmAlpha(cardAlpha * 0.92f, C_PANEL & 0xFFFFFF));
        FMRenderer.roundedFill(matrices, panelX + 1, panelY + 1, panelW - 2, panelH - 2,
                fmAlpha(cardAlpha * 0.95f, C_PANEL_INNER & 0xFFFFFF));

        int logoSize = 102;
        int logoX = (W - logoSize) / 2;
        int logoY = panelY + 32 + (int) (Math.sin(now / 470.0) * 3.0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        this.client.getTextureManager().bindTexture(FM_LOGO);

        RenderSystem.color4f(1.0f, 1.0f, 1.0f, cardAlpha * (0.10f + pulse * 0.08f));
        DrawableHelper.drawTexture(matrices, logoX - 18, logoY - 18, 0, 0,
                logoSize + 36, logoSize + 36, logoSize + 36, logoSize + 36);

        RenderSystem.color4f(1.0f, 1.0f, 1.0f, cardAlpha * (0.18f + breathe * 0.12f));
        DrawableHelper.drawTexture(matrices, logoX - 8, logoY - 8, 0, 0,
                logoSize + 16, logoSize + 16, logoSize + 16, logoSize + 16);

        RenderSystem.color4f(1.0f, 1.0f, 1.0f, cardAlpha * (0.84f + pulse * 0.12f));
        DrawableHelper.drawTexture(matrices, logoX, logoY, 0, 0, logoSize, logoSize, logoSize, logoSize);
        RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);

        DrawableHelper.fill(matrices, panelX + panelW / 2 - 22, panelY + 148, panelX + panelW / 2 + 22, panelY + 149,
                fmAlpha(cardAlpha * (0.20f + pulse * 0.12f), 0xFFFFFF));

        int barW = panelW - 40;
        int barH = 6;
        int barX = panelX + 20;
        int barY = panelY + panelH - 34;
        fmDrawProgressBar(matrices, barX, barY, barW, barH, prog, cardAlpha, now);
        fmDrawLoadingDots(matrices, panelX + panelW / 2, panelY + 182, cardAlpha, now);

        String stage = fmStageLabel(prog);
        String meta = fmStageMeta(prog);
        String percent = Math.round(prog * 100.0f) + "%";

        fmDrawTextureTextCentered(matrices, "FMclient", panelX + panelW / 2.0f, panelY + 151.0f,
                C_TEXT, 13, true, cardAlpha);
        fmDrawTextureTextCentered(matrices, stage, panelX + panelW / 2.0f, panelY + 168.0f,
                C_TEXT_MUTED, 11, false, cardAlpha * (0.90f + breathe * 0.10f));
        fmDrawTextureText(matrices, meta, barX, barY - 16.0f,
                C_TEXT_FAINT, 10, false, cardAlpha * 0.92f);
        SplashTextTexture percentTexture = fmGetTextTexture(percent, C_TEXT, 11, true);
        if (percentTexture != null) {
            fmDrawTexture(matrices, percentTexture, barX + barW - percentTexture.width, barY - 17.0f,
                    cardAlpha * (0.92f + pulse * 0.08f));
        }

        // ── completion: remove overlay ──
        if (fadeOut <= 0.0f) {
            try {
                this.reload.throwException();
                this.exceptionHandler.accept(Optional.empty());
            } catch (Throwable t) {
                this.exceptionHandler.accept(Optional.of(t));
            }
            this.client.setOverlay(null);
        }

        // ── if reloading, handle prepare stage ──
        if (this.reloading && this.prepareCompleteTime != -1L) {
            long elapsed = now - this.prepareCompleteTime;
            if (elapsed >= 2000L) {
                try {
                    this.reload.throwException();
                    this.exceptionHandler.accept(Optional.empty());
                } catch (Throwable t) {
                    this.exceptionHandler.accept(Optional.of(t));
                }
            }
        }

        ci.cancel();
    }

    @Unique
    private static int fmAlpha(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    @Unique
    private SplashTextTexture fmGetTextTexture(String text, int color, int fontSize, boolean bold) {
        String key = text + "|" + color + "|" + fontSize + "|" + bold;
        SplashTextTexture cached = FM_TEXT_CACHE.get(key);
        if (cached != null) return cached;

        try {
            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D probeGraphics = probe.createGraphics();
            Font font = new Font("Monospaced", bold ? Font.BOLD : Font.PLAIN, fontSize);
            probeGraphics.setFont(font);
            probeGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            probeGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            FontMetrics metrics = probeGraphics.getFontMetrics();
            int width = Math.max(1, metrics.stringWidth(text) + 6);
            int height = Math.max(1, metrics.getHeight() + 6);
            int baseline = metrics.getAscent() + 2;
            probeGraphics.dispose();

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setComposite(AlphaComposite.Src);
            graphics.setFont(font);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setColor(new java.awt.Color(0, 0, 0, 112));
            graphics.drawString(text, 3, baseline + 1);
            graphics.setColor(new java.awt.Color((0xFF << 24) | (color & 0xFFFFFF), true));
            graphics.drawString(text, 2, baseline);
            graphics.dispose();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(image, "png", buffer);
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(buffer.toByteArray()));
            Identifier id = new Identifier("fmvisuals", "dynamic/splash_text_" + (fmTextTextureCounter++));
            this.client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(nativeImage));

            SplashTextTexture created = new SplashTextTexture(id, width, height);
            FM_TEXT_CACHE.put(key, created);
            return created;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Unique
    private void fmDrawTextureText(MatrixStack matrices, String text, float x, float y,
                                   int color, int fontSize, boolean bold, float alpha) {
        SplashTextTexture texture = fmGetTextTexture(text, color, fontSize, bold);
        if (texture != null) {
            fmDrawTexture(matrices, texture, x, y, alpha);
        }
    }

    @Unique
    private void fmDrawTextureTextCentered(MatrixStack matrices, String text, float centerX, float y,
                                           int color, int fontSize, boolean bold, float alpha) {
        SplashTextTexture texture = fmGetTextTexture(text, color, fontSize, bold);
        if (texture != null) {
            fmDrawTexture(matrices, texture, centerX - texture.width / 2.0f, y, alpha);
        }
    }

    @Unique
    private void fmDrawTexture(MatrixStack matrices, SplashTextTexture texture, float x, float y, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.color4f(1.0f, 1.0f, 1.0f, Math.max(0.0f, Math.min(1.0f, alpha)));
        this.client.getTextureManager().bindTexture(texture.id);
        DrawableHelper.drawTexture(matrices, Math.round(x), Math.round(y), 0, 0,
                texture.width, texture.height, texture.width, texture.height);
        RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Unique
    private void fmDrawProgressBar(MatrixStack matrices, int x, int y, int width, int height,
                                   float prog, float alpha, long now) {
        int filled = Math.max(0, Math.min(width, (int) (width * prog)));

        DrawableHelper.fill(matrices, x, y - 2, x + width, y + height + 2,
                fmAlpha(alpha * 0.08f, 0x000000));
        DrawableHelper.fill(matrices, x, y, x + width, y + height,
                fmAlpha(alpha * 0.92f, C_TRACK & 0xFFFFFF));
        DrawableHelper.fill(matrices, x, y, x + width, y + 1,
                fmAlpha(alpha * 0.16f, 0xFFFFFF));

        if (filled > 0) {
            DrawableHelper.fill(matrices, x, y, x + filled, y + height,
                    fmAlpha(alpha * 0.95f, C_FILL & 0xFFFFFF));

            int capWidth = Math.min(18, Math.max(8, filled / 5));
            int capX = Math.max(x, x + filled - capWidth);
            DrawableHelper.fill(matrices, capX, y, x + filled, y + height,
                    fmAlpha(alpha * 0.95f, C_FILL_2 & 0xFFFFFF));
            DrawableHelper.fill(matrices, capX, y, x + filled, y + 1,
                    fmAlpha(alpha * 0.26f, 0xFFFFFF));
            DrawableHelper.fill(matrices, Math.max(x, x + filled - 1), y - 2, x + filled + 1, y + height + 2,
                    fmAlpha(alpha * 0.28f, 0xFFFFFF));
        }

        int sweep = x + (int) (((now / 11L) % (width + 52)) - 26);
        DrawableHelper.fill(matrices, sweep, y + 1, sweep + 12, y + 2,
                fmAlpha(alpha * 0.05f, 0xFFFFFF));
    }

    @Unique
    private void fmDrawLoadingDots(MatrixStack matrices, int centerX, int y, float alpha, long now) {
        for (int i = 0; i < 3; i++) {
            float phase = (float) ((now + i * 150L) % 900L) / 900.0f;
            float pulse = (float) Math.sin(phase * Math.PI);
            float dotAlpha = alpha * (0.18f + Math.max(0.0f, pulse) * 0.48f);
            int color = i == 1 ? C_FILL_2 : C_FILL;
            int dotX = centerX - 10 + i * 10;
            DrawableHelper.fill(matrices, dotX, y, dotX + 3, y + 3, fmAlpha(dotAlpha, color & 0xFFFFFF));
        }
    }

    @Unique
    private String fmStageLabel(float prog) {
        boolean ru = this.client.options.language.startsWith("ru");
        if (prog < 0.18f) return ru ? "Запуск визуального ядра" : "Booting visual core";
        if (prog < 0.42f) return ru ? "Сборка игровых ресурсов" : "Loading game resources";
        if (prog < 0.72f) return ru ? "Подготовка интерфейса" : "Preparing interface";
        if (prog < 0.94f) return ru ? "Финальная синхронизация" : "Final synchronization";
        return ru ? "Почти готово" : "Almost ready";
    }

    @Unique
    private String fmStageMeta(float prog) {
        boolean ru = this.client.options.language.startsWith("ru");
        if (prog < 0.18f) return ru ? "Проверка клиента и модулей" : "Checking client and modules";
        if (prog < 0.42f) return ru ? "Распаковка ассетов и текстур" : "Unpacking assets and textures";
        if (prog < 0.72f) return ru ? "Подключение экранов и рендеров" : "Binding screens and renderers";
        if (prog < 0.94f) return ru ? "Стабилизация данных запуска" : "Stabilizing launch data";
        return ru ? "Передаём управление игре" : "Handing control to the game";
    }

    @Unique
    private static final class SplashTextTexture {
        private final Identifier id;
        private final int width;
        private final int height;

        private SplashTextTexture(Identifier id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }
    }
}
