package com.matarif.parkour.task;

import com.matarif.parkour.MatarifParkour;
import com.matarif.parkour.model.ActiveRun;
import com.matarif.parkour.model.Checkpoint;
import com.matarif.parkour.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/** Draws a small marker at each player's next checkpoint without running expensive world scans. */
public class CheckpointParticleTask implements Runnable {
    private final MatarifParkour plugin;
    public CheckpointParticleTask(MatarifParkour plugin) { this.plugin = plugin; }
    @Override public void run() {
        if (!plugin.getConfig().getBoolean("settings.particles-enabled", true)) return;
        Particle particle;
        try { particle = Particle.valueOf(plugin.getConfig().getString("particles.type", "END_ROD").toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { particle = Particle.END_ROD; }
        int amount = Math.max(1, Math.min(12, plugin.getConfig().getInt("settings.particle-amount", 4)));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ActiveRun run = plugin.getRunManager().get(player); if (run == null) continue;
            int index = run.getCheckpointIndex();
            if (index >= run.getCourse().getCheckpoints().size()) continue;
            Checkpoint cp = run.getCourse().getCheckpoints().get(index);
            Location next = LocationUtil.deserialize(cp.getLocation());
            Location previous = run.getLastCheckpoint();
            if (next == null || next.getWorld() == null || !next.getWorld().equals(player.getWorld())) continue;
            next.add(0.5, 0.7, 0.5);
            if (previous.getWorld() != null && previous.getWorld().equals(next.getWorld())) {
                previous.add(0.5, 0.7, 0.5);
                // A short, sparse particle line makes the next section easier to read without flooding the world.
                for (int step = 0; step <= 8; step++) {
                    double t = step / 8.0;
                    Location point = previous.clone().add((next.getX() - previous.getX()) * t,
                            (next.getY() - previous.getY()) * t, (next.getZ() - previous.getZ()) * t);
                    player.spawnParticle(particle, point, 1, 0.04, 0.04, 0.04, 0.0);
                }
            }
            player.spawnParticle(particle, next, amount, 0.25, 0.4, 0.25, 0.01);
        }
    }
}
