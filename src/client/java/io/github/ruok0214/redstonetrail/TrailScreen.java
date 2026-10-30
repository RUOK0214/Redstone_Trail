package io.github.ruok0214.redstonetrail;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TrailScreen extends Screen {
    private final TrailManager manager;
    private int left, top, panelWidth;
    private Button record, radius, clear;
    public TrailScreen(TrailManager manager) { super(tr("title")); this.manager = manager; }
    static Component tr(String key, Object... args) { return Component.translatable("redstone_trail." + key, args); }
    @Override protected void init() {
        panelWidth = Math.min(360, width - 16); left = (width - panelWidth) / 2; top = Math.max(4, (height - 224) / 2);
        int half = (panelWidth - 26) / 2;
        record = addRenderableWidget(Button.builder(tr(manager.book().recording() ? "stop" : "start"), b -> {
            if (manager.book().recording()) manager.stop();
            else { manager.start(); onClose(); }
        }).bounds(left + 10, top + 60, half, 20).build());
        clear = addRenderableWidget(Button.builder(tr("clear"), b -> minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) manager.clear();
            minecraft.gui.setScreen(this);
        }, tr("clear_title"), tr("clear_hint")))).bounds(left + 16 + half, top + 60, half, 20).build());
        radius = addRenderableWidget(Button.builder(tr("radius", manager.book().radius()), b -> {
            int value = manager.book().radius(); manager.book().setRadius(value == 128 ? 16 : value * 2);
            manager.save(); b.setMessage(tr("radius", manager.book().radius()));
        }).bounds(left + 10, top + 86, panelWidth - 20, 20).build());
        radius.setTooltip(Tooltip.create(tr("radius_hint")));
        addRenderableWidget(Button.builder(tr("visible", state(manager.book().visible())), b -> {
            manager.book().setVisible(!manager.book().visible()); manager.save();
            b.setMessage(tr("visible", state(manager.book().visible())));
        }).bounds(left + 10, top + 112, half, 20).build());
        addRenderableWidget(Button.builder(tr("walls", state(manager.book().throughWalls())), b -> {
            manager.book().setThroughWalls(!manager.book().throughWalls()); manager.save();
            b.setMessage(tr("walls", state(manager.book().throughWalls())));
        }).bounds(left + 16 + half, top + 112, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + 10, top + 199, panelWidth - 20, 20).build());
        tick();
    }
    private static Component state(boolean on) { return Component.translatable(on ? "options.on" : "options.off"); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { manager.save(); minecraft.gui.setScreen(null); }
    @Override public void tick() {
        if (manager.book() == null) { onClose(); return; }
        record.setMessage(tr(manager.book().recording() ? "stop" : "start"));
        record.active = manager.canRecord(); radius.active = manager.canRecord() && !manager.book().recording();
        clear.active = manager.canRecord() && (manager.book().size() > 0 || manager.book().recording());
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        super.extractBackground(graphics, x, y, delta);
        graphics.fill(left, top, left + panelWidth, top + 224, 0xEC20242C);
        graphics.outline(left, top, panelWidth, 224, 0xFFFFB74D);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        super.extractRenderState(graphics, x, y, delta);
        var book = manager.book(); if (book == null) return;
        graphics.centeredText(font, title, width / 2, top + 8, 0xFFFFB74D);
        String status = manager.error() != null ? manager.error() : book.limitReached() ? "limit" : book.recording() ? "recording" : "idle";
        graphics.centeredText(font, tr(status), width / 2, top + 26, book.recording() ? 0xFF8EE59B : 0xFFE2E6ED);
        graphics.centeredText(font, tr("count", book.size(), TrailBook.MAX_MARKS), width / 2, top + 42, 0xFFBDC9D9);
        for (int i = 0; i < 4; i++) graphics.text(font, tr("hint" + i), left + 10, top + 141 + i * 12, 0xFFBDC9D9);
    }
}
