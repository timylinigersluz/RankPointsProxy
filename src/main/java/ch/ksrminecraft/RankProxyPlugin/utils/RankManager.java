package ch.ksrminecraft.RankProxyPlugin.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.WeightNode;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * RankManager – lädt und synchronisiert Ränge aus ranks.yaml mit LuckPerms.
 * Nutzt LogHelper für einheitliches Logging (inkl. log.level aus resources.yaml).
 */
public class RankManager {

    private final Path ranksFile;
    private final LuckPerms luckPerms;
    private final List<Rank> rankList = new ArrayList<>();
    private final LogHelper log;

    private String lastHash = "";

    public RankManager(Path dataFolder, LogHelper log, LuckPerms luckPerms) {
        this.ranksFile = dataFolder.resolve("ranks.yaml");
        this.luckPerms = luckPerms;
        this.log = log;
        initialize();
    }

    // -------------------------------
    // Datenklassen
    // -------------------------------
    public static class Enchantment {
        public String id;
        public int level;
    }

    public static class RewardItem {
        public String item;
        public int amount;
        public List<Enchantment> enchantments = new ArrayList<>();
    }

    public static class Rank {
        public String name;
        public int points;
        public List<RewardItem> rewards = new ArrayList<>();
    }

    public static class RankProgressInfo {
        public Rank currentRank;
        public Rank nextRank;
        public int pointsUntilNext;
    }

    // -------------------------------
    // Initialisierung
    // -------------------------------
    private void initialize() {
        try {
            String newHash = computeYamlHash();
            if (!newHash.equals(lastHash)) {
                loadRanks();
                syncRanksWithLuckPerms(); // create-only
                lastHash = newHash;
            } else {
                log.debug("Ranks are up to date – no changes detected.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize RankManager: {}", e.getMessage());
        }
    }

    private String computeYamlHash() throws IOException, NoSuchAlgorithmException {
        String content = Files.readString(ranksFile);
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    public List<Rank> getRankList() {
        return Collections.unmodifiableList(rankList);
    }

    // -------------------------------
    // Laden der Ränge aus ranks.yaml
    // -------------------------------
    private void loadRanks() throws IOException {
        rankList.clear();
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(ranksFile)
                .build();

        CommentedConfigurationNode root = loader.load();
        ConfigurationNode ranksNode = root.node("ranks");

        if (ranksNode == null || ranksNode.virtual()) {
            log.warn("No 'ranks' section found in {} – skipping rank load.", ranksFile.toAbsolutePath());
            return;
        }

        for (ConfigurationNode rankNode : ranksNode.childrenList()) {
            Rank rank = new Rank();
            rank.name = rankNode.node("name").getString("undefined");
            rank.points = rankNode.node("points").getInt(0);

            for (ConfigurationNode rewardNode : rankNode.node("reward").childrenList()) {
                RewardItem reward = new RewardItem();
                reward.item = rewardNode.node("item").getString("minecraft:stone");
                reward.amount = rewardNode.node("amount").getInt(1);

                for (ConfigurationNode enchant : rewardNode.node("enchantments").childrenList()) {
                    Enchantment e = new Enchantment();
                    e.id = enchant.node("id").getString();
                    e.level = enchant.node("level").getInt();
                    reward.enchantments.add(e);
                }

                rank.rewards.add(reward);
            }

            rankList.add(rank);
        }

        rankList.sort(Comparator.comparingInt(r -> r.points));
        log.info("Loaded {} ranks from {}", rankList.size(), ranksFile.toAbsolutePath());
    }

    // -------------------------------
    // Sync mit LuckPerms (create-only)
    // -------------------------------
    public void syncRanksWithLuckPerms() {
        if (luckPerms == null) {
            log.warn("LuckPerms not available. Skipping rank sync.");
            return;
        }

        String hash;
        try {
            hash = computeYamlHash();
        } catch (Exception e) {
            log.error("Could not compute ranks.yaml hash. Fehler: {}", e.getMessage());
            hash = "invalid";
        }

        GroupManager groupManager = luckPerms.getGroupManager();
        int createdCount = 0;

        for (int i = 0; i < rankList.size(); i++) {
            Rank rank = rankList.get(i);
            String groupName = rank.name;
            int weight = i + 1;
            String prefix = "&7[" + groupName.toUpperCase().replace("_", " ") + "] ";

            Group group = groupManager.getGroup(groupName);

            if (group == null) {
                // Gruppe existiert NICHT -> erstellen
                group = groupManager.createAndLoadGroup(groupName).join();
                createdCount++;
                log.info("Created new group in LuckPerms: {}", groupName);

                // Prefix
                group.data().add(PrefixNode.builder(prefix, 100).build());

                // Gewicht (falls verfügbar)
                try {
                    group.data().add(WeightNode.builder(weight).build());
                } catch (NoClassDefFoundError | NoSuchMethodError t) {
                    log.warn("LuckPerms WeightNode not available; skipping weight for '{}'.", groupName);
                }

                // ranks.yaml Hash als Meta
                group.data().add(MetaNode.builder("ranks_yaml_hash", hash).build());

                // Änderungen persistieren
                groupManager.saveGroup(group);
                log.debug("Initialized LuckPerms group: {} (weight={}, prefix='{}')", groupName, weight, prefix);
            } else {
                log.debug("Group '{}' already exists in LuckPerms. Skipping modifications.", groupName);
            }
        }

        log.info("Rank group sync finished: {} group(s) created, {} already existed.",
                createdCount, rankList.size() - createdCount);
    }

    // -------------------------------
    // Rank-Berechnung
    // -------------------------------
    public Optional<Rank> getRankForPoints(int points) {
        Rank result = null;
        for (Rank rank : rankList) {
            if (points >= rank.points) {
                result = rank;
            } else {
                break;
            }
        }
        return Optional.ofNullable(result);
    }

    public Optional<RankProgressInfo> getRankProgress(int points) {
        if (rankList.isEmpty()) return Optional.empty();

        Rank current = null;
        Rank next = null;

        for (Rank rank : rankList) {
            if (points >= rank.points) {
                current = rank;
            } else {
                next = rank;
                break;
            }
        }

        RankProgressInfo info = new RankProgressInfo();
        info.currentRank = current;
        info.nextRank = next;
        info.pointsUntilNext = (next != null) ? (next.points - points) : 0;

        return Optional.of(info);
    }
}
