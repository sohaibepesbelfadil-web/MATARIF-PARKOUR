package com.matarif.parkour.model;

import org.bukkit.Location;

/** Runtime-only state for one attempt. The monotonic start value avoids clock changes affecting the timer. */
public class ActiveRun {
    private final ParkourCourse course;
    private final long startedAtNanos;
    private int checkpointIndex;
    private Location lastCheckpoint;

    public ActiveRun(ParkourCourse course, Location start) {
        this.course = course; this.startedAtNanos = System.nanoTime(); this.lastCheckpoint = start.clone();
    }
    public ParkourCourse getCourse() { return course; }
    public long getStartedAtNanos() { return startedAtNanos; }
    public int getCheckpointIndex() { return checkpointIndex; }
    public void setCheckpointIndex(int value) { checkpointIndex = value; }
    public Location getLastCheckpoint() { return lastCheckpoint.clone(); }
    public void setLastCheckpoint(Location value) { lastCheckpoint = value.clone(); }
    public long elapsedMillis() { return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L); }
}
