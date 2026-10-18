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
