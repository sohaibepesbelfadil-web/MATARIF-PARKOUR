package com.matarif.parkour.manager;

import com.matarif.parkour.model.ParkourCourse;
import com.matarif.parkour.storage.StorageManager;
import com.matarif.parkour.util.LocationUtil;
import org.bukkit.Location;
import java.util.Collection;

/** Applies course setup rules and keeps command code free of persistence details. */
public class CourseManager {
    private final StorageManager storage;
    public CourseManager(StorageManager storage) { this.storage = storage; }
    public ParkourCourse get(String name) { return storage.getCourse(name); }
    public Collection<ParkourCourse> all() { return storage.getCourses(); }
    public boolean create(String name, double defaultFailY) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,32}")) return false;
        return storage.createCourse(new ParkourCourse(name, defaultFailY));
    }
    public void setStart(ParkourCourse course, Location location) { course.setStartLocation(LocationUtil.serialize(LocationUtil.feetBlock(location))); storage.updateCourse(course); }
    public void setFinish(ParkourCourse course, Location location) { course.setFinishLocation(LocationUtil.serialize(LocationUtil.feetBlock(location))); storage.updateCourse(course); }
    public boolean addCheckpoint(ParkourCourse course, int number, Location location) {
        int expected = course.getCheckpoints().size() + 1;
        if (number != expected) return false;
        course.addCheckpoint(new com.matarif.parkour.model.Checkpoint(number, LocationUtil.serialize(LocationUtil.feetBlock(location))));
        storage.updateCourse(course); return true;
    }
    public void setFailY(ParkourCourse course, double y) { course.setFailY(y); storage.updateCourse(course); }
}
