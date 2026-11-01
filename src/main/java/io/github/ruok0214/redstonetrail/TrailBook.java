package io.github.ruok0214.redstonetrail;

import java.util.*;
import java.util.function.LongSupplier;

/** Independent persistent recording and live temporary display, plus comparison snapshots. */
public final class TrailBook {
    public static final int MAX_MARKS = 8192, MAX_SAVED = 16, MAX_SAVED_MARKS = 32768;
    public static final int MAX_RADIUS = 512;
    public record Target(String dimension, int x, int y, int z) { }
    public record Mark(Target target, long changes, long activatedAt) {
        public Mark(Target target, long changes) { this(target, changes, 0); }
    }
    public record Style(int color, int transparency, float width) {
        public static final Style DEFAULT = new Style(0xFFB74D, 0, 2.5f);
        public static final Style TEMPORARY_DEFAULT = new Style(0x63D9FF, 0, 2.5f);
        public Style {
            if (color < 0 || color > 0xFFFFFF || transparency < 0 || transparency > 100
                    || !Float.isFinite(width) || width < 1 || width > 10) throw new IllegalArgumentException("Invalid style");
        }
        public int argb(double fade) { return ((int)Math.round(255 * (1 - transparency / 100d) * Math.clamp(fade, 0, 1)) << 24) | color; }
    }
    public enum Fade { OVER_LIFETIME, INSTANT, AFTER_DURATION }
    public record Temporary(boolean enabled, double seconds, Fade fade, double fadeSeconds) {
        public static final Temporary DEFAULT = new Temporary(false, 5, Fade.OVER_LIFETIME, 1);
        public Temporary {
            if (!Double.isFinite(seconds) || seconds < .5 || seconds > 120 || fade == null
                    || !Double.isFinite(fadeSeconds) || fadeSeconds < .1 || fadeSeconds > 10)
                throw new IllegalArgumentException("Invalid temporary settings");
        }
        public double opacity(long ageMillis) {
            if (!enabled) return 1;
            double age = Math.max(0, ageMillis) / 1000d;
            return Math.clamp(switch (fade) {
                case OVER_LIFETIME -> 1 - age / seconds;
                case INSTANT -> age < seconds ? 1 : 0;
                case AFTER_DURATION -> age <= seconds ? 1 : 1 - (age - seconds) / fadeSeconds;
            }, 0, 1);
        }
    }
    public record Saved(UUID id, String name, boolean visible, Style style, List<Mark> marks) {
        public Saved { marks = List.copyOf(marks); }
    }
    // Keep the version 1 fields readable for lossless migration.
    public record Data(int version, int radius, boolean visible, boolean throughWalls, List<Mark> marks,
                       Style style, Temporary temporary, List<Saved> saved, Style temporaryStyle) {
        public Data(int version, int radius, boolean visible, boolean throughWalls, List<Mark> marks,
                    Style style, Temporary temporary, List<Saved> saved) {
            this(version, radius, visible, throughWalls, marks, style, temporary, saved, null);
        }
        public Data(int version, int radius, boolean visible, boolean throughWalls, List<Mark> marks) {
            this(version, radius, visible, throughWalls, marks, null, null, null, null);
        }
    }
    private final Map<Target, Mark> marks = new LinkedHashMap<>();
    private final Map<UUID, Saved> saved = new LinkedHashMap<>();
    private final LinkedHashMap<Target, Mark> temporaryMarks = new LinkedHashMap<>();
    private final LongSupplier clock;
    private Target center, temporaryCenter;
    private int radius = 64;
    private boolean recording, limitReached, visible = true, throughWalls = true;
    private Style style = Style.DEFAULT, temporaryStyle = Style.TEMPORARY_DEFAULT;
    private Temporary temporary = Temporary.DEFAULT;
    private long revision;

    public TrailBook() { this(() -> System.nanoTime() / 1_000_000); }
    public TrailBook(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }
    public Collection<Mark> marks() { return List.copyOf(marks.values()); }
    public Mark at(Target target) { return marks.get(target); }
    public Collection<Mark> temporaryMarks() { return List.copyOf(temporaryMarks.values()); }
    public Mark temporaryAt(Target target) { return temporaryMarks.get(target); }
    public int temporarySize() { return temporaryMarks.size(); }
    public void setTemporaryCenter(Target origin) { temporaryCenter = origin; }
    public void clearTemporary() { temporaryMarks.clear(); temporaryCenter = null; }
    public Style temporaryStyle() { return temporaryStyle; }
    public void setTemporaryStyle(Style value) {
        value = Objects.requireNonNull(value); if (!temporaryStyle.equals(value)) { temporaryStyle = value; revision++; }
    }
    public List<Saved> saved() { return List.copyOf(saved.values()); }
    public Saved saved(UUID id) { return saved.get(id); }
    public int savedMarkCount() { return saved.values().stream().mapToInt(item -> item.marks().size()).sum(); }
    public boolean canSnapshot() { return size() > 0 && saved.size() < MAX_SAVED && savedMarkCount() + size() <= MAX_SAVED_MARKS; }
    public int size() { return marks.size(); }
    public int radius() { return radius; }
    public boolean recording() { return recording; }
    public boolean limitReached() { return limitReached; }
    public boolean visible() { return visible; }
    public boolean throughWalls() { return throughWalls; }
    public Target center() { return center; }
    public long revision() { return revision; }
    public Style style() { return style; }
    public Temporary temporary() { return temporary; }
    public void setStyle(Style value) { value = Objects.requireNonNull(value); if (!style.equals(value)) { style = value; revision++; } }
    public void configureTemporary(Temporary value) {
        Objects.requireNonNull(value);
        expire();
        if (!temporary.equals(value)) { temporary = value; revision++; }
        if (!value.enabled()) clearTemporary();
        expire();
    }
    public double opacity(Mark mark) { return temporary.opacity(Math.max(0, clock.getAsLong() - mark.activatedAt())); }
    public void expire() {
        if (!temporary.enabled()) return;
        long now = clock.getAsLong();
        temporaryMarks.values().removeIf(mark -> temporary.opacity(Math.max(0, now - mark.activatedAt())) <= 0);
    }
    public Saved snapshot(String name) {
        if (!canSnapshot()) throw new IllegalStateException("Snapshot limit or empty trail");
        Saved item = new Saved(UUID.randomUUID(), cleanName(name), true, style,
                marks.values().stream().map(mark -> new Mark(mark.target(), mark.changes())).toList());
        saved.put(item.id(), item); revision++; return item;
    }
    public void editSaved(UUID id, String name, Style appearance) {
        Saved old = Objects.requireNonNull(saved.get(id), "Missing saved trail");
        saved.put(id, new Saved(id, cleanName(name), old.visible(), Objects.requireNonNull(appearance), old.marks())); revision++;
    }
    public void showSaved(UUID id, boolean show) {
        Saved old = Objects.requireNonNull(saved.get(id), "Missing saved trail");
        if (old.visible() != show) { saved.put(id, new Saved(id, old.name(), show, old.style(), old.marks())); revision++; }
    }
    public void deleteSaved(UUID id) { if (saved.remove(id) != null) revision++; }
    private static String cleanName(String value) {
        String name = Objects.requireNonNull(value).strip();
        if (name.isEmpty() || name.length() > 32 || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid name");
        return name;
    }
    public void setRadius(int value) {
        if (recording) throw new IllegalStateException("Stop before changing the radius");
        if (!validRadius(value)) throw new IllegalArgumentException("radius");
        if (radius != value) { radius = value; revision++; }
    }
    public static boolean validRadius(int value) { return value >= 16 && value <= MAX_RADIUS && Integer.bitCount(value) == 1; }
    public void setVisible(boolean value) { if (visible != value) { visible = value; revision++; } }
    public void setThroughWalls(boolean value) { if (throughWalls != value) { throughWalls = value; revision++; } }
    public void start(Target origin) { center = Objects.requireNonNull(origin); recording = true; limitReached = false; }
    public void stop() { recording = false; }
    /** Saved comparisons are removed only by their explicit delete action. */
    public void clear() { stop(); marks.clear(); center = null; limitReached = false; revision++; }
    private boolean inRange(Target origin, Target target) {
        if (origin == null || !origin.dimension().equals(target.dimension())) return false;
        double dx = (double)target.x() - origin.x(), dy = (double)target.y() - origin.y(), dz = (double)target.z() - origin.z();
        return dx * dx + dy * dy + dz * dz <= (double)radius * radius;
    }
    public boolean accepts(Target target) {
        return (recording && inRange(center, target)) || (temporary.enabled() && inRange(temporaryCenter, target));
    }
    public boolean changed(Target target) {
        boolean captured = false;
        if (recording && inRange(center, target)) {
            var old = marks.get(target);
            if (old == null && marks.size() >= MAX_MARKS) { limitReached = true; stop(); }
            else {
                marks.put(target, new Mark(target, old == null ? 1 : Math.min(Long.MAX_VALUE - 1, old.changes()) + 1));
                revision++; captured = true;
            }
        }
        if (temporary.enabled() && inRange(temporaryCenter, target)) {
            var old = temporaryMarks.remove(target);
            // The live overlay remains bounded without stopping persistent recording.
            if (old == null && temporaryMarks.size() >= MAX_MARKS) temporaryMarks.pollFirstEntry();
            temporaryMarks.put(target, new Mark(target, old == null ? 1 : Math.min(Long.MAX_VALUE - 1, old.changes()) + 1, clock.getAsLong()));
            captured = true;
        }
        return captured;
    }
    public Data data() {
        return new Data(3, radius, visible, throughWalls, List.copyOf(marks.values()), style, temporary, saved(), temporaryStyle);
    }
    public static TrailBook restore(Data data) {
        if (data == null || (data.version() < 1 || data.version() > 3) || data.marks() == null) throw new IllegalArgumentException("Invalid trail file");
        var book = new TrailBook();
        book.setRadius(data.radius()); book.visible = data.visible(); book.throughWalls = data.throughWalls();
        validateMarks(data.marks());
        if (data.version() >= 2) {
            book.style = Objects.requireNonNull(data.style());
            Temporary timing = Objects.requireNonNull(data.temporary());
            book.temporary = new Temporary(data.version() >= 3 && timing.enabled(), timing.seconds(), timing.fade(), timing.fadeSeconds());
            book.temporaryStyle = data.version() >= 3 ? Objects.requireNonNull(data.temporaryStyle())
                    : timing.enabled() ? book.style : Style.TEMPORARY_DEFAULT;
            if (data.saved() == null || data.saved().size() > MAX_SAVED) throw new IllegalArgumentException("Invalid saved trails");
            for (var item : data.saved()) {
                if (item == null || item.id() == null || item.style() == null) throw new IllegalArgumentException("Invalid saved trail");
                validateMarks(item.marks()); cleanName(item.name());
                if (book.saved.putIfAbsent(item.id(), item) != null) throw new IllegalArgumentException("Duplicate saved trail");
            }
            if (book.savedMarkCount() > MAX_SAVED_MARKS) throw new IllegalArgumentException("Too many saved marks");
        }
        boolean oldTransient = data.version() == 2 && data.temporary().enabled();
        if (!oldTransient) for (var mark : data.marks())
            book.marks.put(mark.target(), new Mark(mark.target(), mark.changes()));
        book.revision = 0;
        return book;
    }
    private static void validateMarks(List<Mark> items) {
        if (items == null || items.size() > MAX_MARKS) throw new IllegalArgumentException("Invalid marks");
        Set<Target> positions = new HashSet<>();
        for (var mark : items) {
            if (mark == null || mark.target() == null || mark.target().dimension() == null
                    || mark.target().dimension().isBlank() || mark.changes() < 1 || !positions.add(mark.target()))
                throw new IllegalArgumentException("Invalid mark");
        }
    }
}
