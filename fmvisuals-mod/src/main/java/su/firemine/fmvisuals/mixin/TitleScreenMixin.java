package su.firemine.fmvisuals.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.resource.language.LanguageDefinition;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import su.firemine.fmvisuals.util.FMRenderer;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(value = TitleScreen.class, priority = 1100)
public abstract class TitleScreenMixin extends Screen {
    @Unique private static final String FM_SETTINGS_ICON = "__fm_settings_icon__";
    @Unique private static final String FM_QUIT_ICON = "__fm_quit_icon__";

    @Unique
    private static final String[] FMCLIENT_TAGLINES_RU = {
            "\u0427\u0438\u0441\u0442\u0430\u044F \u0441\u0431\u043E\u0440\u043A\u0430 \u0434\u043B\u044F \u0441\u043F\u043E\u043A\u043E\u0439\u043D\u043E\u0433\u043E \u0437\u0430\u043F\u0443\u0441\u043A\u0430.",
            "\u0410\u043A\u043A\u0443\u0440\u0430\u0442\u043D\u044B\u0439 \u0441\u0442\u0430\u0440\u0442 \u0431\u0435\u0437 \u0432\u0438\u0437\u0443\u0430\u043B\u044C\u043D\u043E\u0433\u043E \u0448\u0443\u043C\u0430.",
            "\u041C\u0438\u043D\u0438\u043C\u0430\u043B\u0438\u0441\u0442\u0438\u0447\u043D\u044B\u0439 \u043A\u043B\u0438\u0435\u043D\u0442 \u0434\u043B\u044F \u0447\u0438\u0441\u0442\u043E\u0439 \u0438\u0433\u0440\u044B.",
            "\u0421\u043E\u0431\u0440\u0430\u043D \u0434\u043B\u044F \u0442\u0438\u0445\u043E\u0433\u043E \u0438 \u0441\u0442\u0430\u0431\u0438\u043B\u044C\u043D\u043E\u0433\u043E \u0432\u0445\u043E\u0434\u0430.",
            "\u041B\u0438\u0448\u043D\u0435\u0435 \u0443\u0431\u0440\u0430\u043D\u043E. \u0424\u043E\u043A\u0443\u0441 \u043E\u0441\u0442\u0430\u0432\u043B\u0435\u043D.",
            "FMclient. \u0421\u0442\u0440\u043E\u0433\u043E, \u0447\u0438\u0441\u0442\u043E, \u0431\u044B\u0441\u0442\u0440\u043E."
    };

    @Unique
    private static final String[] FMCLIENT_TAGLINES_EN = {
            "Clean build for a calm launch.",
            "A neat start without visual noise.",
            "Minimalist client for a clean game.",
            "Built for a quiet and stable entry.",
            "Clutter removed. Focus preserved.",
            "FMclient. Strict, clean, fast."
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

    @Unique
    private static String[] fmTaglines() {
        boolean ru = MinecraftClient.getInstance().options.language.startsWith("ru");
        return ru ? FMCLIENT_TAGLINES_RU : FMCLIENT_TAGLINES_EN;
    }

    @Unique private int fmTypewriterState = STATE_TYPING;
    @Unique private int fmTaglineIndex = 0;
    @Unique private int fmVisibleChars = 0;
    @Unique private long fmLastAnimationStepAt = 0L;

    // ── Language selector state ──
    @Unique private boolean fmLangOpen = false;
    @Unique private float fmLangAnim = 0.0f;
    @Unique private static final int LANG_BX = 6, LANG_BY = 6, LANG_BW = 26, LANG_BH = 14;
    @Unique private static final int LANG_DROP_W = 62, LANG_ITEM_H = 16;

    protected TitleScreenMixin() {
        super(new LiteralText("FMclient"));
    }

    // Remove vanilla splash text as early as possible
    @Inject(method = "init", at = @At("TAIL"))
    private void fmInit(CallbackInfo ci) {
        this.splashText = null;
        this.fmTypewriterState = STATE_TYPING;
        this.fmTaglineIndex = ThreadLocalRandom.current().nextInt(fmTaglines().length);
        this.fmVisibleChars = 0;
        this.fmLastAnimationStepAt = System.currentTimeMillis();
        this.fmLangOpen = false;
        this.fmLangAnim = 0.0f;

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
        this.textRenderer.draw(matrices, "v1.2.5  |  FMclient", 4f, H - 10f, 0xFF5B6874);
        this.fmDrawLangSelector(matrices, mouseX, mouseY);
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
        String[] taglines = fmTaglines();
        if (this.fmTaglineIndex >= taglines.length) this.fmTaglineIndex = 0;
        String currentTagline = taglines[this.fmTaglineIndex];

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
        String[] taglines = fmTaglines();
        if (this.fmTaglineIndex >= taglines.length) this.fmTaglineIndex = 0;
        String tagline = taglines[this.fmTaglineIndex];
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
        if (fmTaglines().length <= 1) {
            return 0;
        }

        int nextIndex = this.fmTaglineIndex;
        while (nextIndex == this.fmTaglineIndex) {
            nextIndex = ThreadLocalRandom.current().nextInt(fmTaglines().length);
        }
        return nextIndex;
    }

    // ── Language selector ────────────────────────────────────────────────────

    @Unique
    private void fmDrawLangSelector(MatrixStack matrices, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isRu = mc.options.language.startsWith("ru");
        String label = isRu ? "RU" : "EN";

        // button
        boolean hovBtn = mouseX >= LANG_BX && mouseX < LANG_BX + LANG_BW
                      && mouseY >= LANG_BY && mouseY < LANG_BY + LANG_BH;
        FMRenderer.roundedFill(matrices, LANG_BX, LANG_BY, LANG_BW, LANG_BH,
                hovBtn ? 0xB8272C33 : 0xA21D2127);
        FMRenderer.roundedBorder(matrices, LANG_BX, LANG_BY, LANG_BW, LANG_BH,
                hovBtn ? 0x9CDDE5EC : 0x5E737E8A);
        float labelX = LANG_BX + (LANG_BW - this.textRenderer.getWidth(label)) / 2.0f;
        this.textRenderer.draw(matrices, label, labelX, LANG_BY + 3, hovBtn ? 0xFFF0F3F6 : 0xFFD2D8DE);

        // animate dropdown
        float target = fmLangOpen ? 1.0f : 0.0f;
        fmLangAnim += (target - fmLangAnim) * 0.18f;
        if (Math.abs(fmLangAnim - target) < 0.01f) fmLangAnim = target;

        if (fmLangAnim > 0.01f) {
            int dropX = LANG_BX;
            int dropY = LANG_BY + LANG_BH + 3;
            int fullH = LANG_ITEM_H * 2 + 4; // 2 items + padding
            int visH = Math.max(4, (int)(fullH * fmLangAnim));
            int bgAlpha = (int)(fmLangAnim * 0xA2);
            int borderAlpha = (int)(fmLangAnim * 0x5E);

            // dropdown background
            FMRenderer.roundedFill(matrices, dropX, dropY, LANG_DROP_W, visH,
                    (bgAlpha << 24) | 0x1D2127);
            FMRenderer.roundedBorder(matrices, dropX, dropY, LANG_DROP_W, visH,
                    (borderAlpha << 24) | 0x737E8A);

            if (fmLangAnim > 0.4f) {
                float textFade = Math.min(1.0f, (fmLangAnim - 0.4f) / 0.6f);
                int textAlpha = (int)(textFade * 255);
                int textColor = (textAlpha << 24) | 0xD2D8DE;
                int textHover = (textAlpha << 24) | 0xF0F3F6;

                // English
                int enY = dropY + 2;
                boolean hovEn = fmLangAnim > 0.7f
                        && mouseX >= dropX && mouseX < dropX + LANG_DROP_W
                        && mouseY >= enY && mouseY < enY + LANG_ITEM_H;
                if (hovEn) {
                    FMRenderer.roundedFill(matrices, dropX + 2, enY, LANG_DROP_W - 4, LANG_ITEM_H,
                            ((int)(textFade * 0x20) << 24) | 0xFFFFFF);
                }
                this.textRenderer.draw(matrices, "English", dropX + 8, enY + 4,
                        hovEn ? textHover : textColor);

                // Russian
                int ruY = enY + LANG_ITEM_H;
                boolean hovRu = fmLangAnim > 0.7f
                        && mouseX >= dropX && mouseX < dropX + LANG_DROP_W
                        && mouseY >= ruY && mouseY < ruY + LANG_ITEM_H;
                if (hovRu) {
                    FMRenderer.roundedFill(matrices, dropX + 2, ruY, LANG_DROP_W - 4, LANG_ITEM_H,
                            ((int)(textFade * 0x20) << 24) | 0xFFFFFF);
                }
                this.textRenderer.draw(matrices, "\u0420\u0443\u0441\u0441\u043A\u0438\u0439", dropX + 8, ruY + 4,
                        hovRu ? textHover : textColor);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void fmLangClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;

        // click on language button
        if (mouseX >= LANG_BX && mouseX < LANG_BX + LANG_BW
                && mouseY >= LANG_BY && mouseY < LANG_BY + LANG_BH) {
            fmLangOpen = !fmLangOpen;
            cir.setReturnValue(true);
            return;
        }

        // click on dropdown items
        if (fmLangOpen && fmLangAnim > 0.7f) {
            int dropX = LANG_BX;
            int dropY = LANG_BY + LANG_BH + 3;
            int enY = dropY + 2;
            int ruY = enY + LANG_ITEM_H;

            if (mouseX >= dropX && mouseX < dropX + LANG_DROP_W) {
                if (mouseY >= enY && mouseY < enY + LANG_ITEM_H) {
                    fmSetLanguage("en_us");
                    fmLangOpen = false;
                    cir.setReturnValue(true);
                    return;
                }
                if (mouseY >= ruY && mouseY < ruY + LANG_ITEM_H) {
                    fmSetLanguage("ru_ru");
                    fmLangOpen = false;
                    cir.setReturnValue(true);
                    return;
                }
            }
            // click outside dropdown — close it
            fmLangOpen = false;
        }
    }

    @Unique
    private void fmSetLanguage(String code) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.options.language = code;
        for (LanguageDefinition def : mc.getLanguageManager().getAllLanguages()) {
            if (def.getCode().equals(code)) {
                mc.getLanguageManager().setLanguage(def);
                break;
            }
        }
        mc.reloadResources();
        mc.options.write();
    }
}
