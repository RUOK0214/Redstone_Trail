package io.github.ruok0214.redstonetrail;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;
import java.util.UUID;

/** Apply changes to either the live trail or one saved comparison; Cancel leaves both untouched. */
public final class AppearanceScreen extends TrailPanel {
    private final UUID savedId;
    private final SettingSlider[] channels = new SettingSlider[3];
    private float lineWidth;
    private int color, transparency;
    private String hexText, nameText;
    private EditBox hex, name;
    private Button apply;
    private boolean syncing;
    AppearanceScreen(Screen parent, TrailManager manager, UUID savedId) {
        super("appearance", parent, manager); this.savedId = savedId;
        var style = savedId == null ? book.style() : book.saved(savedId).style();
        color = style.color(); lineWidth = style.width(); transparency = style.transparency();
        hexText = String.format(Locale.ROOT, "#%06X", color);
        nameText = savedId == null ? tr("current").getString() : book.saved(savedId).name();
    }
    @Override protected void init() {
        layout(380, 232);
        int inner = panelWidth - 20, half = (inner - 6) / 2;
        name = addRenderableWidget(new EditBox(font, left + 80, top + 26, panelWidth - 90, 18, tr("name")));
        name.setMaxLength(32); name.setValue(nameText); name.setEditable(savedId != null);
        name.setResponder(value -> { nameText = value; valid(); });
        addRenderableWidget(new SettingSlider(left + 10, top + 50, half, 0, 100, 1, transparency,
                value -> tr("transparency", (int)value), value -> transparency = (int)value));
        addRenderableWidget(new SettingSlider(left + 16 + half, top + 50, half, 1, 10, .5, lineWidth,
                value -> tr("line_width", String.format(Locale.ROOT, "%.1f", value)), value -> lineWidth = (float)value));
        hex = addRenderableWidget(new EditBox(font, left + 80, top + 76, panelWidth - 90, 20, tr("hex")));
        hex.setMaxLength(7); hex.setValue(hexText);
        hex.setResponder(value -> {
            if (syncing) return;
            hexText = value;
            if (validHex()) { color = Integer.parseInt(value.replace("#", ""), 16); syncChannels(); }
            valid();
        });
        String[] keys = {"red", "green", "blue"};
        for (int i = 0; i < 3; i++) {
            int shift = 16 - i * 8; String key = keys[i];
            channels[i] = addRenderableWidget(new SettingSlider(left + 10, top + 102 + i * 24, inner, 0, 255, 1,
                    (color >> shift) & 255, value -> tr(key, (int)value), value -> setColor((color & ~(255 << shift)) | ((int)value << shift))));
        }
        apply = button(tr("apply"), 10, 206, half, () -> {
            if (!validHex() || nameText.isBlank() || manager.book() != book) return;
            var style = new TrailBook.Style(color, transparency, lineWidth);
            if (savedId == null) book.setStyle(style); else book.editSaved(savedId, nameText, style);
            manager.save(); onClose();
        });
        button(Component.translatable("gui.cancel"), 16 + half, 206, half, this::onClose);
        valid();
    }
    private boolean validHex() { return hexText.matches("#?[0-9a-fA-F]{6}"); }
    private void valid() {
        if (apply != null) apply.active = manager.canRecord() && validHex() && !nameText.isBlank();
        if (hex != null) hex.setTextColor(validHex() ? 0xFFFFFFFF : 0xFFFF7777);
    }
    private void setColor(int value) {
        color = value; hexText = String.format(Locale.ROOT, "#%06X", color);
        syncing = true; hex.setValue(hexText); syncing = false; syncChannels(); valid();
    }
    private void syncChannels() {
        for (int i = 0; i < 3; i++) if (channels[i] != null) channels[i].showSetting((color >> (16 - i * 8)) & 255);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.text(font, tr("name"), left + 10, top + 31, 0xFFBDC9D9);
        g.text(font, tr("hex"), left + 10, top + 82, 0xFFBDC9D9);
        int px = left + 10, py = top + 178, w = panelWidth - 20, h = 20, t = Math.round(lineWidth);
        g.fill(px, py, px + w, py + h, 0xFF11151B);
        int argb = new TrailBook.Style(color, transparency, lineWidth).argb(1);
        g.fill(px, py, px + w, py + t, argb); g.fill(px, py + h - t, px + w, py + h, argb);
        g.fill(px, py, px + t, py + h, argb); g.fill(px + w - t, py, px + w, py + h, argb);
        g.centeredText(font, tr(validHex() ? "preview" : "invalid_hex"), width / 2, py + 6, 0xFFFFFFFF);
    }
}
