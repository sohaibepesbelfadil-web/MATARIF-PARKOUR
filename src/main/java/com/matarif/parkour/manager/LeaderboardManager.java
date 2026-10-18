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
