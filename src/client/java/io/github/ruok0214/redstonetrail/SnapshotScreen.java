package io.github.ruok0214.redstonetrail;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SnapshotScreen extends TrailPanel {
    private String nameText;
    private Button save;
    SnapshotScreen(Screen parent, TrailManager manager) {
        super("snapshot", parent, manager); nameText = tr("default_name", book.saved().size() + 1).getString();
    }
    @Override protected void init() {
        layout(400, 156);
        var name = addRenderableWidget(new EditBox(font, left + 10, top + 47, panelWidth - 20, 20, tr("name")));
        name.setMaxLength(32); name.setValue(nameText); name.setResponder(value -> { nameText = value; update(); });
        int half = (panelWidth - 26) / 2;
        save = button(tr("save_snapshot"), 10, 126, half, () -> {
            if (nameText.isBlank() || manager.book() != book) return;
            book.expire();
            if (!book.canSnapshot()) { update(); return; }
            book.snapshot(nameText); manager.save(); onClose();
        });
        button(Component.translatable("gui.cancel"), 16 + half, 126, half, this::onClose);
        update();
    }
    private void update() { if (save != null) save.active = manager.canRecord() && !nameText.isBlank() && book.canSnapshot(); }
    @Override public void tick() { super.tick(); update(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.text(font, tr("name"), left + 10, top + 31, 0xFFBDC9D9);
        g.text(font, tr("snapshot_hint0"), left + 10, top + 79, 0xFFBDC9D9);
        g.text(font, tr("snapshot_hint1"), left + 10, top + 93, 0xFFBDC9D9);
        g.text(font, tr(book.canSnapshot() ? "snapshot_count" : "snapshot_limit", book.size()), left + 10, top + 109, 0xFFFFD49A);
    }
}
