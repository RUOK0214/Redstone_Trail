package io.github.ruok0214.redstonetrail;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class TrailPanel extends Screen {
    protected final Screen parent;
    protected final TrailManager manager;
    protected final TrailBook book;
    protected int left, top, panelWidth, panelHeight;
    TrailPanel(String title, Screen parent, TrailManager manager) {
        super(tr(title)); this.parent = parent; this.manager = manager; this.book = manager.book();
    }
    static Component tr(String key, Object... args) { return TrailScreen.tr(key, args); }
    protected void layout(int preferredWidth, int preferredHeight) {
        panelWidth = Math.min(preferredWidth, width - 16); panelHeight = Math.min(preferredHeight, height - 8);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
    }
    protected Button button(Component label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(label, b -> action.run()).bounds(left + x, top + y, w, 20).build());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void tick() { if (manager.book() != book) minecraft.gui.setScreen(null); }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractBackground(g, x, y, delta);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xEC20242C);
        g.outline(left, top, panelWidth, panelHeight, 0xFFFFB74D);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.centeredText(font, title, width / 2, top + 8, 0xFFFFB74D);
    }
}
