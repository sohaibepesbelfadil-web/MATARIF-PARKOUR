package com.matarif.parkour.model;

import java.util.UUID;

/** One player's best valid time for one course; times are stored in milliseconds. */
public class PlayerRecord {
    private UUID uuid;
    private String courseName;
    private long bestTimeMs;

    public PlayerRecord() { }
    public PlayerRecord(UUID uuid, String courseName, long bestTimeMs) {
        this.uuid = uuid; this.courseName = courseName; this.bestTimeMs = bestTimeMs;
    }
    public UUID getUuid() { return uuid; }
    public String getCourseName() { return courseName; }
    public long getBestTimeMs() { return bestTimeMs; }
    public void setBestTimeMs(long value) { bestTimeMs = value; }
}
