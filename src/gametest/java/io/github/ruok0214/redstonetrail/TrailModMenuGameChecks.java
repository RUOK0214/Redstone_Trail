package io.github.ruok0214.redstonetrail;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.gui.ModsScreen;
import com.terraformersmc.modmenu.gui.widget.ModListWidget;
import com.terraformersmc.modmenu.util.ModMenuScreenTexts;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Kept separate so the standard client checks also run without Mod Menu installed. */
@SuppressWarnings("UnstableApiUsage")
final class TrailModMenuGameChecks {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static ModsScreen open(ClientGameTestContext context, Screen parent) {
        context.runOnClient(mc -> mc.gui.setScreen(ModMenuApi.createModsScreen(parent)));
        context.waitForScreen(ModsScreen.class);
        context.runOnClient(mc -> mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow().setValue("Redstone Trail"));
        context.runOnClient(mc -> {
            var list = mc.gui.screen().children().stream().filter(ModListWidget.class::isInstance).map(ModListWidget.class::cast).findFirst().orElseThrow();
            list.select(list.children().stream().filter(entry -> entry.getMod().getId().equals("redstone_trail")).findFirst().orElseThrow());
        });
        context.waitTick();
        return context.computeOnClient(mc -> {
            var screen = (ModsScreen) mc.gui.screen();
            var selected = screen.getSelectedEntry();
            check(selected != null && selected.getMod().getId().equals("redstone_trail"), "Mod Menu finds Redstone Trail");
            check(selected.getMod().getTranslatedDescription().equals(Component.translatable("modmenu.descriptionTranslation.redstone_trail").getString()),
                    "Mod Menu uses the localized description");
            check(screen.getModHasConfigScreen("redstone_trail"), "Mod Menu registered the optional configuration entrypoint");
            return screen;
        });
    }
    private static void configure(ClientGameTestContext context) {
        context.clickScreenButton(context.computeOnClient(mc -> ModMenuScreenTexts.CONFIGURE.getString()));
    }
    static void title(ClientGameTestContext context) {
        var parent = context.computeOnClient(mc -> mc.gui.screen());
        var mods = open(context, parent);
        context.takeScreenshot("modmenu-title");
        configure(context); context.waitForScreen(TrailInfoScreen.class);
        context.takeScreenshot("modmenu-world-required");
        context.clickScreenButton("gui.done");
        context.runOnClient(mc -> { check(mc.gui.screen() == mods, "Title guidance returns to Mod Menu"); mc.gui.screen().onClose(); });
        context.runOnClient(mc -> check(mc.gui.screen() == parent, "Mod Menu returns to its original parent"));
    }
    static void inWorld(ClientGameTestContext context) {
        var mods = open(context, null);
        context.takeScreenshot("modmenu-ko");
        configure(context); context.waitForScreen(TrailScreen.class);
        context.clickScreenButton("gui.done");
        context.runOnClient(mc -> check(mc.gui.screen() == mods, "Done returns to Mod Menu"));
        configure(context); context.waitForScreen(TrailScreen.class);
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        context.runOnClient(mc -> check(mc.gui.screen() == mods, "Escape returns to Mod Menu"));
        configure(context); context.waitForScreen(TrailScreen.class);
        context.clickScreenButton("redstone_trail.start");
        context.runOnClient(mc -> {
            check(mc.gui.screen() == null && RedstoneTrailClient.manager.book().recording(), "Start from Mod Menu returns to the game and records");
            RedstoneTrailClient.manager.stop();
        });
    }
}
