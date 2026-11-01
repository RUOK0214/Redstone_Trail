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
        independence();
        snapshotsAndMigration();
        var style = new TrailBook.Style(0x12ABEF, 50, 7.5f);
        check(style.argb(.5) == 0x4012ABEF, "User transparency multiplies fade opacity and preserves RGB");
        check(new TrailBook.Style(0xFFFFFF, 100, 1).argb(1) >>> 24 == 0, "100 percent transparency hides fully");
        System.out.println("TrailFeaturesTest: independent controls, timing, comparisons and migration passed");
    }
    private static void timing(TrailBook.Fade fade) {
        var time = new AtomicLong(1000); var book = new TrailBook(time::get);
        book.configureTemporary(new TrailBook.Temporary(true, 2, fade, 1)); book.setTemporaryCenter(pos(0));
        check(book.changed(pos(0)) && book.size() == 0, "Temporary recording needs no persistent Start");
        time.set(2000); book.changed(pos(1));
        near(book.opacity(book.temporaryAt(pos(0))), fade == TrailBook.Fade.OVER_LIFETIME ? .5 : 1, "Lifetime behavior");
        time.set(3000);
        near(book.opacity(book.temporaryAt(pos(0))), fade == TrailBook.Fade.AFTER_DURATION ? 1 : 0, "Exact expiry boundary");
        time.set(3500);
        near(book.opacity(book.temporaryAt(pos(0))), fade == TrailBook.Fade.AFTER_DURATION ? .5 : 0, "After-expiry fade");
        book.expire();
        if (fade != TrailBook.Fade.AFTER_DURATION) check(book.temporaryAt(pos(0)) == null && book.temporaryAt(pos(1)) != null, "Earlier activations expire first");
        book.changed(pos(1));
        near(book.opacity(book.temporaryAt(pos(1))), 1, "Reactivation resets only this position");
        book.stop(); book.setVisible(false);
        time.set(5000); book.expire();
        check(book.temporaryAt(pos(0)) == null && book.temporaryAt(pos(1)) != null, "Persistent stop/hide does not disable the temporary overlay");
        time.set(7000); book.expire(); check(book.temporarySize() == 0, "Temporary marks expire");
        book.changed(pos(0));
        var restored = TrailBook.restore(book.data());
        check(restored.temporarySize() == 0 && restored.temporary().enabled(), "Restore preferences but never transient marks");
        time.set(17000); book.configureTemporary(new TrailBook.Temporary(true, 120, fade, 1));
        check(book.temporarySize() == 0, "Changing timing cannot resurrect expired live marks");
    }
    private static void independence() throws Exception {
        var time = new AtomicLong(0); var book = new TrailBook(time::get);
        check(!book.changed(pos(0)), "Both off ignores changes");
        book.start(pos(0)); book.changed(pos(0)); book.stop();
        check(book.size() == 1 && book.temporarySize() == 0, "Start controls only persistent recording");
        book.configureTemporary(new TrailBook.Temporary(true, 5, TrailBook.Fade.INSTANT, 1)); book.setTemporaryCenter(pos(0));
        book.changed(pos(1));
        check(book.size() == 1 && book.temporarySize() == 1 && !book.recording(), "Temporary alone works while persistent recording is stopped");
        book.start(pos(0)); book.changed(pos(2));
        check(book.size() == 2 && book.temporarySize() == 2, "Both collect the same new update independently");
        var saved = book.snapshot("Persistent only");
        check(saved.marks().size() == 2 && saved.marks().stream().noneMatch(m -> m.target().equals(pos(1))), "Snapshot excludes temporary-only marks");
        book.clear();
        check(book.size() == 0 && book.temporarySize() == 2 && book.temporary().enabled() && book.saved().size() == 1, "Clear affects only persistent recording");
        book.start(pos(0)); book.changed(pos(3));
        book.configureTemporary(new TrailBook.Temporary(false, 5, TrailBook.Fade.INSTANT, 1));
        check(book.temporarySize() == 0 && book.size() == 1 && book.recording(), "Temporary Off clears only temporary marks and leaves recording running");
        book.changed(pos(4));
        check(book.size() == 2 && book.temporarySize() == 0, "Persistent recording continues while temporary is off");
        book.configureTemporary(new TrailBook.Temporary(true, 5, TrailBook.Fade.INSTANT, 1)); book.setTemporaryCenter(pos(1000));
        book.changed(pos(1000)); book.changed(pos(0));
        check(book.size() == 3 && book.temporarySize() == 1 && book.at(pos(1000)) == null, "Independent fixed-recording and player-following centers");
        book.setStyle(new TrailBook.Style(0xFF6600, 15, 4));
        book.setTemporaryStyle(new TrailBook.Style(0x33DDFF, 40, 2));
        time.set(10000); book.expire();
        check(book.size() == 3 && book.temporarySize() == 0 && book.saved(saved.id()) != null, "Expiry never deletes current or saved persistent trails");
        var storage = new TrailStorage(Files.createTempDirectory("dual-trail-"), "world");
        storage.save(book); var restored = storage.load();
        check(restored.size() == 3 && restored.saved().size() == 1 && restored.temporarySize() == 0
                && restored.temporary().enabled() && !restored.recording()
                && restored.style().color() == 0xFF6600 && restored.temporaryStyle().color() == 0x33DDFF,
                "Both preferences and persistent marks roundtrip independently");
        var full = new TrailBook(time::get); full.setRadius(128); full.start(pos(0));
        for (int x = 0; x < 32; x++) for (int y = 80; y < 112; y++) for (int z = 0; z < 8; z++)
            full.changed(new TrailBook.Target("minecraft:overworld", x, y, z));
        full.configureTemporary(new TrailBook.Temporary(true, 5, TrailBook.Fade.INSTANT, 1)); full.setTemporaryCenter(pos(0));
        full.changed(pos(-1));
        check(full.limitReached() && !full.recording() && full.temporarySize() == 1 && full.temporary().enabled(), "Persistent capacity cannot stop the temporary overlay");
        for (int x = 0; x < 32; x++) for (int y = 80; y < 112; y++) for (int z = 0; z < 8; z++)
            full.changed(new TrailBook.Target("minecraft:overworld", x, y, z));
        check(full.temporarySize() == TrailBook.MAX_MARKS && full.temporaryAt(pos(-1)) == null,
                "Temporary capacity evicts the oldest activation and stays bounded");
        full.changed(pos(0)); full.changed(pos(-2));
        check(full.temporaryAt(pos(0)) != null && full.temporaryAt(new TrailBook.Target("minecraft:overworld", 0, 80, 1)) == null
                && full.size() == TrailBook.MAX_MARKS, "Refreshing a temporary position protects it from eviction without changing permanent marks");
    }
    private static void snapshotsAndMigration() throws Exception {
        var book = TrailBook.restore(new TrailBook.Data(1, 32, false, false, List.of(new TrailBook.Mark(pos(0), 3))));
        check(book.size() == 1 && book.style().equals(TrailBook.Style.DEFAULT) && book.saved().isEmpty()
                && !book.temporary().enabled() && !book.visible(), "Version 1 migrates existing marks");
        var first = book.snapshot("A");
        var oldV2 = new TrailBook.Data(2, 32, true, true, List.of(new TrailBook.Mark(pos(1), 2, 1234)), book.style(),
                new TrailBook.Temporary(true, 7, TrailBook.Fade.AFTER_DURATION, 2), List.of(first));
        var migrated = TrailBook.restore(oldV2);
        check(migrated.size() == 0 && migrated.saved().size() == 1 && !migrated.temporary().enabled()
                && migrated.temporary().seconds() == 7 && migrated.data().version() == 3,
                "Version 2 preserves comparisons and timing; new independent toggle starts off");
        var oldPersistent = TrailBook.restore(new TrailBook.Data(2, 32, true, true, List.of(new TrailBook.Mark(pos(0), 4)),
                book.style(), TrailBook.Temporary.DEFAULT, List.of(first)));
        check(oldPersistent.at(pos(0)).changes() == 4 && oldPersistent.saved().size() == 1 && !oldPersistent.temporary().enabled(),
                "Version 2 persistent paths and snapshots survive migration");
        var style = new TrailBook.Style(0x00FF88, 35, 6);
        book.setStyle(style); var second = book.snapshot("B");
        check(book.saved(first.id()).style().equals(TrailBook.Style.DEFAULT), "Current appearance does not mutate a snapshot");
        book.editSaved(first.id(), "A renamed", new TrailBook.Style(0xCC22FF, 20, 4));
        book.showSaved(second.id(), false); book.clear();
        check(book.size() == 0 && book.saved().size() == 2, "Clear current preserves every comparison");
        var storage = new TrailStorage(Files.createTempDirectory("trail-v3-"), "comparison");
        storage.save(book); var restored = storage.load();
        check(restored.style().equals(style) && restored.saved(first.id()).name().equals("A renamed")
                && restored.saved(first.id()).style().color() == 0xCC22FF && !restored.saved(second.id()).visible(), "Appearance, names and visibility persist");
        restored.deleteSaved(first.id());
        restored.start(pos(0)); restored.changed(pos(1));
        for (int i = 1; i < TrailBook.MAX_SAVED; i++) restored.snapshot("Copy " + i);
        check(!restored.canSnapshot(), "Snapshot count limit preserves old data");
    }
}
