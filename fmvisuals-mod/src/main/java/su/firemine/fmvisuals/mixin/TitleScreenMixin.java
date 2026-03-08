package su.firemine.fmvisuals.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.firemine.fmvisuals.util.FMRenderer;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(value = TitleScreen.class, priority = 1100)
public abstract class TitleScreenMixin extends Screen {
    @Unique private static final String FM_SETTINGS_ICON = "__fm_settings_icon__";
    @Unique private static final String FM_QUIT_ICON = "__fm_quit_icon__";

    @Unique
    private static final String[] FMCLIENT_TAGLINES = {
            "Чистая сборка для спокойного запуска.",
            "Аккуратный старт без визуального шума.",
            "Минималистичный клиент для чистой игры.",
            "Собран для тихого и стабильного входа.",
            "Лишнее убрано. Фокус оставлен.",
            "FMclient. Строго, чисто, быстро."
    };

    @Unique private static final long TYPE_INTERVAL_MS = 65L;
    @Unique private static final long DELETE_INTERVAL_MS = 35L;
    @Unique private static final long HOLD_FULL_MS = 1400L;
    @Unique private static final long HOLD_EMPTY_MS = 420L;
    @Unique private static final long CURSOR_BLINK_MS = 530L;
    @Unique private static final int STATE_TYPING = 0;
    @Unique private static final int STATE_HOLD_FULL = 1;
    @Unique private static final int STATE_DELETING = 2;
    @Unique private static final int STATE_HOLD_EMPTY = 3;

    @Shadow private String splashText;

    @Unique private int fmTypewriterState = STATE_TYPING;
    @Unique private int fmTaglineIndex = 0;
    @Unique private int fmVisibleChars = 0;
    @Unique private long fmLastAnimationStepAt = 0L;

    protected TitleScreenMixin() {
        super(new LiteralText("FMclient"));
    }

    // Remove vanilla splash text as early as possible
    @Inject(method = "init", at = @At("TAIL"))
    private void fmInit(CallbackInfo ci) {
        this.splashText = null;
        this.fmTypewriterState = STATE_TYPING;
        this.fmTaglineIndex = ThreadLocalRandom.current().nextInt(FMCLIENT_TAGLINES.length);
        this.fmVisibleChars = 0;
        this.fmLastAnimationStepAt = System.currentTimeMillis();

        for (ClickableWidget button : this.buttons) {
            if (((ClickableWidgetAccessor) button).getWidthPx() <= 20) {
                button.visible = false;
                button.active = false;
                continue;
            }

            Text message = button.getMessage();
            String label = message == null ? "" : message.getString();
            if (label.contains("Realms")) {
                button.visible = false;
                button.active = false;
            }
        }

        this.fmLayoutButtons();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fmRenderOverlay(MatrixStack matrices, int mouseX, int mouseY,
                                 float delta, CallbackInfo ci) {
        int W = this.width, H = this.height, cx = W / 2;

        fill(matrices, 0, 0, W, H, 0xFF030308);
        FMRenderer.drawNetwork(matrices, W, H);
        fill(matrices, 0, 0, W, 1, 0x2260AAD6);
        fill(matrices, 0, H - 1, W, H, 0x2260AAD6);

        if (this.textRenderer == null) {
            ci.cancel();
            return;
        }

        int[] menuBounds = this.fmFindMenuBounds();
        int logoY = menuBounds[1] - 44;
        fill(matrices, cx - 92, logoY - 14, cx + 12, logoY - 12, 0xFFE4E8EC);
        fill(matrices, cx + 18, logoY - 14, cx + 92, logoY - 12, 0xFF607180);
        matrices.push();
        matrices.translate(cx, logoY, 0);
        matrices.scale(2.85f, 2.85f, 1.0f);
        String title = "FMclient";
        int tw = this.textRenderer.getWidth(title);
        this.textRenderer.drawWithShadow(matrices, title, -tw / 2.0f, 0, 0xFFF5F7F9);
        matrices.pop();
        this.fmAdvanceTypewriter();
        String tagline = this.fmCurrentVisibleTagline();
        int subtitleWidth = this.textRenderer.getWidth(tagline);
        this.textRenderer.draw(matrices, tagline, cx - subtitleWidth / 2.0f, logoY + 34.0f, 0xFF8A97A4);

        super.render(matrices, mouseX, mouseY, delta);
        this.textRenderer.draw(matrices, "v1.2.4  |  FMclient", 4f, H - 10f, 0xFF5B6874);
        ci.cancel();
    }

    @Unique
    private int[] fmFindMenuBounds() {
        int minX = this.width / 2 - 100;
        int minY = this.height / 4 + 40;
        int maxX = this.width / 2 + 100;
        int maxY = minY + 120;

        boolean found = false;
        for (ClickableWidget button : this.buttons) {
            if (!button.visible) continue;
            int bw = ((ClickableWidgetAccessor) button).getWidthPx();
            if (bw <= 40) continue;

            found = true;
            minX = Math.min(minX, button.x);
            minY = Math.min(minY, button.y);
            maxX = Math.max(maxX, button.x + bw);
            maxY = Math.max(maxY, button.y + ((ClickableWidgetAccessor) button).getHeightPx());
        }

        if (!found) {
            minX = this.width / 2 - 120;
            minY = this.height / 4;
            maxX = this.width / 2 + 120;
            maxY = minY + 160;
        }

        return new int[] { minX, minY, maxX, maxY };
    }

    @Unique
    private void fmLayoutButtons() {
        int cx = this.width / 2;
        int mainWidth = 170;
        int mainHeight = 22;
        int secondaryGap = 6;
        int smallWidth = (mainWidth - secondaryGap) / 2;
        int smallHeight = 22;
        int topY = this.height / 4 + 52;
        int rowGap = 28;
        int secondaryRowY = topY + rowGap * 2 + 4;

        for (ClickableWidget button : this.buttons) {
            if (!button.visible) continue;

            ClickableWidgetAccessor accessor = (ClickableWidgetAccessor) button;
            Text message = button.getMessage();
            String label = message == null ? "" : message.getString();

            if (this.fmIsMainMenuButton(label)) {
                accessor.setWidthPx(mainWidth);
                accessor.setHeightPx(mainHeight);
                button.x = cx - mainWidth / 2;
                button.y = this.fmMainButtonY(label, topY, rowGap);
                continue;
            }

            if (this.fmIsSettingsButton(label)) {
                accessor.setWidthPx(smallWidth);
                accessor.setHeightPx(smallHeight);
                button.x = cx - mainWidth / 2;
                button.y = secondaryRowY;
                button.setMessage(new LiteralText(FM_SETTINGS_ICON));
                continue;
            }

            if (this.fmIsQuitButton(label)) {
                accessor.setWidthPx(smallWidth);
                accessor.setHeightPx(smallHeight);
                button.x = cx - mainWidth / 2 + smallWidth + secondaryGap;
                button.y = secondaryRowY;
                button.setMessage(new LiteralText(FM_QUIT_ICON));
            }
        }
    }

    @Unique
    private boolean fmIsMainMenuButton(String label) {
        return label.contains("Одиноч")
                || label.contains("Singleplayer")
                || label.contains("Сетев")
                || label.contains("Multiplayer");
    }

    @Unique
    private int fmMainButtonY(String label, int topY, int rowGap) {
        if (label.contains("Одиноч") || label.contains("Singleplayer")) {
            return topY;
        }
        if (label.contains("Сетев") || label.contains("Multiplayer")) {
            return topY + rowGap;
        }
        return topY + rowGap * 2;
    }

    @Unique
    private boolean fmIsSettingsButton(String label) {
        return label.contains("Настрой")
                || label.contains("Options")
                || FM_SETTINGS_ICON.equals(label);
    }

    @Unique
    private boolean fmIsQuitButton(String label) {
        return label.contains("Выйти")
                || label.contains("Quit")
                || FM_QUIT_ICON.equals(label);
    }

    @Unique
    private void fmAdvanceTypewriter() {
        long now = System.currentTimeMillis();
        String currentTagline = FMCLIENT_TAGLINES[this.fmTaglineIndex];

        switch (this.fmTypewriterState) {
            case STATE_TYPING:
                if (now - this.fmLastAnimationStepAt >= TYPE_INTERVAL_MS) {
                    if (this.fmVisibleChars < currentTagline.length()) {
                        this.fmVisibleChars++;
                        this.fmLastAnimationStepAt = now;
                    } else {
                        this.fmTypewriterState = STATE_HOLD_FULL;
                        this.fmLastAnimationStepAt = now;
                    }
                }
                break;
            case STATE_HOLD_FULL:
                if (now - this.fmLastAnimationStepAt >= HOLD_FULL_MS) {
                    this.fmTypewriterState = STATE_DELETING;
                    this.fmLastAnimationStepAt = now;
                }
                break;
            case STATE_DELETING:
                if (now - this.fmLastAnimationStepAt >= DELETE_INTERVAL_MS) {
                    if (this.fmVisibleChars > 0) {
                        this.fmVisibleChars--;
                        this.fmLastAnimationStepAt = now;
                    } else {
                        this.fmTypewriterState = STATE_HOLD_EMPTY;
                        this.fmLastAnimationStepAt = now;
                        this.fmTaglineIndex = this.fmNextTaglineIndex();
                    }
                }
                break;
            case STATE_HOLD_EMPTY:
                if (now - this.fmLastAnimationStepAt >= HOLD_EMPTY_MS) {
                    this.fmTypewriterState = STATE_TYPING;
                    this.fmLastAnimationStepAt = now;
                }
                break;
        }
    }

    @Unique
    private String fmCurrentVisibleTagline() {
        String tagline = FMCLIENT_TAGLINES[this.fmTaglineIndex];
        int endIndex = Math.min(this.fmVisibleChars, tagline.length());
        String visibleText = tagline.substring(0, endIndex);
        return this.fmShouldShowCursor() ? visibleText + "|" : visibleText;
    }

    @Unique
    private boolean fmShouldShowCursor() {
        return (System.currentTimeMillis() / CURSOR_BLINK_MS) % 2L == 0L;
    }

    @Unique
    private int fmNextTaglineIndex() {
        if (FMCLIENT_TAGLINES.length <= 1) {
            return 0;
        }

        int nextIndex = this.fmTaglineIndex;
        while (nextIndex == this.fmTaglineIndex) {
            nextIndex = ThreadLocalRandom.current().nextInt(FMCLIENT_TAGLINES.length);
        }
        return nextIndex;
    }
}
