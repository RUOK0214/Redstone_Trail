package io.github.ruok0214.redstonetrail;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

final class SettingSlider extends AbstractSliderButton {
    private final double minimum, maximum, step;
    private final DoubleFunction<Component> label;
    private final DoubleConsumer changed;

    SettingSlider(int x, int y, int width, double minimum, double maximum, double step, double initial,
                  DoubleFunction<Component> label, DoubleConsumer changed) {
        super(x, y, width, 20, Component.empty(), (initial - minimum) / (maximum - minimum));
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.label = label;
        this.changed = changed;
        updateMessage();
    }

    void showSetting(double setting) {
        value = Math.clamp((setting - minimum) / (maximum - minimum), 0, 1);
        updateMessage();
    }

    private double settingValue() {
        return Math.clamp(minimum + Math.round(value * (maximum - minimum) / step) * step, minimum, maximum);
    }

    @Override protected void updateMessage() { setMessage(label.apply(settingValue())); }
    @Override protected void applyValue() { changed.accept(settingValue()); }
}
