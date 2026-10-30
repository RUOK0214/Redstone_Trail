package io.github.ruok0214.redstonetrail;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

final class TrailSettingsTest {
    private static TrailBook.Target pos(int x, int y, int z) { return new TrailBook.Target("minecraft:overworld", x, y, z); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void run() throws Exception {
        var directory = Files.createTempDirectory("trail-shared-settings-");
        var settingsFile = new TrailSettingsStorage(directory);
        check(settingsFile.load() == null, "Missing shared settings allow first-world migration");
        var a = new TrailBook(); a.setRadius(512); a.setVisible(false); a.setThroughWalls(false);
        a.setStyle(new TrailBook.Style(0xAB1234, 45, 7)); a.setTemporaryStyle(new TrailBook.Style(0x34ABCD, 65, 3));
        a.configureTemporary(new TrailBook.Temporary(true, 12, TrailBook.Fade.AFTER_DURATION, 4));
        a.start(pos(0, 0, 0)); a.setTemporaryCenter(pos(0, 0, 0));
        for (var edge : List.of(pos(512,0,0), pos(-512,0,0), pos(0,512,0), pos(0,0,-512)))
            check(a.changed(edge), "512-block radius includes boundaries in three dimensions");
        for (var outside : List.of(pos(513,0,0), pos(512,1,0), pos(400,400,0)))
            check(!a.changed(outside), "512-block radius remains spherical and excludes outside");
        check(a.size() == 4 && a.temporarySize() == 4, "Both trails support radius 512");
        var snapshot = a.snapshot("World A"); a.stop();
        var worldA = new TrailStorage(directory.resolve("worlds"), "A"); worldA.save(a);
        var shared = TrailSettings.from(a); settingsFile.save(shared);
        check(settingsFile.load().equals(shared), "All shared preferences survive restart");
        var worldB = new TrailStorage(directory.resolve("worlds"), "B"); var b = worldB.load();
        settingsFile.load().applyTo(b);
        check(TrailSettings.from(b).equals(shared) && b.size() == 0 && b.saved().isEmpty() && !b.recording(),
                "A new world inherits settings but no positions, saved comparisons or active recording");
        b.setRadius(256); b.setStyle(new TrailBook.Style(0x55CCAA, 10, 2));
        settingsFile.save(TrailSettings.from(b)); worldB.save(b);
        var restoredA = worldA.load(); settingsFile.load().applyTo(restoredA);
        check(restoredA.radius() == 256 && restoredA.style().equals(b.style()) && restoredA.size() == 4
                && restoredA.saved(snapshot.id()).style().equals(snapshot.style()),
                "Latest shared settings replace old world settings without changing paths or saved-trail styles");
        restoredA.setRadius(512); worldA.save(restoredA);
        check(worldA.load().radius() == 512, "World files accept the expanded radius");
        boolean rejected = false;
        try { b.setRadius(384); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Unsupported radius is rejected");
        Files.writeString(settingsFile.file(), "{invalid"); rejected = false;
        try { settingsFile.load(); } catch (IOException expected) { rejected = true; }
        check(rejected && Files.readString(settingsFile.file()).equals("{invalid"), "Unreadable global settings remain intact");
        Files.writeString(settingsFile.file(), "null"); rejected = false;
        try { settingsFile.load(); } catch (IOException expected) { rejected = true; }
        check(rejected, "Null settings cannot silently reset preferences");
        System.out.println("TrailSettingsTest: shared preferences, isolated worlds, migration and radius 512 passed");
    }
}
