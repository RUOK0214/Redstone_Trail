package io.github.ruok0214.redstonetrail;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("UnstableApiUsage")
public final class TrailClientGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void menu(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_T); context.waitForScreen(ChatScreen.class);
        context.getInput().typeChars("/rt"); context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        context.waitForScreen(TrailScreen.class);
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
        }
    }
}
