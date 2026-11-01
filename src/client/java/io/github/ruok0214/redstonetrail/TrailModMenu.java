package io.github.ruok0214.redstonetrail;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Loaded only through Mod Menu's optional entrypoint. */
public final class TrailModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<Screen> getModConfigScreenFactory() {
        return parent -> {
            var mc = Minecraft.getInstance();
            var manager = RedstoneTrailClient.manager;
            if (mc.level == null || mc.player == null || manager == null) return new TrailInfoScreen(parent);
            manager.tick(mc);
            return new TrailScreen(parent, manager);
        };
    }
}
