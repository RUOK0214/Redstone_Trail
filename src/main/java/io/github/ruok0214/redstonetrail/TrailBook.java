package io.github.ruok0214.redstonetrail;

import java.util.*;

/** Persistent changed positions. Recording itself is deliberately session-only. */
public final class TrailBook {
    public static final int MAX_MARKS = 8192;
    public record Target(String dimension, int x, int y, int z) { }
    public record Mark(Target target, long changes) { }
    public record Data(int version, int radius, boolean visible, boolean throughWalls, List<Mark> marks) { }
    private final Map<Target, Mark> marks = new LinkedHashMap<>();
    private Target center;
    private int radius = 64;
    private boolean recording, limitReached, visible = true, throughWalls = true;
    private long revision;

    public Collection<Mark> marks() { return List.copyOf(marks.values()); }
    public Mark at(Target target) { return marks.get(target); }
    public int size() { return marks.size(); }
    public int radius() { return radius; }
    public boolean recording() { return recording; }
    public boolean limitReached() { return limitReached; }
    public boolean visible() { return visible; }
    public boolean throughWalls() { return throughWalls; }
    public Target center() { return center; }
    public long revision() { return revision; }
    public void setRadius(int value) {
        if (recording) throw new IllegalStateException("Stop before changing the radius");
        if (value != 16 && value != 32 && value != 64 && value != 128) throw new IllegalArgumentException("radius");
        if (radius != value) { radius = value; revision++; }
    }
    public void setVisible(boolean value) { if (visible != value) { visible = value; revision++; } }
    public void setThroughWalls(boolean value) { if (throughWalls != value) { throughWalls = value; revision++; } }
    public void start(Target origin) { center = Objects.requireNonNull(origin); recording = true; limitReached = false; }
    public void stop() { recording = false; }
    public void clear() { stop(); marks.clear(); center = null; limitReached = false; revision++; }
    public boolean accepts(Target target) {
        if (!recording || center == null || !center.dimension().equals(target.dimension())) return false;
        double dx = (double)target.x() - center.x(), dy = (double)target.y() - center.y(), dz = (double)target.z() - center.z();
        return dx * dx + dy * dy + dz * dz <= (double)radius * radius;
    }
    public boolean changed(Target target) {
        if (!accepts(target)) return false;
        var old = marks.get(target);
        if (old == null && marks.size() >= MAX_MARKS) { limitReached = true; stop(); return false; }
        marks.put(target, new Mark(target, old == null ? 1 : Math.min(Long.MAX_VALUE - 1, old.changes()) + 1));
        revision++;
        return true;
    }
    public Data data() { return new Data(1, radius, visible, throughWalls, List.copyOf(marks.values())); }
    public static TrailBook restore(Data data) {
        if (data == null || data.version() != 1 || data.marks() == null || data.marks().size() > MAX_MARKS)
            throw new IllegalArgumentException("Invalid trail file");
        var book = new TrailBook();
        book.setRadius(data.radius()); book.visible = data.visible(); book.throughWalls = data.throughWalls();
        for (var mark : data.marks()) {
            if (mark == null || mark.target() == null || mark.target().dimension() == null
                    || mark.target().dimension().isBlank() || mark.changes() < 1
                    || book.marks.putIfAbsent(mark.target(), mark) != null) throw new IllegalArgumentException("Invalid mark");
        }
        book.revision = 0;
        return book;
    }
}
