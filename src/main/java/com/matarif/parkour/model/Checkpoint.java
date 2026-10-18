package com.matarif.parkour.model;

/**
 * A saved checkpoint in a course. The location is stored as a plain string instead of a Bukkit
 * Location so the data remains simple JSON and can be loaded even before a world is available.
 */
public class Checkpoint {
    private int number;
    private String location;

    public Checkpoint() { }
    public Checkpoint(int number, String location) { this.number = number; this.location = location; }
    public int getNumber() { return number; }
    public String getLocation() { return location; }
}
