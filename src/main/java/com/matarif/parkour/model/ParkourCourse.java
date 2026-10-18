package com.matarif.parkour.model;

import java.util.ArrayList;
import java.util.List;

/** Stores a course's saved pad locations and ordered checkpoints. Location strings keep this model Gson-friendly. */
public class ParkourCourse {
    private String name;
    private String startLocation;
    private String finishLocation;
    private double failY = 60.0;
    private List<Checkpoint> checkpoints = new ArrayList<>();

    public ParkourCourse() { }
    public ParkourCourse(String name, double failY) { this.name = name; this.failY = failY; }
    public String getName() { return name; }
    public String getStartLocation() { return startLocation; }
    public void setStartLocation(String value) { startLocation = value; }
    public String getFinishLocation() { return finishLocation; }
    public void setFinishLocation(String value) { finishLocation = value; }
    public double getFailY() { return failY; }
    public void setFailY(double value) { failY = value; }
    public List<Checkpoint> getCheckpoints() { if (checkpoints == null) checkpoints = new ArrayList<>(); return checkpoints; }
    public void addCheckpoint(Checkpoint checkpoint) { getCheckpoints().add(checkpoint); }
    public boolean isFullyConfigured() { return startLocation != null && finishLocation != null; }
}
