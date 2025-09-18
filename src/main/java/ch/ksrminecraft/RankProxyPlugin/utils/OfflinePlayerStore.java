package ch.ksrminecraft.RankProxyPlugin.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * OfflinePlayerStore
 *
 * Hält eine lokale JSON-Datei mit Spielernamen und UUIDs für Offline-Lookups.
 * - Nutzt atomare Writes, um Datenkorruption zu vermeiden
 * - Bidirektionale Maps (name->uuid, uuid->name)
 * - Mit LogHelper für konsistentes Logging
 */
public class OfflinePlayerStore {

    private final Path filePath;
    private final Map<String, UUID> nameToUuid = new HashMap<>();
    private final Map<UUID, String> uuidToName = new HashMap<>();
    private final LogHelper log;

    private static final Gson GSON = new Gson();

    public OfflinePlayerStore(Path dataFolder, LogHelper log) {
        this.filePath = dataFolder.resolve("offline_players.json");
        this.log = log;
        load();
    }

    private void ensureParent() throws Exception {
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private void load() {
        try {
            ensureParent();
            if (!Files.exists(filePath)) {
                log.debug("[OfflinePlayerStore] Keine bestehende Datei gefunden – neuer Store wird angelegt.");
                return;
            }

            Type type = new TypeToken<Map<String, String>>() {}.getType();
            try (FileReader reader = new FileReader(filePath.toFile())) {
                Map<String, String> raw = GSON.fromJson(reader, type);
                if (raw == null) {
                    log.warn("[OfflinePlayerStore] Datei war leer – keine Spieler geladen.");
                    return;
                }

                nameToUuid.clear();
                uuidToName.clear();

                for (Map.Entry<String, String> entry : raw.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getValue());
                        String nameLower = entry.getKey().toLowerCase(Locale.ROOT);
                        nameToUuid.put(nameLower, uuid);
                        uuidToName.put(uuid, entry.getKey());
                    } catch (IllegalArgumentException ignored) {
                        log.warn("[OfflinePlayerStore] Fehlerhafte UUID übersprungen: {}", entry.getValue());
                    }
                }
                log.info("[OfflinePlayerStore] {} Spieler erfolgreich geladen.", nameToUuid.size());
            }
        } catch (Exception e) {
            log.error("[OfflinePlayerStore] Fehler beim Laden: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            ensureParent();

            // Atomarer Write: erst temp, dann move/replace
            Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");

            Map<String, String> raw = new HashMap<>();
            for (Map.Entry<String, UUID> entry : nameToUuid.entrySet()) {
                raw.put(entry.getKey(), entry.getValue().toString());
            }

            try (FileWriter writer = new FileWriter(tmp.toFile())) {
                GSON.toJson(raw, writer);
            }

            Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.debug("[OfflinePlayerStore] {} Spieler in Datei gespeichert.", nameToUuid.size());
        } catch (Exception e) {
            log.error("[OfflinePlayerStore] Fehler beim Speichern: {}", e.getMessage());
        }
    }

    public void record(String name, UUID uuid) {
        String lower = name.toLowerCase(Locale.ROOT);
        nameToUuid.put(lower, uuid);
        uuidToName.put(uuid, name);
        log.trace("[OfflinePlayerStore] Spieler '{}' mit UUID {} eingetragen.", name, uuid);
    }

    public Optional<UUID> getUUID(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(nameToUuid.get(name.toLowerCase(Locale.ROOT)));
    }

    public Optional<String> getName(UUID uuid) {
        if (uuid == null) return Optional.empty();
        return Optional.ofNullable(uuidToName.get(uuid));
    }

    public List<String> getAllNamesStartingWith(String prefix) {
        String p = (prefix == null) ? "" : prefix.toLowerCase(Locale.ROOT);
        return nameToUuid.keySet().stream()
                .filter(name -> name.startsWith(p))
                .sorted()
                .toList();
    }
}
