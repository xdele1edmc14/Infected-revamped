package me.DaWHeL.infected.loot;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class GeneratedChestRepository {
    private final File file;
    private final Map<UUID, GeneratedChestPlacement> placements = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    private boolean loadBlocked;

    public GeneratedChestRepository(File dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        file = new File(dataFolder, "generated-weapon-chests.yml");
        reload();
    }

    public synchronized List<GeneratedChestPlacement> snapshot() {
        return List.copyOf(placements.values());
    }

    public synchronized List<String> errors() {
        return List.copyOf(errors);
    }

    public synchronized List<String> reload() {
        placements.clear();
        errors.clear();
        loadBlocked = false;
        if (!file.isFile()) return List.of();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            ConfigurationSection section = yaml.getConfigurationSection("placements");
            if (section == null) return List.of();
            for (String key : section.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String path = "placements." + key;
                    GeneratedChestPlacement placement = new GeneratedChestPlacement(
                            id,
                            Objects.requireNonNull(yaml.getString(path + ".world"), "missing world"),
                            yaml.getInt(path + ".x"), yaml.getInt(path + ".y"), yaml.getInt(path + ".z"),
                            yaml.getStringList(path + ".original-ground"),
                            GeneratedChestPlacement.State.valueOf(
                                    yaml.getString(path + ".state", "ACTIVE").toUpperCase(Locale.ROOT)));
                    placements.put(id, placement);
                } catch (RuntimeException exception) {
                    loadBlocked = true;
                    errors.add("Invalid generated chest " + key + ": " + readable(exception));
                }
            }
        } catch (IOException | InvalidConfigurationException exception) {
            loadBlocked = true;
            errors.add("Could not load generated-weapon-chests.yml: " + readable(exception));
        }
        return List.copyOf(errors);
    }

    public synchronized void replaceAll(List<GeneratedChestPlacement> replacements) {
        ensureWritable();
        Map<UUID, GeneratedChestPlacement> next = new LinkedHashMap<>();
        for (GeneratedChestPlacement placement : replacements) {
            Objects.requireNonNull(placement, "placement");
            if (next.putIfAbsent(placement.id(), placement) != null) {
                throw new IllegalArgumentException("Duplicate generated chest id: " + placement.id());
            }
        }
        placements.clear();
        placements.putAll(next);
        save();
    }

    private void ensureWritable() {
        if (loadBlocked) {
            throw new IllegalStateException(
                    "generated-weapon-chests.yml is invalid and must be repaired before generated chests can change.");
        }
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create plugin data folder.");
            YamlConfiguration yaml = new YamlConfiguration();
            for (GeneratedChestPlacement placement : placements.values()) {
                String path = "placements." + placement.id();
                yaml.set(path + ".world", placement.world());
                yaml.set(path + ".x", placement.x());
                yaml.set(path + ".y", placement.y());
                yaml.set(path + ".z", placement.z());
                yaml.set(path + ".original-ground", placement.originalGround());
                yaml.set(path + ".state", placement.state().name());
            }
            File temporary = new File(parent, file.getName() + ".tmp");
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            reload();
            throw new IllegalStateException("Could not save generated-weapon-chests.yml.", exception);
        }
    }

    private static String readable(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
