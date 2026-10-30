package io.github.ruok0214.redstonetrail;

import java.util.Objects;

/** Shared preferences only; positions, recordings and comparison snapshots stay in their world. */
public record TrailSettings(int version, int radius, boolean visible, boolean throughWalls,
                            TrailBook.Style style, TrailBook.Temporary temporary, TrailBook.Style temporaryStyle) {
    public TrailSettings {
        if (version != 1 || !TrailBook.validRadius(radius)) throw new IllegalArgumentException("Invalid shared settings");
        Objects.requireNonNull(style); Objects.requireNonNull(temporary); Objects.requireNonNull(temporaryStyle);
    }
    public static TrailSettings from(TrailBook book) {
        return new TrailSettings(1, book.radius(), book.visible(), book.throughWalls(), book.style(), book.temporary(), book.temporaryStyle());
    }
    public void applyTo(TrailBook book) {
        book.setRadius(radius); book.setVisible(visible); book.setThroughWalls(throughWalls);
        book.setStyle(style); book.configureTemporary(temporary); book.setTemporaryStyle(temporaryStyle);
    }
}
