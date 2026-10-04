package com.biw.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * JSON-backed configuration stored at {@code config/biw.json} inside the game
 * directory. Loads lazily; falls back to defaults and rewrites a clean file on
 * any read error (Requirement 6.3).
 */
public final class Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private JsonObject root;

    private Config(Path path, JsonObject root) {
        this.path = path;
        this.root = root;
    }

    public static Config load() {
        Path file = resolvePath();
        JsonObject root = readOrDefault(file);
        Config config = new Config(file, root);
        if (!root.has("modules")) {
            root.add("modules", new JsonObject());
            config.save();
        }
        return config;
    }

    private static Path resolvePath() {
        // Game working directory is the Minecraft folder.
        return Paths.get("config", "biw.json").toAbsolutePath();
    }

    private static JsonObject readOrDefault(Path file) {
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject parsed = GSON.fromJson(r, JsonObject.class);
                    if (parsed != null) return parsed;
                }
            }
        } catch (Exception e) {
            System.err.println("[BIW] config read failed, using defaults: " + e);
        }
        JsonObject fresh = new JsonObject();
        fresh.add("modules", new JsonObject());
        return fresh;
    }

    /** Returns the settings object for a module id, or null if absent. */
    public JsonObject section(String moduleId) {
        JsonObject modules = root.getAsJsonObject("modules");
        if (modules == null || !modules.has(moduleId)) return null;
        return modules.getAsJsonObject(moduleId);
    }

    /** Stores a module's settings object under its id. */
    public void putSection(String moduleId, JsonObject section) {
        JsonObject modules = root.getAsJsonObject("modules");
        if (modules == null) {
            modules = new JsonObject();
            root.add("modules", modules);
        }
        modules.add(moduleId, section);
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
        } catch (IOException e) {
            System.err.println("[BIW] config save failed: " + e);
        }
    }

    /** Exposed for tests: serialize the current root to a JSON string. */
    public String toJson() {
        return GSON.toJson(root);
    }

    /** Exposed for tests: build a Config from an in-memory root (no file I/O). */
    public static Config fromRoot(JsonObject root) {
        return new Config(resolvePath(), root);
    }
}
