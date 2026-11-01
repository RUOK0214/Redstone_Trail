package io.github.ruok0214.redstonetrail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;

public final class TrailSettingsStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    public TrailSettingsStorage(Path directory) { file = directory.resolve("settings.json"); }
    public Path file() { return file; }
    /** Null means that the first successfully opened world should supply the initial preferences. */
    public TrailSettings load() throws IOException {
        if (!Files.exists(file)) return null;
        if (Files.size(file) > 64 * 1024) throw new IOException("Settings file exceeds 64 KiB");
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return Objects.requireNonNull(GSON.fromJson(reader, TrailSettings.class));
        } catch (RuntimeException ex) { throw new IOException("Invalid shared settings: " + file, ex); }
    }
    public void save(TrailSettings settings) throws IOException {
        Objects.requireNonNull(settings);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "redstone-trail-settings-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(settings), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
