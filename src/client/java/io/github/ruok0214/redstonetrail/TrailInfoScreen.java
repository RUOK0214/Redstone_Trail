package io.github.ruok0214.redstonetrail;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** World-specific settings are available after joining a world. */
final class TrailInfoScreen extends Screen {
    private final Screen parent;
    TrailInfoScreen(Screen parent) { super(Component.literal("Redstone Trail")); this.parent = parent; }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, height - 32, 200, 20).build());
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 24, 0xFFFFB74D);
        int textWidth = Math.min(360, width - 40), y = 54;
        for (String key : new String[]{"redstone_trail.about_world", "redstone_trail.about_controls", "redstone_trail.about_saved"}) {
            for (var line : font.split(Component.translatable(key), textWidth)) {
                graphics.text(font, line, (width - textWidth) / 2, y, 0xFFE2E6ED); y += font.lineHeight + 3;
            }
            y += 10;
        }
    }
}
