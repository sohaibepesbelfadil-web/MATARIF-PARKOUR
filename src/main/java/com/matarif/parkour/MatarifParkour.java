package com.matarif.parkour;

import com.matarif.parkour.command.ParkourCommand;
import com.matarif.parkour.listener.AntiExploitListener;
import com.matarif.parkour.listener.ParkourListener;
import com.matarif.parkour.manager.CheckpointManager;
import com.matarif.parkour.manager.CourseManager;
import com.matarif.parkour.manager.LeaderboardManager;
import com.matarif.parkour.manager.RunManager;
import com.matarif.parkour.storage.StorageManager;
import com.matarif.parkour.task.ActionBarTask;
import com.matarif.parkour.task.CheckpointParticleTask;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Main plugin entry point: loads storage, wires managers/listeners, and provides shared feedback helpers. */
public final class MatarifParkour extends JavaPlugin {
    private StorageManager storageManager;
    private CourseManager courseManager;
    private CheckpointManager checkpointManager;
    private RunManager runManager;
    private LeaderboardManager leaderboardManager;
    private final Map<UUID, Long> recoveryNotices = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        try { storageManager = new StorageManager(this); }
        catch (IOException ex) { getLogger().log(Level.SEVERE, "Could not load parkour data. Plugin will disable to protect existing files.", ex); getServer().getPluginManager().disablePlugin(this); return; }
        checkpointManager = new CheckpointManager(); courseManager = new CourseManager(storageManager);
        leaderboardManager = new LeaderboardManager(storageManager); runManager = new RunManager(this, checkpointManager);
        ParkourCommand command = new ParkourCommand(this);
        if (getCommand("parkour") != null) { getCommand("parkour").setExecutor(command); getCommand("parkour").setTabCompleter(command); }
        getServer().getPluginManager().registerEvents(new ParkourListener(this), this);
        getServer().getPluginManager().registerEvents(new AntiExploitListener(this), this);
        long actionPeriod = Math.max(1L, getConfig().getLong("settings.actionbar-period-ticks", 2));
        long particlePeriod = Math.max(2L, getConfig().getLong("settings.particle-period-ticks", 10));
        getServer().getScheduler().runTaskTimer(this, new ActionBarTask(this), 1L, actionPeriod);
        getServer().getScheduler().runTaskTimer(this, new CheckpointParticleTask(this), 2L, particlePeriod);
        getLogger().info("MATARIF Parkour enabled. Loaded " + storageManager.getCourses().size() + " course(s).");
    }
    @Override public void onDisable() { if (storageManager != null) storageManager.shutdown(); }
    public StorageManager getStorageManager() { return storageManager; }
    public CourseManager getCourseManager() { return courseManager; }
    public CheckpointManager getCheckpointManager() { return checkpointManager; }
    public RunManager getRunManager() { return runManager; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public Map<UUID, Long> getRecoveryNotices() { return recoveryNotices; }

    public void rawMessage(CommandSender sender, String key) { sender.sendMessage(color(getConfig().getString("prefix", "") + getConfig().getString("messages." + key, "Message not configured: " + key))); }
    public void message(CommandSender sender, String key, Map<String, String> placeholders) {
        long now = System.currentTimeMillis();
        if (sender instanceof Player p && key.equals("command-error")) {
            // The short error cooldown only suppresses spam; it never blocks course actions.
            Long previous = errorCooldowns.get(p.getUniqueId());
            if (previous != null && now - previous < getConfig().getLong("settings.feedback-cooldown-ms", 900)) return;
            errorCooldowns.put(p.getUniqueId(), now);
        }
        String text = getConfig().getString("prefix", "") + getConfig().getString("messages." + key, "Message not configured: " + key);
        sender.sendMessage(color(replace(text, placeholders)));
    }
    private final Map<UUID, Long> errorCooldowns = new HashMap<>();
    public void title(Player player, String key, Map<String, String> placeholders) {
        if (!getConfig().getBoolean("settings.titles-enabled", true)) return;
        String base = "titles." + key + ".";
        String title = replace(getConfig().getString(base + "title", ""), placeholders);
        String subtitle = replace(getConfig().getString(base + "subtitle", ""), placeholders);
        player.sendTitle(color(title), color(subtitle), getConfig().getInt("settings.title-fade-in", 5), getConfig().getInt("settings.title-stay", 25), getConfig().getInt("settings.title-fade-out", 8));
    }
    public void playSound(Player player, String key) {
        String path = "sounds." + key + ".";
        if (!getConfig().getBoolean(path + "enabled", true)) return;
        try {
            Sound sound = Sound.valueOf(getConfig().getString(path + "type", "ENTITY_EXPERIENCE_ORB_PICKUP").toUpperCase(java.util.Locale.ROOT));
            player.playSound(player.getLocation(), sound, (float) getConfig().getDouble(path + "volume", 0.8), (float) getConfig().getDouble(path + "pitch", 1.0));
        } catch (IllegalArgumentException ex) { getLogger().warning("Invalid sound configured at " + path + "type"); }
    }
    private String replace(String text, Map<String, String> values) { String out = text; for (Map.Entry<String, String> e : values.entrySet()) out = out.replace("{" + e.getKey() + "}", e.getValue()); return out; }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text.replace("\n", "
")); }
}
