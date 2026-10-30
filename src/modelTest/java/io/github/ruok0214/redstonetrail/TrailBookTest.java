package io.github.ruok0214.redstonetrail;

import java.nio.file.Files;
import java.util.List;

public final class TrailBookTest {
    private static TrailBook.Target pos(int x, int y, int z) { return new TrailBook.Target("minecraft:overworld", x, y, z); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var book = new TrailBook();
        check(!book.changed(pos(1, 0, 0)), "No capture before start");
        book.start(pos(0, 0, 0));
        check(book.changed(pos(64, 0, 0)), "Radius boundary included");
        check(!book.changed(pos(64, 1, 0)) && !book.changed(pos(65, 0, 0)), "Spherical radius excludes outside");
        check(!book.changed(new TrailBook.Target("minecraft:the_nether", 0, 0, 0)), "Other dimension excluded");
        book.changed(pos(64, 0, 0));
        check(book.size() == 1 && book.at(pos(64, 0, 0)).changes() == 2, "Repeated changes merge by position");
        book.stop(); check(!book.changed(pos(0, 0, 0)) && book.size() == 1, "Stop keeps trail but ignores events");
        book.start(pos(1000, 0, 0)); book.changed(pos(1000, 0, 0));
        check(book.size() == 2, "New start adds to retained trail");
        book.setVisible(false); book.setThroughWalls(false);
        var storage = new TrailStorage(Files.createTempDirectory("redstone-trail-test-"), "test-world");
        storage.save(book); var loaded = storage.load();
        check(loaded.size() == 2 && !loaded.recording() && !loaded.visible() && !loaded.throughWalls(), "Roundtrip and safe stopped restore");
        check(!storage.file().equals(new TrailStorage(storage.file().getParent(), "other-world").file()), "Worlds have separate storage");
        loaded.clear(); check(loaded.size() == 0 && !loaded.recording(), "Clear stops and removes marks");
        loaded.setRadius(128); loaded.start(pos(0, 0, 0));
        for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) for (int z = 0; z < 8; z++) loaded.changed(pos(x, y, z));
        check(loaded.size() == TrailBook.MAX_MARKS, "Capacity filled");
        check(!loaded.changed(pos(-1, 0, 0)) && loaded.limitReached() && !loaded.recording(), "Overflow stops without deleting old marks");
        check(loaded.size() == TrailBook.MAX_MARKS && loaded.at(pos(0, 0, 0)) != null, "Oldest mark preserved");
        loaded.clear(); check(!loaded.limitReached(), "Clear resets capacity status");
        Files.writeString(storage.file(), "{invalid");
        boolean failed = false;
        try { storage.load(); } catch (java.io.IOException expected) { failed = true; }
        check(failed && Files.readString(storage.file()).equals("{invalid"), "Corrupt file preserved");
        failed = false;
        try { TrailBook.restore(new TrailBook.Data(1, 64, true, true, List.of(new TrailBook.Mark(pos(0,0,0), 0)))); }
        catch (IllegalArgumentException expected) { failed = true; }
        check(failed, "Invalid marks rejected");
        TrailFeaturesTest.run();
        System.out.println("TrailBookTest: all checks passed");
    }
}
