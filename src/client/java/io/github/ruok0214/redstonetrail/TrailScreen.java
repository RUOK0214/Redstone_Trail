package io.github.ruok0214.redstonetrail;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TrailScreen extends TrailPanel {
    private Button record, radius, clear, snapshot, temporaryToggle;
    public TrailScreen(TrailManager manager) { this(null, manager); }
    public TrailScreen(Screen parent, TrailManager manager) { super("title", parent, manager); }
    static Component tr(String key, Object... args) { return Component.translatable("redstone_trail." + key, args); }
    @Override protected void init() {
        layout(400, 232);
        int half = (panelWidth - 26) / 2, right = 16 + half;
        record = button(tr(book.recording() ? "stop" : "start"), 10, 60, half, () -> {
            if (book.recording()) manager.stop(); else { manager.start(); minecraft.gui.setScreen(null); }
        });
        clear = button(tr("clear"), right, 60, half, () -> minecraft.gui.setScreen(new ConfirmScreen(yes -> {
            if (yes && manager.book() == book) manager.clear();
            minecraft.gui.setScreen(this);
        }, tr("clear_title"), tr("clear_hint"))));
        radius = addRenderableWidget(Button.builder(tr("radius", book.radius()), b -> {
            int value = book.radius(); book.setRadius(value == TrailBook.MAX_RADIUS ? 16 : value * 2);
            manager.save(); b.setMessage(tr("radius", book.radius()));
        }).bounds(left + 10, top + 86, half, 20).build());
        radius.setTooltip(Tooltip.create(tr("radius_hint")));
        button(tr("appearance"), right, 86, half, () -> minecraft.gui.setScreen(new AppearanceScreen(this, manager, null)))
                .active = manager.canRecord();
        addRenderableWidget(Button.builder(tr("visible", state(book.visible())), b -> {
            book.setVisible(!book.visible()); manager.save(); b.setMessage(tr("visible", state(book.visible())));
        }).bounds(left + 10, top + 112, half, 20).build()).active = manager.canRecord();
        addRenderableWidget(Button.builder(tr("walls", state(book.throughWalls())), b -> {
            book.setThroughWalls(!book.throughWalls()); manager.save(); b.setMessage(tr("walls", state(book.throughWalls())));
        }).bounds(left + right, top + 112, half, 20).build()).active = manager.canRecord();
        snapshot = button(tr("snapshot"), 10, 138, half, () -> minecraft.gui.setScreen(new SnapshotScreen(this, manager)));
        snapshot.setTooltip(Tooltip.create(tr("snapshot_limit")));
        button(tr("saved_title"), right, 138, half, () -> minecraft.gui.setScreen(new SavedTrailsScreen(this, manager)));
        temporaryToggle = button(temporaryLabel(), 10, 164, half,
                () -> manager.setTemporaryEnabled(!book.temporary().enabled()));
        button(tr("temporary_title"), right, 164, half,
                () -> minecraft.gui.setScreen(new TemporaryScreen(this, manager))).active = manager.canRecord();
        button(Component.translatable("gui.done"), 10, 206, panelWidth - 20, this::onClose);
        tick();
    }
    private static Component state(boolean on) { return Component.translatable(on ? "options.on" : "options.off"); }
    private Component temporaryLabel() { return tr("temporary_toggle", state(book.temporary().enabled())); }
    @Override public void onClose() { manager.save(); super.onClose(); }
    @Override public void tick() {
        super.tick();
        if (manager.book() != book) return;
        record.setMessage(tr(book.recording() ? "stop" : "start"));
        record.active = manager.canRecord(); radius.active = manager.canRecord() && !book.recording();
        clear.active = manager.canRecord() && (book.size() > 0 || book.recording());
        snapshot.active = manager.canRecord() && book.canSnapshot();
        temporaryToggle.active = manager.canRecord(); temporaryToggle.setMessage(temporaryLabel());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        String status = manager.error() != null ? manager.error() : book.limitReached() ? "limit" : book.recording() ? "recording" : "idle";
        g.centeredText(font, tr(status), width / 2, top + 26, book.recording() ? 0xFF8EE59B : 0xFFE2E6ED);
        g.centeredText(font, tr("dual_count", book.size(), book.temporarySize()), width / 2, top + 42, 0xFFBDC9D9);
        g.centeredText(font, tr("main_hint"), width / 2, top + 190, 0xFFBDC9D9);
    }
}
