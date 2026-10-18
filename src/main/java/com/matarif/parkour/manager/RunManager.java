package com.matarif.parkour.manager;

import com.matarif.parkour.MatarifParkour;
import com.matarif.parkour.model.ActiveRun;
import com.matarif.parkour.model.ParkourCourse;
import com.matarif.parkour.util.LocationUtil;
import com.matarif.parkour.util.TimeFormatter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns live run state, timer completion, recovery teleports and anti-exploit invalidation. */
public class RunManager {
    private final MatarifParkour plugin;
    private final CheckpointManager checkpoints;
    private final Map<UUID, ActiveRun> active = new HashMap<>();
    private final Set<UUID> recoveryTeleports = new HashSet<>();
    private final Map<UUID, Long> startCooldowns = new HashMap<>();
    public RunManager(MatarifParkour plugin, CheckpointManager checkpoints) { this.plugin = plugin; this.checkpoints = checkpoints; }
    public ActiveRun get(Player player) { return active.get(player.getUniqueId()); }
    public boolean isActive(Player player) { return active.containsKey(player.getUniqueId()); }
    public boolean isRecoveryTeleport(UUID uuid) { return recoveryTeleports.remove(uuid); }
    public boolean start(Player player, ParkourCourse course) {
        long now = System.currentTimeMillis();
        if (active.containsKey(player.getUniqueId())) return false;
        if (now - startCooldowns.getOrDefault(player.getUniqueId(), 0L) < plugin.getConfig().getLong("settings.start-trigger-cooldown-ms", 1200)) return false;
        Location start = checkpoints.getStart(course); if (start == null) return false;
        startCooldowns.put(player.getUniqueId(), now);
        active.put(player.getUniqueId(), new ActiveRun(course, start));
        plugin.message(player, "run-started", Map.of("course", course.getName()));
        plugin.title(player, "start", Map.of("course", course.getName()));
        plugin.playSound(player, "start");
        return true;
    }
    public boolean touchStart(Player player, Location feet) {
        for (ParkourCourse c : plugin.getCourseManager().all()) {
            if (LocationUtil.sameBlock(feet, LocationUtil.deserialize(c.getStartLocation()))) {
                if (!c.isFullyConfigured()) {
                    plugin.message(player, "course-incomplete", Map.of());
                    plugin.playSound(player, "command-error");
                    return false;
                }
                return start(player, c);
            }
        }
        return false;
    }
    public boolean touchFinish(Player player, Location feet) {
        ActiveRun run = get(player); if (run == null) return false;
        Location finish = LocationUtil.deserialize(run.getCourse().getFinishLocation());
        if (!LocationUtil.sameBlock(feet, finish)) return false;
        if (run.getCheckpointIndex() < run.getCourse().getCheckpoints().size()) {
            plugin.message(player, "checkpoints-required", Map.of());
            return true;
        }
        finish(player); return true;
    }
    public boolean touchCheckpoint(Player player, Location feet) {
        ActiveRun run = get(player); if (run == null || !plugin.getCheckpointManager().isNextCheckpoint(run, feet)) return false;
        plugin.getCheckpointManager().activate(run, feet);
        int number = run.getCheckpointIndex();
        plugin.message(player, "checkpoint-reached", Map.of("number", String.valueOf(number)));
        plugin.title(player, "checkpoint", Map.of("number", String.valueOf(number)));
        plugin.playSound(player, "checkpoint");
        return true;
    }
    public void recover(Player player, String reason) {
        ActiveRun run = get(player); if (run == null) return;
        long now = System.currentTimeMillis();
        long last = plugin.getRecoveryNotices().getOrDefault(player.getUniqueId(), 0L);
        if (now - last >= plugin.getConfig().getLong("settings.recovery-notice-cooldown-ms", 1500)) {
            plugin.getRecoveryNotices().put(player.getUniqueId(), now);
            plugin.message(player, "recovery", Map.of()); plugin.playSound(player, "recovery");
        }
        Location destination = run.getLastCheckpoint().clone();
        destination.setX(destination.getBlockX() + 0.5); destination.setZ(destination.getBlockZ() + 0.5);
        destination.setYaw(player.getLocation().getYaw()); destination.setPitch(player.getLocation().getPitch());
        destination = findSafeDestination(destination);
        if (destination.getWorld() != null) {
            recoveryTeleports.add(player.getUniqueId());
            if (!player.teleport(destination)) recoveryTeleports.remove(player.getUniqueId());
        }
    }
    private Location findSafeDestination(Location base) {
        if (base.getWorld() == null) return base;
        for (int offset = 0; offset <= 3; offset++) {
            Location candidate = base.clone().add(0, offset, 0);
            if (candidate.getBlock().isPassable() && candidate.clone().add(0, 1, 0).getBlock().isPassable()
                    && !candidate.clone().subtract(0, 1, 0).getBlock().isPassable()) return candidate;
        }
        return base;
    }
    public void invalidate(Player player, String reason) {
        ActiveRun removed = active.remove(player.getUniqueId()); if (removed == null) return;
        plugin.message(player, "run-invalidated", Map.of("reason", reason));
        plugin.title(player, "invalid", Map.of("reason", reason)); plugin.playSound(player, "invalid");
    }
    public void finish(Player player) {
        ActiveRun run = active.remove(player.getUniqueId()); if (run == null) return;
        long time = run.elapsedMillis(); String formatted = TimeFormatter.format(time);
        plugin.message(player, "run-finished", Map.of("course", run.getCourse().getName(), "time", formatted));
        plugin.title(player, "finish", Map.of("time", formatted, "course", run.getCourse().getName()));
        plugin.playSound(player, "finish");
        boolean record = plugin.getStorageManager().saveBest(player.getUniqueId(), run.getCourse().getName(), time);
        if (record) {
            plugin.message(player, "new-record", Map.of("time", formatted)); plugin.title(player, "record", Map.of("time", formatted)); plugin.playSound(player, "record");
        }
    }
    public void remove(Player player) { active.remove(player.getUniqueId()); startCooldowns.remove(player.getUniqueId()); plugin.getRecoveryNotices().remove(player.getUniqueId()); recoveryTeleports.remove(player.getUniqueId()); }
    public Map<UUID, ActiveRun> activeRuns() { return active; }
}
