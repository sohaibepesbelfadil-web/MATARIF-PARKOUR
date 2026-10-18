# MATARIF Parkour — complete source bundle

This document contains every project file, with a short purpose note before each one. For a real build, keep each file in its own path inside the project directory.

## `.gitignore`

**Purpose and implementation note:** Keeps generated build output and IDE files out of Git.

```text
target/
.idea/
.vscode/
*.iml
```

## `README.md`

**Purpose and implementation note:** Setup, commands, course-pad placement rules, and build/install instructions.

```markdown
# MATARIF Parkour

A Paper 1.21.x parkour plugin with timed runs, ordered checkpoints, JSON persistence, personal records, and a top-three leaderboard.

## Requirements
- Java 21
- Maven 3.8 or newer
- Paper 1.21.x (the project targets Paper API 1.21.4)

## Build
Run `mvn clean package` from this folder. The shaded plugin JAR is `target/MATARIF-Parkour.jar`.

## Install
Copy the JAR into the server's `plugins` folder, restart the server, then configure a course while standing in the block-space directly above each pad:

1. `/parkour create CloudHop`
2. Stand on the start pressure plate and run `/parkour setstart CloudHop`.
3. Stand on the finish pad and run `/parkour setfinish CloudHop`.
4. Stand in the intended checkpoint block-space and run `/parkour addcheckpoint CloudHop 1`, then repeat with 2, 3, etc.
5. Set the fall boundary with `/parkour setfaily CloudHop 60`.

The saved pad location is the player's feet block in the same world. Start, finish and checkpoint detection uses that block coordinate, so stand in the exact block-space you want players to enter. Courses and records are saved under `plugins/MATARIFParkour/`.

## Commands
- `/parkour create <name>` (admin)
- `/parkour setstart <name>` (admin)
- `/parkour setfinish <name>` (admin)
- `/parkour addcheckpoint <name> <number>` (admin; sequential numbers required)
- `/parkour setfaily <name> <y>` (admin)
- `/parkour leaderboard <name>` (available to players)

Admin permission: `matarifparkour.admin` (default: operators).

## Notes
Timer updates use a monotonic clock. Checkpoint recovery does not reset the timer. JSON writes are serialized through a single background executor; Bukkit world/player operations stay on the server thread. Standard Bukkit/Paper events can detect common prohibited actions, but no event-only anti-cheat can reliably identify every possible velocity exploit.
```

## `pom.xml`

**Purpose and implementation note:** Maven build and dependency setup. Targets Java 21 and Paper 1.21.4, and shades Gson into the plugin.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- Build notes: targets Java 21 and Paper 1.21.4; Gson is shaded into this plugin so storage does not depend on server internals. -->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.matarif</groupId><artifactId>matarif-parkour</artifactId><version>1.0.0</version><packaging>jar</packaging>
  <properties><maven.compiler.release>21</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
  <repositories><repository><id>papermc</id><url>https://repo.papermc.io/repository/maven-public/</url></repository></repositories>
  <dependencies>
    <dependency><groupId>io.papermc.paper</groupId><artifactId>paper-api</artifactId><version>1.21.4-R0.1-SNAPSHOT</version><scope>provided</scope></dependency>
    <dependency><groupId>com.google.code.gson</groupId><artifactId>gson</artifactId><version>2.11.0</version></dependency>
  </dependencies>
  <build><finalName>MATARIF-Parkour</finalName><plugins>
    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.13.0</version><configuration><release>21</release></configuration></plugin>
    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-shade-plugin</artifactId><version>3.6.0</version><executions><execution><phase>package</phase><goals><goal>shade</goal></goals><configuration><createDependencyReducedPom>false</createDependencyReducedPom><relocations><relocation><pattern>com.google.gson</pattern><shadedPattern>com.matarif.parkour.libs.gson</shadedPattern></relocation></relocations></configuration></execution></executions></plugin>
  </plugins></build>
</project>
```

## `src/main/java/com/matarif/parkour/MatarifParkour.java`

**Purpose and implementation note:** Main lifecycle class. Loads data, wires managers and listeners, and centralizes messages, titles, and sounds.

```java
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
```

## `src/main/java/com/matarif/parkour/command/ParkourCommand.java`

**Purpose and implementation note:** Validates commands and handles course setup, tab completion, and leaderboard output.

```java
package com.matarif.parkour.command;

import com.matarif.parkour.MatarifParkour;
import com.matarif.parkour.model.ParkourCourse;
import com.matarif.parkour.model.PlayerRecord;
import com.matarif.parkour.util.TimeFormatter;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Implements /parkour, keeping argument checks and player feedback in one place. */
public class ParkourCommand implements CommandExecutor, TabCompleter {
    private final MatarifParkour plugin;
    public ParkourCommand(MatarifParkour plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { plugin.rawMessage(sender, "help"); return true; }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("leaderboard")) { leaderboard(sender, args); return true; }
        if (!(sender instanceof Player player)) { plugin.rawMessage(sender, "player-only"); return true; }
        if (!player.hasPermission("matarifparkour.admin")) { plugin.message(player, "no-permission", Map.of()); plugin.playSound(player, "command-error"); return true; }
        try {
            switch (sub) {
                case "create" -> create(player, args);
                case "setstart" -> setPad(player, args, true);
                case "setfinish" -> setPad(player, args, false);
                case "addcheckpoint" -> addCheckpoint(player, args);
                case "setfaily" -> setFailY(player, args);
                default -> { plugin.rawMessage(player, "unknown-subcommand"); plugin.playSound(player, "command-error"); }
            }
        } catch (NumberFormatException ex) {
            plugin.message(player, "invalid-number", Map.of()); plugin.playSound(player, "command-error");
        } catch (Exception ex) {
            plugin.getLogger().warning("Command /parkour " + sub + " failed: " + ex.getMessage());
            plugin.message(player, "command-error", Map.of()); plugin.playSound(player, "command-error");
        }
        return true;
    }
    private void create(Player player, String[] args) {
        if (args.length != 2) { plugin.rawMessage(player, "usage"); return; }
        String name = args[1];
        if (plugin.getCourseManager().get(name) != null) { plugin.message(player, "course-exists", Map.of("course", name)); plugin.playSound(player, "command-error"); return; }
        boolean ok = plugin.getCourseManager().create(name, plugin.getConfig().getDouble("settings.default-fail-y", 60.0));
        if (!ok) { plugin.message(player, "command-error", Map.of()); plugin.playSound(player, "command-error"); return; }
        plugin.message(player, "course-created", Map.of("course", name)); plugin.playSound(player, "command-success");
    }
    private void setPad(Player player, String[] args, boolean start) {
        if (args.length != 2) { plugin.rawMessage(player, "usage"); return; }
        ParkourCourse course = course(player, args[1]); if (course == null) return;
        if (start) plugin.getCourseManager().setStart(course, player.getLocation()); else plugin.getCourseManager().setFinish(course, player.getLocation());
        plugin.message(player, start ? "start-set" : "finish-set", Map.of("course", course.getName())); plugin.playSound(player, "command-success");
    }
    private void addCheckpoint(Player player, String[] args) {
        if (args.length != 3) { plugin.rawMessage(player, "usage"); return; }
        ParkourCourse course = course(player, args[1]); if (course == null) return;
        int number = Integer.parseInt(args[2]); int expected = course.getCheckpoints().size() + 1;
        if (number < 1 || !plugin.getCourseManager().addCheckpoint(course, number, player.getLocation())) {
            plugin.message(player, "invalid-checkpoint", Map.of("expected", String.valueOf(expected))); plugin.playSound(player, "command-error"); return;
        }
        plugin.message(player, "checkpoint-added", Map.of("number", String.valueOf(number), "course", course.getName())); plugin.playSound(player, "command-success");
    }
    private void setFailY(Player player, String[] args) {
        if (args.length != 3) { plugin.rawMessage(player, "usage"); return; }
        ParkourCourse course = course(player, args[1]); if (course == null) return;
        double y = Double.parseDouble(args[2]);
        if (!Double.isFinite(y) || y < player.getWorld().getMinHeight() || y > player.getWorld().getMaxHeight()) { plugin.message(player, "invalid-number", Map.of()); return; }
        plugin.getCourseManager().setFailY(course, y); plugin.message(player, "fail-y-set", Map.of("y", String.valueOf(y), "course", course.getName())); plugin.playSound(player, "command-success");
    }
    private ParkourCourse course(Player player, String name) {
        ParkourCourse c = plugin.getCourseManager().get(name);
        if (c == null) { plugin.message(player, "course-not-found", Map.of("course", name)); plugin.playSound(player, "command-error"); }
        return c;
    }
    private void leaderboard(CommandSender sender, String[] args) {
        if (args.length != 2) { plugin.rawMessage(sender, "usage"); return; }
        ParkourCourse course = plugin.getCourseManager().get(args[1]);
        if (course == null) { plugin.message(sender, "course-not-found", Map.of("course", args[1])); return; }
        List<PlayerRecord> top = plugin.getLeaderboardManager().topThree(course.getName());
        plugin.message(sender, "leaderboard-title", Map.of("course", course.getName()));
        if (sender instanceof Player player) plugin.playSound(player, "command-success");
        if (top.isEmpty()) { plugin.message(sender, "no-records", Map.of("course", course.getName())); return; }
        for (int i = 0; i < top.size(); i++) {
            PlayerRecord r = top.get(i);
            plugin.message(sender, "leaderboard-line", Map.of("rank", String.valueOf(i + 1), "player", plugin.getLeaderboardManager().playerName(r), "time", TimeFormatter.format(r.getBestTimeMs())));
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("create", "setstart", "setfinish", "addcheckpoint", "setfaily", "leaderboard")) {
                if (sub.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) result.add(sub);
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
            for (ParkourCourse c : plugin.getCourseManager().all()) {
                if (c.getName().toLowerCase(java.util.Locale.ROOT).startsWith(args[1].toLowerCase(java.util.Locale.ROOT))) result.add(c.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("addcheckpoint")) {
            ParkourCourse c = plugin.getCourseManager().get(args[1]);
            result.add(String.valueOf(c == null ? 1 : c.getCheckpoints().size() + 1));
        }
        return result;
    }
}
```

## `src/main/java/com/matarif/parkour/listener/AntiExploitListener.java`

**Purpose and implementation note:** Invalidates active runs for common prohibited abilities and external movement; event-only checks cannot detect every custom exploit.

```java
package com.matarif.parkour.listener;

import com.matarif.parkour.MatarifParkour;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

/** Invalidates active runs for common prohibited abilities; event-based checks cannot identify every custom velocity exploit. */
public class AntiExploitListener implements Listener {
    private final MatarifParkour plugin;
    public AntiExploitListener(MatarifParkour plugin) { this.plugin = plugin; }
    @EventHandler public void onFlight(PlayerToggleFlightEvent event) { if (event.isFlying()) invalidate(event.getPlayer(), "flight"); }
    @EventHandler public void onGlide(EntityToggleGlideEvent event) { if (event.getEntity() instanceof Player p && event.isGliding()) invalidate(p, "elytra gliding"); }
    @EventHandler public void onPearl(ProjectileLaunchEvent event) { if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player p) invalidate(p, "ender pearl"); }
    @EventHandler public void onTeleport(PlayerTeleportEvent event) {
        if (!plugin.getRunManager().isActive(event.getPlayer())) return;
        if (plugin.getRunManager().isRecoveryTeleport(event.getPlayer().getUniqueId())) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) invalidate(event.getPlayer(), "ender pearl");
        else invalidate(event.getPlayer(), "external teleport");
    }
    @EventHandler public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || !plugin.getRunManager().isActive(p)) return;
        switch (event.getCause()) {
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE, BLOCK_EXPLOSION, ENTITY_EXPLOSION -> invalidate(p, "damage or knockback boost");
            default -> { /* Environmental damage is not treated as a boost; falling still uses checkpoint recovery. */ }
        }
    }
    @EventHandler public void onVelocity(PlayerVelocityEvent event) {
        if (plugin.getRunManager().isActive(event.getPlayer()) && event.getVelocity().lengthSquared() > 1.25) invalidate(event.getPlayer(), "external velocity boost");
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { invalidate(event.getPlayer(), "world change"); }
    private void invalidate(Player p, String reason) { if (plugin.getRunManager().isActive(p)) plugin.getRunManager().invalidate(p, reason); }
}
```

## `src/main/java/com/matarif/parkour/listener/ParkourListener.java`

**Purpose and implementation note:** Detects pad/checkpoint contact, hazards, fail-height recovery, and disconnect cleanup.

```java
package com.matarif.parkour.listener;

import com.matarif.parkour.MatarifParkour;
import com.matarif.parkour.model.ActiveRun;
import com.matarif.parkour.model.ParkourCourse;
import com.matarif.parkour.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Handles block-space contact, fail-height recovery and player cleanup for active runs. */
public class ParkourListener implements Listener {
    private final MatarifParkour plugin;
    private final Map<UUID, Long> feedbackCooldowns = new HashMap<>();
    public ParkourListener(MatarifParkour plugin) { this.plugin = plugin; }
    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Location from = event.getFrom(), to = event.getTo();
        if (from.getWorld() == null || to.getWorld() == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ() && from.getWorld().equals(to.getWorld())) return;
        Player player = event.getPlayer(); Location feet = LocationUtil.feetBlock(to);
        ActiveRun run = plugin.getRunManager().get(player);
        if (run == null) { plugin.getRunManager().touchStart(player, feet); return; }
        if (to.getY() < run.getCourse().getFailY()) { plugin.getRunManager().recover(player, "fall"); return; }
        Material feetType = to.getBlock().getType();
        Material bodyType = to.clone().add(0, 1, 0).getBlock().getType();
        if (isHazard(feetType) || isHazard(bodyType)) { plugin.getRunManager().recover(player, "hazard"); return; }
        if (plugin.getRunManager().touchCheckpoint(player, feet)) return;
        plugin.getRunManager().touchFinish(player, feet);
    }
    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null) return;
        Material type = event.getClickedBlock().getType();
        if (type != Material.STONE_PRESSURE_PLATE && type != Material.OAK_PRESSURE_PLATE && type != Material.SPRUCE_PRESSURE_PLATE && type != Material.BIRCH_PRESSURE_PLATE && type != Material.JUNGLE_PRESSURE_PLATE && type != Material.ACACIA_PRESSURE_PLATE && type != Material.DARK_OAK_PRESSURE_PLATE && type != Material.CRIMSON_PRESSURE_PLATE && type != Material.WARPED_PRESSURE_PLATE && type != Material.POLISHED_BLACKSTONE_PRESSURE_PLATE && type != Material.LIGHT_WEIGHTED_PRESSURE_PLATE && type != Material.HEAVY_WEIGHTED_PRESSURE_PLATE && type != Material.MANGROVE_PRESSURE_PLATE && type != Material.CHERRY_PRESSURE_PLATE && type != Material.BAMBOO_PRESSURE_PLATE) return;
        Location feet = event.getPlayer().getLocation().getBlock().getLocation();
        if (plugin.getRunManager().get(event.getPlayer()) == null) plugin.getRunManager().touchStart(event.getPlayer(), feet);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { plugin.getRunManager().remove(event.getPlayer()); }
    private boolean isHazard(Material m) { return m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN; }
}
```

## `src/main/java/com/matarif/parkour/manager/CheckpointManager.java`

**Purpose and implementation note:** Finds the next required checkpoint and advances checkpoints in order.

```java
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
```

## `src/main/java/com/matarif/parkour/manager/CourseManager.java`

**Purpose and implementation note:** Applies validation and saves course setup changes through storage.

```java
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
```

## `src/main/java/com/matarif/parkour/manager/LeaderboardManager.java`

**Purpose and implementation note:** Sorts saved personal-best records and resolves player names.

```java
package com.matarif.parkour.manager;

import com.matarif.parkour.model.PlayerRecord;
import com.matarif.parkour.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Reads valid persisted personal-best records and returns the fastest three for a course. */
public class LeaderboardManager {
    private final StorageManager storage;
    public LeaderboardManager(StorageManager storage) { this.storage = storage; }
    public List<PlayerRecord> topThree(String course) {
        return storage.getRecords(course).stream().filter(r -> r.getBestTimeMs() > 0)
                .sorted(Comparator.comparingLong(PlayerRecord::getBestTimeMs)).limit(3).collect(Collectors.toList());
    }
    public String playerName(PlayerRecord record) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(record.getUuid());
        return player.getName() == null ? record.getUuid().toString().substring(0, 8) : player.getName();
    }
}
```

## `src/main/java/com/matarif/parkour/manager/RunManager.java`

**Purpose and implementation note:** Owns active run state, monotonic timing, checkpoint recovery, completion, records, and invalidation.

```java
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
```

## `src/main/java/com/matarif/parkour/model/ActiveRun.java`

**Purpose and implementation note:** Stores runtime-only timer and checkpoint state for one attempt.

```java
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
```

## `src/main/java/com/matarif/parkour/model/Checkpoint.java`

**Purpose and implementation note:** Stores the ordered checkpoint number and serialized location.

```java
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
```

## `src/main/java/com/matarif/parkour/model/ParkourCourse.java`

**Purpose and implementation note:** Stores course pads, failure height, and checkpoints.

```java
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
```

## `src/main/java/com/matarif/parkour/model/PlayerRecord.java`

**Purpose and implementation note:** Stores UUID, course name, and best completion time in milliseconds.

```java
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
```

## `src/main/java/com/matarif/parkour/storage/JsonStorage.java`

**Purpose and implementation note:** Reads and writes JSON using temporary files and atomic replacement where supported.

```java
package com.matarif.parkour.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.matarif.parkour.model.ParkourCourse;
import com.matarif.parkour.model.PlayerRecord;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes the two JSON documents. It never touches Bukkit worlds or players, so writes can run off-thread. */
public class JsonStorage {
    private static final Type COURSE_LIST = new TypeToken<List<ParkourCourse>>(){}.getType();
    private static final Type RECORD_LIST = new TypeToken<List<PlayerRecord>>(){}.getType();
    private final Path coursesFile;
    private final Path recordsFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JsonStorage(Path folder) throws IOException {
        Files.createDirectories(folder);
        coursesFile = folder.resolve("courses.json"); recordsFile = folder.resolve("records.json");
        if (Files.notExists(coursesFile)) Files.writeString(coursesFile, "[]", StandardCharsets.UTF_8);
        if (Files.notExists(recordsFile)) Files.writeString(recordsFile, "[]", StandardCharsets.UTF_8);
    }
    public List<ParkourCourse> loadCourses() throws IOException {
        String json = Files.readString(coursesFile, StandardCharsets.UTF_8);
        List<ParkourCourse> result = gson.fromJson(json, COURSE_LIST);
        return result == null ? new ArrayList<>() : result;
    }
    public List<PlayerRecord> loadRecords() throws IOException {
        String json = Files.readString(recordsFile, StandardCharsets.UTF_8);
        List<PlayerRecord> result = gson.fromJson(json, RECORD_LIST);
        return result == null ? new ArrayList<>() : result;
    }
    public String encodeCourses(List<ParkourCourse> values) { return gson.toJson(values, COURSE_LIST); }
    public String encodeRecords(List<PlayerRecord> values) { return gson.toJson(values, RECORD_LIST); }
    public void writeCourses(String json) throws IOException { atomicWrite(coursesFile, json); }
    public void writeRecords(String json) throws IOException { atomicWrite(recordsFile, json); }
    private void atomicWrite(Path target, String content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try { Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
    }
}
```

## `src/main/java/com/matarif/parkour/storage/StorageManager.java`

**Purpose and implementation note:** Keeps loaded data in memory and serializes background writes through a single executor.

```java
package com.matarif.parkour.storage;

import com.matarif.parkour.model.ParkourCourse;
import com.matarif.parkour.model.PlayerRecord;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Owns loaded course/record maps and queues immutable JSON snapshots onto one writer thread. */
public class StorageManager {
    private final JavaPlugin plugin;
    private final JsonStorage json;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "MATARIF-Parkour-Storage"); t.setDaemon(true); return t; });
    private final Map<String, ParkourCourse> courses = new LinkedHashMap<>();
    private final Map<String, PlayerRecord> records = new LinkedHashMap<>();

    public StorageManager(JavaPlugin plugin) throws IOException {
        this.plugin = plugin; this.json = new JsonStorage(plugin.getDataFolder().toPath());
        try {
            for (ParkourCourse c : json.loadCourses()) if (c != null && c.getName() != null) courses.put(key(c.getName()), c);
            for (PlayerRecord r : json.loadRecords()) if (r != null && r.getUuid() != null && r.getCourseName() != null) records.put(recordKey(r.getUuid(), r.getCourseName()), r);
        } catch (Exception ex) {
            throw new IOException("Could not load saved JSON data. Check courses.json and records.json; files were not overwritten.", ex);
        }
    }
    public synchronized Collection<ParkourCourse> getCourses() { return new ArrayList<>(courses.values()); }
    public synchronized ParkourCourse getCourse(String name) { return courses.get(key(name)); }
    public synchronized boolean createCourse(ParkourCourse course) {
        String k = key(course.getName()); if (courses.containsKey(k)) return false; courses.put(k, course); saveCourses(); return true;
    }
    public synchronized void updateCourse(ParkourCourse course) { courses.put(key(course.getName()), course); saveCourses(); }
    public synchronized PlayerRecord getRecord(UUID uuid, String course) { return records.get(recordKey(uuid, course)); }
    public synchronized List<PlayerRecord> getRecords(String course) {
        List<PlayerRecord> result = new ArrayList<>();
        for (PlayerRecord r : records.values()) if (r.getCourseName().equalsIgnoreCase(course)) result.add(r);
        return result;
    }
    public synchronized boolean saveBest(UUID uuid, String course, long millis) {
        String k = recordKey(uuid, course); PlayerRecord old = records.get(k);
        if (old != null && old.getBestTimeMs() <= millis) return false;
        records.put(k, new PlayerRecord(uuid, course, millis)); saveRecords(); return true;
    }
    public synchronized void saveCourses() {
        String snapshot = json.encodeCourses(new ArrayList<>(courses.values()));
        writer.submit(() -> writeSafely(() -> json.writeCourses(snapshot), "courses.json"));
    }
    public synchronized void saveRecords() {
        String snapshot = json.encodeRecords(new ArrayList<>(records.values()));
        writer.submit(() -> writeSafely(() -> json.writeRecords(snapshot), "records.json"));
    }
    private void writeSafely(IoAction action, String file) {
        try { action.run(); } catch (IOException ex) { plugin.getLogger().log(Level.SEVERE, "Could not save " + file + ".", ex); }
    }
    private static String key(String value) { return value.toLowerCase(java.util.Locale.ROOT); }
    private static String recordKey(UUID uuid, String course) { return uuid + "|" + key(course); }
    public void shutdown() {
        saveCourses(); saveRecords(); writer.shutdown();
        try { if (!writer.awaitTermination(5, TimeUnit.SECONDS)) { writer.shutdownNow(); plugin.getLogger().warning("Storage writer did not finish before shutdown."); } }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); writer.shutdownNow(); }
    }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
}
```

## `src/main/java/com/matarif/parkour/task/ActionBarTask.java`

**Purpose and implementation note:** Displays accurate live elapsed time using the run’s monotonic clock.

```java
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
```

## `src/main/java/com/matarif/parkour/task/CheckpointParticleTask.java`

**Purpose and implementation note:** Shows a subtle particle line toward the next checkpoint and marks its location.

```java
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
```

## `src/main/java/com/matarif/parkour/util/LocationUtil.java`

**Purpose and implementation note:** Converts locations to JSON-friendly strings and compares exact block positions.

```java
package com.matarif.parkour.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Serializes locations without Bukkit object graphs and centralizes exact feet-block comparisons. */
public final class LocationUtil {
    private LocationUtil() { }
    public static String serialize(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + "|" + loc.getX() + "|" + loc.getY() + "|" + loc.getZ() + "|" + loc.getYaw() + "|" + loc.getPitch();
    }
    public static Location deserialize(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] p = value.split("\\|", -1);
            if (p.length != 6) return null;
            World world = Bukkit.getWorld(p[0]);
            if (world == null) return null;
            return new Location(world, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (IllegalArgumentException ex) { return null; }
    }
    public static Location feetBlock(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    public static boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && b.getWorld() != null
                && a.getWorld().getUID().equals(b.getWorld().getUID())
                && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }
}
```

## `src/main/java/com/matarif/parkour/util/TimeFormatter.java`

**Purpose and implementation note:** Formats elapsed milliseconds as MM:SS.mmm.

```java
package com.matarif.parkour.util;

/** Keeps all timer displays consistent across action bars, titles, messages and leaderboards. */
public final class TimeFormatter {
    private TimeFormatter() { }
    public static String format(long millis) {
        millis = Math.max(0L, millis);
        long minutes = millis / 60_000L;
        long seconds = (millis / 1_000L) % 60L;
        long ms = millis % 1_000L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, ms);
    }
}
```

## `src/main/resources/config.yml`

**Purpose and implementation note:** Player messages, titles, cooldowns, sound effects, timer refresh rate, and particles.

```yaml
# Runtime settings and player-facing feedback. Sound names must be valid Bukkit Sound enum constants.
prefix: "&8[&cMATARIF Parkour&8] &7"
settings:
  default-fail-y: 60.0
  actionbar-period-ticks: 2
  particle-period-ticks: 10
  particle-amount: 4
  particles-enabled: true
  feedback-cooldown-ms: 900
  recovery-notice-cooldown-ms: 1500
  start-trigger-cooldown-ms: 1200
  titles-enabled: true
  title-fade-in: 5
  title-stay: 25
  title-fade-out: 8
messages:
  no-permission: "&cYou don't have permission to do that."
  player-only: "&cThis command can only be used in-game."
  usage: "&cUsage: /parkour <create|setstart|setfinish|addcheckpoint|setfaily|leaderboard>"
  unknown-subcommand: "&cUnknown subcommand. Use /parkour for help."
  course-created: "&aCourse '{course}' created. Stand on each pad and run its setup command."
  course-exists: "&cCourse '{course}' already exists."
  course-not-found: "&cCourse '{course}' doesn't exist."
  start-set: "&aStart pad saved for '{course}'."
  finish-set: "&aFinish pad saved for '{course}'."
  checkpoint-added: "&aCheckpoint {number} saved for '{course}'."
  invalid-checkpoint: "&cThe next checkpoint number must be {expected}."
  fail-y-set: "&aFailure Y-level set to {y} for '{course}'."
  course-incomplete: "&cThis course needs a start and finish pad before it can be played."
  checkpoints-required: "&cTouch every checkpoint before finishing the course."
  run-started: "&aRun started: &f{course}"
  checkpoint-reached: "&bCheckpoint {number} reached!"
  recovery: "&7Returning to your last checkpoint. Your timer is still running."
  run-invalidated: "&cRun invalidated: {reason}"
  run-finished: "&aFinished {course} in &f{time}&a."
  new-record: "&6&lPERSONAL BEST! &e{time}"
  old-record: "&7Your best time is still {time}."
  no-records: "&7No completed runs have been recorded for '{course}' yet."
  leaderboard-title: "&6&l{course} &7- Top 3"
  leaderboard-line: "&e#{rank} &f{player} &7- &b{time}"
  invalid-number: "&cEnter a valid number."
  command-error: "&cThat command couldn't be completed. Check the course name and arguments."
  help: "&e/parkour create <name> &7- Create a course
&e/parkour setstart <name> &7- Set the start pad
&e/parkour setfinish <name> &7- Set the finish pad
&e/parkour addcheckpoint <name> <number> &7- Add a checkpoint
&e/parkour setfaily <name> <y> &7- Set the fail height
&e/parkour leaderboard <name> &7- Show the top three"
titles:
  start: { title: "&aGO!", subtitle: "&f{course}" }
  checkpoint: { title: "&bCheckpoint {number}", subtitle: "&7Keep going!" }
  finish: { title: "&6Course complete!", subtitle: "&f{time}" }
  record: { title: "&eNew personal best!", subtitle: "&f{time}" }
  invalid: { title: "&cRun invalidated", subtitle: "&7{reason}" }
sounds:
  command-success: { enabled: true, type: "ENTITY_EXPERIENCE_ORB_PICKUP", volume: 0.8, pitch: 1.2 }
  command-error: { enabled: true, type: "ENTITY_VILLAGER_NO", volume: 0.8, pitch: 0.9 }
  start: { enabled: true, type: "BLOCK_NOTE_BLOCK_PLING", volume: 0.8, pitch: 1.5 }
  checkpoint: { enabled: true, type: "ENTITY_EXPERIENCE_ORB_PICKUP", volume: 0.8, pitch: 1.2 }
  recovery: { enabled: true, type: "BLOCK_AMETHYST_BLOCK_CHIME", volume: 0.6, pitch: 0.7 }
  finish: { enabled: true, type: "UI_TOAST_CHALLENGE_COMPLETE", volume: 0.9, pitch: 1.0 }
  record: { enabled: true, type: "ENTITY_PLAYER_LEVELUP", volume: 0.8, pitch: 1.2 }
  invalid: { enabled: true, type: "ENTITY_VILLAGER_NO", volume: 0.8, pitch: 0.8 }
particles:
  type: "END_ROD"
```

## `src/main/resources/plugin.yml`

**Purpose and implementation note:** Registers MATARIF Parkour, its command alias, and the admin permission.

```yaml
# Plugin descriptor: declares the main class, command alias, API baseline, and admin permission.
name: MATARIFParkour
version: 1.0.0
main: com.matarif.parkour.MatarifParkour
api-version: '1.21'
author: MATARIF
description: A checkpoint-based parkour timer and leaderboard plugin.
commands:
  parkour:
    description: Set up courses and play parkour.
    aliases: [pk]
    usage: /parkour <create|setstart|setfinish|addcheckpoint|setfaily|leaderboard>
permissions:
  matarifparkour.admin:
    description: Allows players to create and configure parkour courses.
    default: op
```
