package io.github.ruok0214.redstonetrail;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class SavedTrailsScreen extends TrailPanel {
    private int page, rows;
    private List<TrailBook.Saved> items = List.of();
    SavedTrailsScreen(Screen parent, TrailManager manager) { super("saved_title", parent, manager); }
    @Override protected void init() {
        layout(430, 330);
        items = book.saved(); rows = Math.max(1, (panelHeight - 94) / 44);
        int pages = Math.max(1, (items.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int bw = (panelWidth - 32) / 3;
        for (int row = 0; row < rows && page * rows + row < items.size(); row++) {
            var item = items.get(page * rows + row); int y = 53 + row * 44;
            var show = addRenderableWidget(Button.builder(tr(item.visible() ? "hide" : "show"), b -> {
                var current = book.saved(item.id()); book.showSaved(item.id(), !current.visible()); manager.save();
                b.setMessage(tr(book.saved(item.id()).visible() ? "hide" : "show"));
            }).bounds(left + 10, top + y, bw, 20).build());
            show.active = manager.canRecord();
            button(tr("appearance"), 16 + bw, y, bw, () -> minecraft.gui.setScreen(new AppearanceScreen(this, manager, item.id())))
                    .active = manager.canRecord();
            button(tr("delete"), 22 + bw * 2, y, bw, () -> minecraft.gui.setScreen(new ConfirmScreen(yes -> {
                if (yes && manager.book() == book) { book.deleteSaved(item.id()); manager.save(); }
                minecraft.gui.setScreen(this);
            }, tr("delete_title", item.name()), tr("delete_hint")))).active = manager.canRecord();
            show.setTooltip(Tooltip.create(Component.literal(item.name())));
        }
        button(Component.literal("<"), 10, panelHeight - 49, 50, () -> { page--; rebuildWidgets(); }).active = page > 0;
        button(Component.literal(">"), panelWidth - 60, panelHeight - 49, 50, () -> { page++; rebuildWidgets(); }).active = page < pages - 1;
        button(Component.translatable("gui.done"), 10, panelHeight - 25, panelWidth - 20, this::onClose);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.centeredText(font, tr("saved_count", book.saved().size(), TrailBook.MAX_SAVED), width / 2, top + 24, 0xFFBDC9D9);
        if (items.isEmpty()) g.centeredText(font, tr("no_saved"), width / 2, top + 65, 0xFFBDC9D9);
        for (int row = 0; row < rows && page * rows + row < items.size(); row++) {
            var item = book.saved(items.get(page * rows + row).id()); if (item == null) continue;
            int y0 = top + 40 + row * 44;
            g.fill(left + 10, y0, left + 18, y0 + 8, 0xFF000000 | item.style().color());
            String name = item.name();
            while (font.width(name) > panelWidth - 120 && !name.isEmpty()) name = name.substring(0, name.length() - 1);
            g.text(font, Component.literal(name), left + 23, y0, item.visible() ? 0xFFFFFFFF : 0xFF8993A0);
            g.text(font, tr("blocks", item.marks().size()), left + panelWidth - 85, y0, 0xFFBDC9D9);
        }
        g.centeredText(font, tr("page", page + 1, Math.max(1, (items.size() + rows - 1) / rows)), width / 2, top + panelHeight - 43, 0xFFBDC9D9);
    }
}
