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
