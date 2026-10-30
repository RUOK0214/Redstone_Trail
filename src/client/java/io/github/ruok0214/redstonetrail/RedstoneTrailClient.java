package io.github.ruok0214.redstonetrail;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

public final class RedstoneTrailClient implements ClientModInitializer {
    public static TrailManager manager;
    public static KeyMapping openMenu;
    private boolean menuRequested;
    private volatile List<Outline> outlines = List.of();
    record Outline(AABB box, boolean throughWalls, int color, float width) { }
    @Override public void onInitializeClient() {
        manager = new TrailManager();
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("redstone_trail", "main"));
        openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.redstone_trail.menu", InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(), category));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            var root = dispatcher.register(ClientCommands.literal("redstonetrail").requires(source -> source.attended())
                    .executes(context -> { menuRequested = true; return 1; })
                    .then(ClientCommands.literal("start").executes(context -> { manager.start(); return 1; }))
                    .then(ClientCommands.literal("stop").executes(context -> { manager.stop(); return 1; })));
            dispatcher.register(ClientCommands.literal("rt").requires(source -> source.attended())
                    .executes(context -> { menuRequested = true; return 1; }).redirect(root));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            manager.tick(client);
            while (openMenu.consumeClick()) if (client.gui.screen() == null) menuRequested = true;
            if (menuRequested && client.player != null && manager.book() != null) client.gui.setScreen(new TrailScreen(manager));
            menuRequested = false;
            if (client.level == null) outlines = List.of();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> manager.saveAll());
        LevelExtractionEvents.END_EXTRACTION.register(context -> outlines = extractOutlines());
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
                for (var outline : outlines) {
                    var gizmo = Gizmos.cuboid(outline.box(), GizmoStyle.stroke(outline.color(), outline.width()));
                    if (outline.throughWalls()) gizmo.setAlwaysOnTop();
                }
            }
        });
    }
    static List<Outline> extractOutlines() {
        Minecraft mc = Minecraft.getInstance();
        var book = manager.book();
        if (mc.level == null || mc.player == null || book == null || mc.gui.hud.isHidden()) return List.of();
        List<Outline> result = new ArrayList<>();
        if (book.visible()) for (var mark : book.marks())
            addOutline(result, mc, mark, book.style(), book.opacity(mark), .004, book.throughWalls());
        int layer = 1;
        for (var saved : book.saved()) {
            // Slightly separated bounds keep overlapping comparison colors distinguishable.
            if (saved.visible()) for (var mark : saved.marks())
                addOutline(result, mc, mark, saved.style(), 1, .004 + .24 * layer / (layer + 2), book.throughWalls());
            layer++;
        }
        return List.copyOf(result);
    }
    private static void addOutline(List<Outline> output, Minecraft mc, TrailBook.Mark mark, TrailBook.Style style,
                                   double fade, double inflation, boolean throughWalls) {
        int color = style.argb(fade);
        if ((color >>> 24) == 0) return;
        var t = mark.target();
        if (!t.dimension().equals(manager.dimension()) || !mc.level.hasChunkAt(TrailManager.pos(t))
                || mc.player.distanceToSqr(t.x() + .5, t.y() + .5, t.z() + .5) > 192 * 192) return;
        output.add(new Outline(new AABB(TrailManager.pos(t)).inflate(inflation), throughWalls, color, style.width()));
    }
}
