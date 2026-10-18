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
