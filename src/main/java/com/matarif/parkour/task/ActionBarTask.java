package com.matarif.parkour.task;

import com.matarif.parkour.MatarifParkour;
import com.matarif.parkour.model.ActiveRun;
import com.matarif.parkour.util.TimeFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Refreshes action bars from each run's monotonic elapsed time; it does not maintain time by tick counting. */
public class ActionBarTask implements Runnable {
    private final MatarifParkour plugin;
    public ActionBarTask(MatarifParkour plugin) { this.plugin = plugin; }
    @Override public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ActiveRun run = plugin.getRunManager().get(player);
            if (run != null) player.sendActionBar(Component.text(TimeFormatter.format(run.elapsedMillis())));
        }
    }
}
