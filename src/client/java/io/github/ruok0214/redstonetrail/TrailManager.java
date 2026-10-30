package io.github.ruok0214.redstonetrail;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;

public final class TrailManager {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("redstone_trail");
    private static final class Session {
        final TrailStorage storage;
        TrailBook book;
        long savedRevision;
        boolean readFailed, saveFailed;
        Session(String identity) {
            storage = new TrailStorage(FabricLoader.getInstance().getConfigDir().resolve("redstone_trail/worlds"), identity);
            try { book = storage.load(); }
            catch (IOException ex) { readFailed = true; book = new TrailBook(); LOG.error("Preserving unreadable trail file", ex); }
        }
    }
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<BlockPos, BlockState> knownStates = new HashMap<>();
    private Session session;
    private ClientLevel level;
    private int ticks;
    public TrailBook book() { return session == null ? null : session.book; }
    public String error() { return session == null ? null : session.readFailed ? "load_failed" : session.saveFailed ? "save_failed" : null; }
    public boolean canRecord() { return book() != null && !session.readFailed; }
    public String dimension() { return level == null ? "" : level.dimension().identifier().toString(); }
    public TrailBook.Target target(BlockPos pos) { return new TrailBook.Target(dimension(), pos.getX(), pos.getY(), pos.getZ()); }
    public static BlockPos pos(TrailBook.Target t) { return new BlockPos(t.x(), t.y(), t.z()); }
    private String identity(Minecraft mc) {
        if (mc.getSingleplayerServer() != null)
            return "local:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (mc.getCurrentServer() != null) return "server:" + mc.getCurrentServer().ip.strip().toLowerCase(Locale.ROOT);
        return "connection:" + mc.getConnection().getConnection().getRemoteAddress();
    }
    public void tick(Minecraft mc) {
        ticks++;
        if (level != mc.level) {
            if (book() != null) book().stop();
            save(); knownStates.clear(); level = mc.level;
            session = level == null ? null : sessions.computeIfAbsent(identity(mc), Session::new);
        }
        if (ticks % 100 == 0) saveAll();
    }
    public void start() {
        Minecraft mc = Minecraft.getInstance();
        tick(mc);
        if (!canRecord() || mc.player == null || level == null || book().recording()) return;
        knownStates.clear(); book().start(target(mc.player.blockPosition()));
        message("started");
    }
    public void stop() {
        if (book() == null) return;
        book().stop(); knownStates.clear(); save(); message("stopped");
    }
    public void clear() {
        if (book() == null || session.readFailed) return;
        book().clear(); knownStates.clear(); save();
    }
    /** Only seed the old state here; predicted local changes never create marks. */
    public void beforePrediction(ClientLevel source, BlockPos position) {
        if (!observing(source, position)) return;
        if (knownStates.size() >= TrailBook.MAX_MARKS * 2) { stop(); message("observation_limit"); return; }
        knownStates.putIfAbsent(position.immutable(), source.getBlockState(position));
    }
    private boolean observing(ClientLevel source, BlockPos pos) {
        return source == level && source == Minecraft.getInstance().level && book() != null
                && source.hasChunkAt(pos) && book().accepts(target(pos));
    }
    /** Called before each authoritative block packet applies, including section updates. */
    public void observed(ClientLevel source, BlockPos position, BlockState next) {
        if (!observing(source, position)) return;
        var previous = knownStates.getOrDefault(position, source.getBlockState(position));
        if (previous == next) return;
        boolean added = book().changed(target(position));
        if (added) knownStates.put(position.immutable(), next);
        if (book().limitReached()) { knownStates.clear(); save(); message("limit"); }
    }
    public void unloaded(ClientLevel source, int x, int z) {
        if (source == level) knownStates.keySet().removeIf(pos -> pos.getX() >> 4 == x && pos.getZ() >> 4 == z);
    }
    public void save() {
        if (session == null || session.readFailed || session.savedRevision == book().revision()) return;
        try { session.storage.save(book()); session.savedRevision = book().revision(); session.saveFailed = false; }
        catch (IOException ex) { session.saveFailed = true; LOG.error("Cannot save Redstone Trail", ex); }
    }
    public void saveAll() {
        Session active = session;
        for (Session item : sessions.values()) { session = item; save(); }
        session = active;
    }
    private void message(String key) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendOverlayMessage(Component.translatable("redstone_trail." + key));
    }
}
