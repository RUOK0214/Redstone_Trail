package io.github.ruok0214.redstonetrail;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
final class TrailSharedSettingsGameChecks {
    record FirstWorld(TestWorldSave save, TrailSettings settings, int count, UUID savedId, TrailBook.Style savedStyle) { }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static FirstWorld prepare(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(mc -> {
            RedstoneTrailClient.manager.stop(); RedstoneTrailClient.manager.setTemporaryEnabled(false);
            mc.gui.setScreen(new TrailScreen(RedstoneTrailClient.manager));
        });
        context.waitForScreen(TrailScreen.class);
        for (int expected : new int[]{128, 256, 512}) {
            var label = context.computeOnClient(mc -> TrailScreen.tr("radius", RedstoneTrailClient.manager.book().radius()).getString());
            context.clickScreenButton(label);
            context.runOnClient(mc -> check(RedstoneTrailClient.manager.book().radius() == expected, "Radius button reaches " + expected));
        }
        context.takeScreenshot("radius-512-ko");
        context.clickScreenButton("redstone_trail.start");
        world.getServer().runCommand("setblock 6 300 4 minecraft:gold_block");
        context.waitFor(mc -> RedstoneTrailClient.manager.book().at(RedstoneTrailClient.manager.target(new BlockPos(6,300,4))) != null);
        context.runOnClient(mc -> check(RedstoneTrailClient.extractOutlines().stream().anyMatch(o -> o.box().minY > 299 && o.box().minY < 300),
                "A server-reported change over 192 blocks away is captured and rendered"));
        return context.computeOnClient(mc -> {
            var manager = RedstoneTrailClient.manager; manager.stop(); var book = manager.book();
            var saved = book.snapshot("World A only");
            book.setStyle(new TrailBook.Style(0xAA55CC, 32, 6)); book.setTemporaryStyle(new TrailBook.Style(0x22EEAA, 48, 4));
            book.setVisible(false); book.setThroughWalls(false);
            book.configureTemporary(new TrailBook.Temporary(true, 7, TrailBook.Fade.AFTER_DURATION, 2));
            manager.saveAll();
            return new FirstWorld(world.getWorldSave(), TrailSettings.from(book), book.size(), saved.id(), saved.style());
        });
    }
    static void switchWorlds(ClientGameTestContext context, FirstWorld first) {
        TrailSettings latest;
        try (var second = context.worldBuilder().create()) {
            check(!second.getWorldSave().getSaveDirectory().equals(first.save().getSaveDirectory()), "Test uses a distinct second world");
            second.getConnection().waitForChunksDownload();
            context.waitFor(mc -> RedstoneTrailClient.manager.book() != null);
            context.runOnClient(mc -> {
                var book = RedstoneTrailClient.manager.book();
                check(TrailSettings.from(book).equals(first.settings()), "All preferences carry to a different world");
                check(book.size() == 0 && book.saved().isEmpty() && !book.recording(), "World A records do not appear in world B");
                mc.gui.setScreen(new TrailScreen(RedstoneTrailClient.manager));
            });
            context.waitForScreen(TrailScreen.class); context.takeScreenshot("shared-settings-world-b-ko");
            context.clickScreenButton("gui.done");
            latest = context.computeOnClient(mc -> {
                var manager = RedstoneTrailClient.manager; var book = manager.book();
                book.setRadius(256); book.setVisible(true); book.setThroughWalls(true);
                book.setStyle(new TrailBook.Style(0x11AAEE, 15, 3)); manager.setTemporaryEnabled(false);
                manager.saveAll();
                return TrailSettings.from(book);
            });
        }
        try (var reopened = first.save().open()) {
            reopened.getConnection().waitForChunksDownload();
            context.waitFor(mc -> RedstoneTrailClient.manager.book() != null);
            context.runOnClient(mc -> {
                var manager = RedstoneTrailClient.manager; var book = manager.book();
                check(TrailSettings.from(book).equals(latest), "Returning to a cached world keeps the newest shared settings");
                check(book.size() == first.count() && book.saved(first.savedId()).style().equals(first.savedStyle()) && !book.recording(),
                        "World A keeps its own paths and saved style, without resuming recording");
                manager.saveAll(); RedstoneTrailClient.manager = new TrailManager(); RedstoneTrailClient.manager.tick(mc);
                check(TrailSettings.from(RedstoneTrailClient.manager.book()).equals(latest)
                        && RedstoneTrailClient.manager.book().size() == first.count(), "File reload restores shared settings and world records independently");
            });
        }
    }
}
