package io.github.ruok0214.redstonetrail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TrailStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    public TrailStorage(Path directory, String identity) {
        try { file = directory.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(StandardCharsets.UTF_8))) + ".json"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public Path file() { return file; }
    public TrailBook load() throws IOException {
        if (!Files.exists(file)) return new TrailBook();
        if (Files.size(file) > 32 * 1024 * 1024) throw new IOException("Trail file exceeds 32 MiB");
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return TrailBook.restore(GSON.fromJson(reader, TrailBook.Data.class));
        } catch (RuntimeException ex) { throw new IOException("Invalid trail file: " + file, ex); }
    }
    public void save(TrailBook book) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "redstone-trail-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(book.data()), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
