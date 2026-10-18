package com.matarif.parkour.storage;

import com.matarif.parkour.model.ParkourCourse;
import com.matarif.parkour.model.PlayerRecord;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Owns loaded course/record maps and queues immutable JSON snapshots onto one writer thread. */
public class StorageManager {
    private final JavaPlugin plugin;
    private final JsonStorage json;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "MATARIF-Parkour-Storage"); t.setDaemon(true); return t; });
    private final Map<String, ParkourCourse> courses = new LinkedHashMap<>();
    private final Map<String, PlayerRecord> records = new LinkedHashMap<>();

    public StorageManager(JavaPlugin plugin) throws IOException {
        this.plugin = plugin; this.json = new JsonStorage(plugin.getDataFolder().toPath());
        try {
            for (ParkourCourse c : json.loadCourses()) if (c != null && c.getName() != null) courses.put(key(c.getName()), c);
            for (PlayerRecord r : json.loadRecords()) if (r != null && r.getUuid() != null && r.getCourseName() != null) records.put(recordKey(r.getUuid(), r.getCourseName()), r);
        } catch (Exception ex) {
            throw new IOException("Could not load saved JSON data. Check courses.json and records.json; files were not overwritten.", ex);
        }
    }
    public synchronized Collection<ParkourCourse> getCourses() { return new ArrayList<>(courses.values()); }
    public synchronized ParkourCourse getCourse(String name) { return courses.get(key(name)); }
    public synchronized boolean createCourse(ParkourCourse course) {
        String k = key(course.getName()); if (courses.containsKey(k)) return false; courses.put(k, course); saveCourses(); return true;
    }
    public synchronized void updateCourse(ParkourCourse course) { courses.put(key(course.getName()), course); saveCourses(); }
    public synchronized PlayerRecord getRecord(UUID uuid, String course) { return records.get(recordKey(uuid, course)); }
    public synchronized List<PlayerRecord> getRecords(String course) {
        List<PlayerRecord> result = new ArrayList<>();
        for (PlayerRecord r : records.values()) if (r.getCourseName().equalsIgnoreCase(course)) result.add(r);
        return result;
    }
    public synchronized boolean saveBest(UUID uuid, String course, long millis) {
        String k = recordKey(uuid, course); PlayerRecord old = records.get(k);
        if (old != null && old.getBestTimeMs() <= millis) return false;
        records.put(k, new PlayerRecord(uuid, course, millis)); saveRecords(); return true;
    }
    public synchronized void saveCourses() {
        String snapshot = json.encodeCourses(new ArrayList<>(courses.values()));
        writer.submit(() -> writeSafely(() -> json.writeCourses(snapshot), "courses.json"));
    }
    public synchronized void saveRecords() {
        String snapshot = json.encodeRecords(new ArrayList<>(records.values()));
        writer.submit(() -> writeSafely(() -> json.writeRecords(snapshot), "records.json"));
    }
    private void writeSafely(IoAction action, String file) {
        try { action.run(); } catch (IOException ex) { plugin.getLogger().log(Level.SEVERE, "Could not save " + file + ".", ex); }
    }
    private static String key(String value) { return value.toLowerCase(java.util.Locale.ROOT); }
    private static String recordKey(UUID uuid, String course) { return uuid + "|" + key(course); }
    public void shutdown() {
        saveCourses(); saveRecords(); writer.shutdown();
        try { if (!writer.awaitTermination(5, TimeUnit.SECONDS)) { writer.shutdownNow(); plugin.getLogger().warning("Storage writer did not finish before shutdown."); } }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); writer.shutdownNow(); }
    }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
}
