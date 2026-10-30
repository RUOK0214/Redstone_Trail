package io.github.ruok0214.redstonetrail;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("UnstableApiUsage")
public final class TrailClientGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void menu(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_T); context.waitForScreen(ChatScreen.class);
        context.getInput().typeChars("/rt"); context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        context.waitForScreen(TrailScreen.class);
    }
    private static String label(ClientGameTestContext context, String prefix) {
        return context.computeOnClient(mc -> mc.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow().getMessage().getString());
    }
    private static void firstButton(ClientGameTestContext context, String key) {
        context.runOnClient(mc -> mc.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(TrailScreen.tr(key).getString())).findFirst().orElseThrow()
                .onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)));
        context.waitTick();
    }
    private static void slider(ClientGameTestContext context, String prefix, int steps) {
        context.runOnClient(mc -> {
            var s = mc.gui.screen().children().stream().filter(AbstractSliderButton.class::isInstance).map(AbstractSliderButton.class::cast)
                    .filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow();
            mc.gui.screen().setFocused(s); s.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            for (int i = 0; i < Math.abs(steps); i++) s.keyPressed(new KeyEvent(steps > 0 ? GLFW.GLFW_KEY_RIGHT : GLFW.GLFW_KEY_LEFT, 0, 0));
            s.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
        });
    }
    private static void color(ClientGameTestContext context, String value) {
        context.runOnClient(mc -> mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList().get(1).setValue(value));
    }
    private static void snapshot(ClientGameTestContext context, String name) {
        context.clickScreenButton("redstone_trail.snapshot"); context.waitForScreen(SnapshotScreen.class);
        context.runOnClient(mc -> mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue(name));
        context.clickScreenButton("redstone_trail.save_snapshot"); context.waitForScreen(TrailScreen.class);
    }
    private static void features(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        menu(context); context.clickScreenButton("redstone_trail.start");
        world.getServer().runCommand("setblock 7 81 0 minecraft:diamond_block");
        world.getServer().runCommand("setblock 6 81 0 minecraft:emerald_block");
        context.waitFor(mc -> RedstoneTrailClient.manager.book().size() == 2);
        menu(context); context.clickScreenButton("redstone_trail.stop");
        context.clickScreenButton("redstone_trail.appearance"); context.waitForScreen(AppearanceScreen.class);
        color(context, "#12ABEF"); slider(context, "투명도:", 20); slider(context, "선 두께:", 20);
        context.runOnClient(mc -> check(RedstoneTrailClient.manager.book().style().equals(TrailBook.Style.DEFAULT), "Appearance remains a draft until Apply"));
        context.getInput().resizeWindow(854, 480); context.waitTick(); context.takeScreenshot("appearance-small-ko");
        context.clickScreenButton("redstone_trail.apply"); context.waitForScreen(TrailScreen.class);
        var blue = context.computeOnClient(mc -> RedstoneTrailClient.manager.book().style());
        check(blue.color() == 0x12ABEF && blue.transparency() > 0 && blue.width() > 2.5, "Current appearance applies after resize");
        context.runOnClient(mc -> check(RedstoneTrailClient.extractOutlines().stream().allMatch(o -> o.color() == blue.argb(1) && o.width() == blue.width()), "Existing current marks use edited alpha, color and width"));
        snapshot(context, "회로 A");
        context.clickScreenButton("redstone_trail.appearance"); color(context, "invalid");
        context.runOnClient(mc -> check(mc.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(TrailScreen.tr("apply").getString())).noneMatch(b -> b.active), "Invalid hex disables Apply"));
        context.clickScreenButton("gui.cancel");
        context.clickScreenButton("redstone_trail.appearance"); color(context, "#FF44BB"); context.clickScreenButton("redstone_trail.apply");
        snapshot(context, "회로 B");
        context.clickScreenButton(label(context, "계속 유지:"));
        context.runOnClient(mc -> {
            var outlines = RedstoneTrailClient.extractOutlines();
            check(outlines.size() == 4 && outlines.stream().map(o -> o.color() & 0xFFFFFF).distinct().count() == 2, "Two independently colored snapshots show together while current trail is hidden");
            check(outlines.stream().map(o -> Math.round(o.box().getXsize() * 1000)).distinct().count() == 2, "Overlapping comparisons use separated bounds");
        });
        context.clickScreenButton("gui.done");
        world.getServer().runCommand("tp @a 6.5 81 4.5 180 20");
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(mc -> { mc.player.setYRot(180); mc.player.setXRot(20); });
        context.waitTicks(3); context.takeScreenshot("comparison-world-ko");
        menu(context); context.clickScreenButton("redstone_trail.saved_title"); context.waitForScreen(SavedTrailsScreen.class);
        context.takeScreenshot("saved-trails-small-ko");
        firstButton(context, "appearance"); context.waitForScreen(AppearanceScreen.class);
        color(context, "#00FF88"); slider(context, "투명도:", 10); slider(context, "선 두께:", -5);
        context.runOnClient(mc -> mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("회로 A 수정"));
        context.clickScreenButton("redstone_trail.apply"); context.waitForScreen(SavedTrailsScreen.class);
        context.runOnClient(mc -> {
            var item = RedstoneTrailClient.manager.book().saved().getFirst();
            check(item.name().equals("회로 A 수정") && item.style().color() == 0x00FF88, "Saved appearance and name editable");
            check(RedstoneTrailClient.extractOutlines().stream().anyMatch(o -> o.color() == item.style().argb(1) && o.width() == item.style().width()), "Saved appearance edits update already drawn outlines");
        });
        firstButton(context, "hide");
        context.runOnClient(mc -> check(RedstoneTrailClient.extractOutlines().size() == 2 && RedstoneTrailClient.manager.book().saved().size() == 2, "Hide removes only selected snapshot geometry"));
        firstButton(context, "show");
        context.runOnClient(mc -> check(RedstoneTrailClient.extractOutlines().size() == 4, "Show restores comparison without recording again"));
        firstButton(context, "delete"); context.waitForScreen(ConfirmScreen.class); context.clickScreenButton("gui.no");
        context.runOnClient(mc -> check(RedstoneTrailClient.manager.book().saved().size() == 2, "Cancel snapshot deletion keeps both trails"));
        firstButton(context, "delete"); context.waitForScreen(ConfirmScreen.class); context.clickScreenButton("gui.yes");
        context.runOnClient(mc -> check(RedstoneTrailClient.manager.book().saved().size() == 1 && RedstoneTrailClient.extractOutlines().size() == 2, "Delete removes only chosen snapshot"));
        context.clickScreenButton("gui.done"); context.waitForScreen(TrailScreen.class);
        // The two controls live on the same screen, with no mutually exclusive mode selector.
        context.clickScreenButton(label(context, "계속 유지:"));
        context.clickScreenButton("redstone_trail.temporary_title"); context.waitForScreen(TemporaryScreen.class);
        context.clickScreenButton("redstone_trail.temporary_appearance"); context.waitForScreen(AppearanceScreen.class);
        color(context, "#33DDFF"); slider(context, "투명도:", 10);
        context.clickScreenButton("redstone_trail.apply"); context.waitForScreen(TemporaryScreen.class);
        context.clickScreenButton(label(context, "사라짐:")); context.clickScreenButton(label(context, "사라짐:"));
        context.takeScreenshot("temporary-settings-small-ko");
        context.clickScreenButton("redstone_trail.apply"); context.waitForScreen(TrailScreen.class);
        context.runOnClient(mc -> check(!RedstoneTrailClient.manager.book().temporary().enabled()
                && RedstoneTrailClient.manager.book().style().color() == 0xFF44BB
                && RedstoneTrailClient.manager.book().temporaryStyle().color() == 0x33DDFF,
                "Temporary settings edit only temporary appearance and do not turn it on"));
        context.clickScreenButton(label(context, "일시적 트레일:"));
        context.clickScreenButton("gui.done");
        world.getServer().runCommand("setblock 8 81 0 minecraft:gold_block");
        context.waitFor(mc -> RedstoneTrailClient.manager.book().temporarySize() == 1);
        context.runOnClient(mc -> check(!RedstoneTrailClient.manager.book().recording()
                && RedstoneTrailClient.manager.book().size() == 2, "Temporary On records without persistent Start"));
        menu(context); context.takeScreenshot("temporary-on-recording-off-ko");
        context.clickScreenButton("redstone_trail.start");
        world.getServer().runCommand("setblock 9 81 0 minecraft:diamond_block");
        context.waitFor(mc -> RedstoneTrailClient.manager.book().size() == 3 && RedstoneTrailClient.manager.book().temporarySize() == 2);
        menu(context); context.takeScreenshot("both-running-small-ko"); context.clickScreenButton("redstone_trail.stop");
        world.getServer().runCommand("setblock 10 81 0 minecraft:emerald_block");
        context.waitFor(mc -> RedstoneTrailClient.manager.book().temporaryAt(RedstoneTrailClient.manager.target(new BlockPos(10,81,0))) != null);
        context.runOnClient(mc -> check(RedstoneTrailClient.manager.book().size() == 3
                && RedstoneTrailClient.manager.book().temporary().enabled(), "Stop affects only persistent recording"));
        context.clickScreenButton("redstone_trail.clear"); context.waitForScreen(ConfirmScreen.class); context.clickScreenButton("gui.yes");
        context.runOnClient(mc -> check(RedstoneTrailClient.manager.book().size() == 0 && RedstoneTrailClient.manager.book().temporarySize() > 0
                && RedstoneTrailClient.manager.book().saved().size() == 1, "Clear leaves live temporary marks and saved comparisons"));
        context.clickScreenButton("redstone_trail.start");
        world.getServer().runCommand("setblock 11 81 0 minecraft:gold_block");
        context.waitFor(mc -> RedstoneTrailClient.manager.book().size() == 1);
        menu(context); context.clickScreenButton(label(context, "일시적 트레일:"));
        context.runOnClient(mc -> check(RedstoneTrailClient.manager.book().temporarySize() == 0 && !RedstoneTrailClient.manager.book().temporary().enabled()
                && RedstoneTrailClient.manager.book().recording() && RedstoneTrailClient.manager.book().size() == 1,
                "Temporary Off clears only its own marks while recording continues"));
        world.getServer().runCommand("setblock 12 81 0 minecraft:diamond_block");
        context.waitFor(mc -> RedstoneTrailClient.manager.book().size() == 2);
        context.takeScreenshot("recording-on-temporary-off-ko");
        context.clickScreenButton("gui.done");
        String[] blocks = {"diamond_block", "emerald_block", "gold_block"};
        for (var fade : TrailBook.Fade.values()) {
            context.runOnClient(mc -> {
                var m = RedstoneTrailClient.manager;
                m.setTemporaryEnabled(false);
                m.book().configureTemporary(new TrailBook.Temporary(true, 2, fade, 1));
                m.setTemporaryEnabled(true);
            });
            world.getServer().runCommand("setblock 8 81 0 minecraft:" + blocks[fade.ordinal()]);
            context.waitFor(mc -> RedstoneTrailClient.manager.book().temporarySize() == 1);
            if (fade != TrailBook.Fade.INSTANT) {
                context.waitFor(mc -> {
                    var b0 = RedstoneTrailClient.manager.book();
                    var mark = b0.temporaryAt(RedstoneTrailClient.manager.target(new BlockPos(8,81,0)));
                    return mark != null && b0.opacity(mark) < .8 && b0.opacity(mark) > .1;
                });
                context.runOnClient(mc -> check(RedstoneTrailClient.extractOutlines().stream().anyMatch(o ->
                        (o.color() & 0xFFFFFF) == 0x33DDFF
                        && (o.color() >>> 24) < (RedstoneTrailClient.manager.book().temporaryStyle().argb(1) >>> 24)),
                        "Actual temporary renderer receives fading alpha independently"));
            }
            context.waitFor(mc -> RedstoneTrailClient.manager.book().temporarySize() == 0);
            context.runOnClient(mc -> check(RedstoneTrailClient.manager.book().at(RedstoneTrailClient.manager.target(new BlockPos(8,81,0))) != null
                    && RedstoneTrailClient.manager.book().recording() && RedstoneTrailClient.extractOutlines().size() >= 3,
                    "Temporary expiry cannot erase persistent marks or stop recording"));
        }
        context.runOnClient(mc -> {
            int size = RedstoneTrailClient.manager.book().size();
            RedstoneTrailClient.manager.saveAll(); RedstoneTrailClient.manager = new TrailManager(); RedstoneTrailClient.manager.tick(mc);
            check(RedstoneTrailClient.manager.book().saved().size() == 1 && RedstoneTrailClient.manager.book().size() == size
                    && RedstoneTrailClient.manager.book().temporarySize() == 0 && RedstoneTrailClient.manager.book().temporary().enabled()
                    && !RedstoneTrailClient.manager.book().recording(), "Reload restores both preferences and durable marks; temporary history stays empty");
        });
    }
    @Override public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1000, 700);
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("fill -5 80 -5 8 80 5 minecraft:stone");
            world.getServer().runCommand("setblock 0 81 0 minecraft:lever[face=floor,powered=false]");
            world.getServer().runCommand("fill 1 81 0 4 81 0 minecraft:redstone_wire");
            world.getServer().runCommand("setblock 5 81 0 minecraft:redstone_lamp");
            world.getServer().runCommand("tp @a 2.5 81 4.5 180 20");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(10);
            context.waitFor(mc -> RedstoneTrailClient.manager.book() != null);
            context.runOnClient(mc -> {
                check(RedstoneTrailClient.manager.book().size() == 0, "Setup before recording creates no trail");
                mc.getLanguageManager().setSelected("ko_kr"); mc.options.languageCode = "ko_kr";
                mc.reloadResourcePacks();
            });
            context.waitTicks(60); menu(context); context.takeScreenshot("menu-ko");
            context.clickScreenButton("redstone_trail.start"); context.waitTick();
            context.runOnClient(mc -> check(mc.gui.screen() == null && RedstoneTrailClient.manager.book().recording(), "Start button records and returns to game"));
            // Real mouse input exercises client prediction as well as server confirmation.
            context.runOnClient(mc -> {
                var eye = mc.player.getEyePosition();
                double dx = .5 - eye.x, dy = 81.2 - eye.y, dz = .5 - eye.z;
                mc.player.setYRot((float)(Math.toDegrees(Math.atan2(dz, dx)) - 90));
                mc.player.setXRot((float)-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
            });
            context.waitTicks(3); context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitFor(mc -> RedstoneTrailClient.manager.book().size() >= 6);
            context.runOnClient(mc -> {
                var manager = RedstoneTrailClient.manager;
                for (int x = 0; x <= 5; x++) check(manager.book().at(manager.target(new BlockPos(x, 81, 0))) != null, "Real redstone propagation outlines block " + x);
                check(RedstoneTrailClient.extractOutlines().size() >= 6, "Changed blocks have render geometry");
            });
            world.getServer().runCommand("setblock 0 81 0 minecraft:lever[face=floor,powered=false]");
            context.waitTicks(20); world.getConnection().waitForClientboundPackets();
            context.runOnClient(mc -> {
                var manager = RedstoneTrailClient.manager;
                check(manager.book().at(manager.target(new BlockPos(5,81,0))).changes() >= 2, "Lamp off leaves retained trail");
            });
            menu(context); context.takeScreenshot("recording-ko"); context.clickScreenButton("redstone_trail.stop");
            int stopped = context.computeOnClient(mc -> RedstoneTrailClient.manager.book().size());
            context.clickScreenButton("gui.done"); context.waitTicks(100); context.takeScreenshot("trail-after-stop-ko");
            world.getServer().runCommand("setblock 7 81 0 minecraft:gold_block");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(4);
            context.runOnClient(mc -> check(RedstoneTrailClient.manager.book().size() == stopped && RedstoneTrailClient.extractOutlines().size() == stopped,
                    "Stop retains every outline and ignores later server updates"));
            context.runOnClient(mc -> {
                RedstoneTrailClient.manager.saveAll();
                RedstoneTrailClient.manager = new TrailManager();
                RedstoneTrailClient.manager.tick(mc);
                check(RedstoneTrailClient.manager.book().size() == stopped && !RedstoneTrailClient.manager.book().recording()
                        && RedstoneTrailClient.extractOutlines().size() == stopped, "Fresh manager restores saved trail and rendered outlines");
            });
            menu(context); context.clickScreenButton("redstone_trail.clear"); context.waitForScreen(ConfirmScreen.class);
            context.clickScreenButton("gui.no"); context.waitForScreen(TrailScreen.class);
            context.runOnClient(mc -> check(RedstoneTrailClient.manager.book().size() == stopped, "Cancel clear keeps trail"));
            context.clickScreenButton("redstone_trail.clear"); context.waitForScreen(ConfirmScreen.class);
            context.clickScreenButton("gui.yes"); context.waitForScreen(TrailScreen.class);
            context.runOnClient(mc -> check(RedstoneTrailClient.manager.book().size() == 0 && RedstoneTrailClient.extractOutlines().isEmpty(), "Confirmed clear removes geometry"));
            context.getInput().resizeWindow(854, 480); context.waitTick(); context.takeScreenshot("menu-small-ko");
            context.clickScreenButton("gui.done");
            features(context, world);
        }
    }
}
