package ch.ksrminecraft.RankProxyPlugin.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class OfflinePlayerStore {

    private final Path filePath;
    private final Map<String, UUID> nameToUuid = new HashMap<>();
    private final Map<UUID, String> uuidToName = new HashMap<>();

    private static final Gson GSON = new Gson();

    public OfflinePlayerStore(Path dataFolder) {
        this.filePath = dataFolder.resolve("offline_players.json");
        load();
    }

    private void load() {
        try {
            if (!Files.exists(filePath)) return;

            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> raw = GSON.fromJson(new FileReader(filePath.toFile()), type);

            for (Map.Entry<String, String> entry : raw.entrySet()) {
                UUID uuid = UUID.fromString(entry.getValue());
                String name = entry.getKey().toLowerCase();

                nameToUuid.put(name, uuid);
                uuidToName.put(uuid, entry.getKey());
            }
        } catch (Exception e) {
            System.err.println("[OfflinePlayerStore] Failed to load offline player store: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Map<String, String> raw = new HashMap<>();
            for (Map.Entry<String, UUID> entry : nameToUuid.entrySet()) {
                raw.put(entry.getKey(), entry.getValue().toString());
            }

            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                GSON.toJson(raw, writer);
            }
        } catch (Exception e) {
            System.err.println("[OfflinePlayerStore] Failed to save offline player store: " + e.getMessage());
        }
    }

    public void record(String name, UUID uuid) {
        String lower = name.toLowerCase();
        nameToUuid.put(lower, uuid);
        uuidToName.put(uuid, name);
    }

    public Optional<UUID> getUUID(String name) {
        return Optional.ofNullable(nameToUuid.get(name.toLowerCase()));
    }

    public Optional<String> getName(UUID uuid) {
        return Optional.ofNullable(uuidToName.get(uuid));
    }

    public List<String> getAllNamesStartingWith(String prefix) {
        return nameToUuid.keySet().stream()
                .filter(name -> name.startsWith(prefix.toLowerCase()))
                .sorted()
                .toList();
    }
}
