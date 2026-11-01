package io.github.ruok0214.redstonetrail;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;

public final class TemporaryScreen extends TrailPanel {
    private double seconds, fadeSeconds;
    private TrailBook.Fade fade;
    private SettingSlider tail;
    TemporaryScreen(Screen parent, TrailManager manager) {
        super("temporary_title", parent, manager);
        var settings = book.temporary(); seconds = settings.seconds(); fade = settings.fade(); fadeSeconds = settings.fadeSeconds();
    }
    @Override protected void init() {
        layout(400, 232);
        int inner = panelWidth - 20, half = (inner - 6) / 2;
        button(tr("temporary_appearance"), 10, 28, inner,
                () -> minecraft.gui.setScreen(new AppearanceScreen(this, manager, null, true))).active = manager.canRecord();
        addRenderableWidget(new SettingSlider(left + 10, top + 54, inner, .5, 120, .5, seconds,
                value -> tr("lifetime", String.format(Locale.ROOT, "%.1f", value)), value -> seconds = value));
        addRenderableWidget(Button.builder(fadeLabel(), b -> {
            fade = TrailBook.Fade.values()[(fade.ordinal() + 1) % TrailBook.Fade.values().length];
            b.setMessage(fadeLabel()); tail.active = fade == TrailBook.Fade.AFTER_DURATION;
        }).bounds(left + 10, top + 80, inner, 20).build());
        tail = addRenderableWidget(new SettingSlider(left + 10, top + 106, inner, .1, 10, .1, fadeSeconds,
                value -> tr("fade_seconds", String.format(Locale.ROOT, "%.1f", value)), value -> fadeSeconds = value));
        tail.active = fade == TrailBook.Fade.AFTER_DURATION;
        button(tr("apply"), 10, 206, half, () -> {
            if (manager.book() != book || !manager.canRecord()) return;
            book.configureTemporary(new TrailBook.Temporary(book.temporary().enabled(), seconds, fade, fadeSeconds)); manager.save(); onClose();
        }).active = manager.canRecord();
        button(Component.translatable("gui.cancel"), 16 + half, 206, half, this::onClose);
    }
    private Component fadeLabel() { return tr("fade." + fade.name().toLowerCase(Locale.ROOT)); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.text(font, tr("fade_hint." + fade.name().toLowerCase(Locale.ROOT)), left + 10, top + 138, 0xFFFFD49A);
        g.text(font, tr("temporary_hint0"), left + 10, top + 157, 0xFFBDC9D9);
        g.text(font, tr("temporary_hint1"), left + 10, top + 171, 0xFFBDC9D9);
        g.text(font, tr("temporary_hint2"), left + 10, top + 185, 0xFFBDC9D9);
    }
}
