package com.matarif.parkour.manager;

import com.matarif.parkour.model.ActiveRun;
import com.matarif.parkour.model.Checkpoint;
import com.matarif.parkour.model.ParkourCourse;
import com.matarif.parkour.util.LocationUtil;
import org.bukkit.Location;

/** Finds the next expected checkpoint and advances a run only when the player touches it in order. */
public class CheckpointManager {
    public Checkpoint next(ActiveRun run) {
        int index = run.getCheckpointIndex();
        return index < run.getCourse().getCheckpoints().size() ? run.getCourse().getCheckpoints().get(index) : null;
    }
    public boolean isNextCheckpoint(ActiveRun run, Location feetLocation) {
        Checkpoint cp = next(run); if (cp == null) return false;
        return LocationUtil.sameBlock(feetLocation, LocationUtil.deserialize(cp.getLocation()));
    }
    public void activate(ActiveRun run, Location feetLocation) {
        run.setCheckpointIndex(run.getCheckpointIndex() + 1);
        run.setLastCheckpoint(feetLocation.clone());
    }
    public Location getStart(ParkourCourse course) { return LocationUtil.deserialize(course.getStartLocation()); }
}
