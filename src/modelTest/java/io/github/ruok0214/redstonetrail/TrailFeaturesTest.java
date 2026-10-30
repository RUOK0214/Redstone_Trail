package io.github.ruok0214.redstonetrail;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class TrailFeaturesTest {
    private static TrailBook.Target pos(int x) { return new TrailBook.Target("minecraft:overworld", x, 80, 0); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void near(double actual, double expected, String message) { check(Math.abs(actual - expected) < .00001, message + ": " + actual); }
    static void run() throws Exception {
        for (var fade : TrailBook.Fade.values()) timing(fade);
        snapshotsAndMigration();
        var style = new TrailBook.Style(0x12ABEF, 50, 7.5f);
        check(style.argb(.5) == 0x4012ABEF, "User transparency multiplies fade opacity and preserves RGB");
        check(new TrailBook.Style(0xFFFFFF, 100, 1).argb(1) >>> 24 == 0, "100 percent transparency hides fully");
        boolean failed = false;
        try { new TrailBook.Style(0, 0, Float.NaN); } catch (IllegalArgumentException expected) { failed = true; }
        check(failed, "Invalid style is rejected");
        System.out.println("TrailFeaturesTest: timing, comparisons, appearance and migration passed");
    }
    private static void timing(TrailBook.Fade fade) {
        var time = new AtomicLong(1000); var book = new TrailBook(time::get);
        book.configureTemporary(new TrailBook.Temporary(true, 2, fade, 1)); book.start(pos(0)); book.changed(pos(0));
        time.set(2000); book.changed(pos(1));
        near(book.opacity(book.at(pos(0))), fade == TrailBook.Fade.OVER_LIFETIME ? .5 : 1, "Lifetime behavior");
        var saved = book.snapshot("Frozen " + fade);
        time.set(3000);
        near(book.opacity(book.at(pos(0))), fade == TrailBook.Fade.AFTER_DURATION ? 1 : 0, "Exact expiry boundary");
        time.set(3500);
        near(book.opacity(book.at(pos(0))), fade == TrailBook.Fade.AFTER_DURATION ? .5 : 0, "After-expiry fade");
        book.expire();
        if (fade != TrailBook.Fade.AFTER_DURATION) check(book.at(pos(0)) == null && book.at(pos(1)) != null, "Earlier activations expire first");
        book.changed(pos(1));
        near(book.opacity(book.at(pos(1))), 1, "Reactivation resets only this position");
        book.stop(); book.setVisible(false);
        time.set(5000); book.expire();
        check(book.at(pos(0)) == null && book.at(pos(1)) != null, "Stop and hide do not freeze time; reactivated block remains");
        time.set(7000); book.expire();
        check(book.size() == 0 && book.saved(saved.id()).marks().size() == 2, "Temporary marks expire, snapshots do not");
        book.start(pos(0)); book.changed(pos(0));
        check(TrailBook.restore(book.data()).size() == 0, "Transient current marks do not resurrect on reload");
        book.configureTemporary(new TrailBook.Temporary(false, 2, fade, 1));
        time.set(100000); book.expire(); check(book.size() == 1, "Switching to persistent freezes remaining marks");
        book.configureTemporary(new TrailBook.Temporary(true, 2, fade, 1));
        near(book.opacity(book.at(pos(0))), 1, "Enabling temporary mode starts existing marks now");
        time.set(110000); book.configureTemporary(new TrailBook.Temporary(true, 120, fade, 1));
        check(book.size() == 0, "Changing settings cannot resurrect expired marks");
    }
    private static void snapshotsAndMigration() throws Exception {
        var book = TrailBook.restore(new TrailBook.Data(1, 32, false, false, List.of(new TrailBook.Mark(pos(0), 3))));
        check(book.size() == 1 && book.style().equals(TrailBook.Style.DEFAULT) && book.saved().isEmpty()
                && !book.temporary().enabled() && !book.visible(), "Version 1 migrates marks, visibility and defaults");
        var first = book.snapshot("A");
        var style = new TrailBook.Style(0x00FF88, 35, 6);
        book.setStyle(style); var second = book.snapshot("B");
        check(book.saved(first.id()).style().equals(TrailBook.Style.DEFAULT), "Current appearance does not mutate a snapshot");
        book.editSaved(first.id(), "A renamed", new TrailBook.Style(0xCC22FF, 20, 4));
        book.showSaved(second.id(), false); book.clear();
        check(book.size() == 0 && book.saved().size() == 2, "Clear current preserves every comparison");
        var storage = new TrailStorage(Files.createTempDirectory("trail-v2-"), "comparison");
        storage.save(book); var restored = storage.load();
        check(restored.style().equals(style) && restored.saved(first.id()).name().equals("A renamed")
                && restored.saved(first.id()).style().color() == 0xCC22FF && !restored.saved(second.id()).visible(), "Style, names and visibility roundtrip independently");
        restored.deleteSaved(first.id()); check(restored.saved().size() == 1 && restored.saved(second.id()) != null, "Delete affects only the selected trail");
        restored.start(pos(0)); restored.changed(pos(1));
        for (int i = 1; i < TrailBook.MAX_SAVED; i++) restored.snapshot("Copy " + i);
        check(!restored.canSnapshot(), "Snapshot count limit prevents overflow without losing old snapshots");
        boolean failed = false;
        try { restored.snapshot("Overflow"); } catch (IllegalStateException expected) { failed = true; }
        check(failed && restored.saved().size() == TrailBook.MAX_SAVED, "Overflow leaves saved data intact");
    }
}
