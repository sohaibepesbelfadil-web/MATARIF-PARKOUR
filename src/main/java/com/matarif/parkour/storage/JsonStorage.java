package com.matarif.parkour.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.matarif.parkour.model.ParkourCourse;
import com.matarif.parkour.model.PlayerRecord;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes the two JSON documents. It never touches Bukkit worlds or players, so writes can run off-thread. */
public class JsonStorage {
    private static final Type COURSE_LIST = new TypeToken<List<ParkourCourse>>(){}.getType();
    private static final Type RECORD_LIST = new TypeToken<List<PlayerRecord>>(){}.getType();
    private final Path coursesFile;
    private final Path recordsFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JsonStorage(Path folder) throws IOException {
        Files.createDirectories(folder);
        coursesFile = folder.resolve("courses.json"); recordsFile = folder.resolve("records.json");
        if (Files.notExists(coursesFile)) Files.writeString(coursesFile, "[]", StandardCharsets.UTF_8);
        if (Files.notExists(recordsFile)) Files.writeString(recordsFile, "[]", StandardCharsets.UTF_8);
    }
    public List<ParkourCourse> loadCourses() throws IOException {
        String json = Files.readString(coursesFile, StandardCharsets.UTF_8);
        List<ParkourCourse> result = gson.fromJson(json, COURSE_LIST);
        return result == null ? new ArrayList<>() : result;
    }
    public List<PlayerRecord> loadRecords() throws IOException {
        String json = Files.readString(recordsFile, StandardCharsets.UTF_8);
        List<PlayerRecord> result = gson.fromJson(json, RECORD_LIST);
        return result == null ? new ArrayList<>() : result;
    }
    public String encodeCourses(List<ParkourCourse> values) { return gson.toJson(values, COURSE_LIST); }
    public String encodeRecords(List<PlayerRecord> values) { return gson.toJson(values, RECORD_LIST); }
    public void writeCourses(String json) throws IOException { atomicWrite(coursesFile, json); }
    public void writeRecords(String json) throws IOException { atomicWrite(recordsFile, json); }
    private void atomicWrite(Path target, String content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try { Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
    }
}
