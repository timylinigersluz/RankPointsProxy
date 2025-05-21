package ch.ksrminecraft.RankProxyPlugin.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PrefixNode;
import org.slf4j.Logger;
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

public class RankManager {

    private final Path ranksFile;
    private final Logger logger;
    private final LuckPerms luckPerms;
    private final List<Rank> rankList = new ArrayList<>();
    private String lastHash = "";

    public RankManager(Path dataFolder, Logger logger, LuckPerms luckPerms) {
        this.ranksFile = dataFolder.resolve("ranks.yaml");
        this.logger = logger;
        this.luckPerms = luckPerms;
        initialize();
    }

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

    private void initialize() {
        try {
            String newHash = computeYamlHash();
            if (!newHash.equals(lastHash)) {
                loadRanks();
                syncRanksWithLuckPerms();
                lastHash = newHash;
            } else {
                logger.info("Ranks are up to date – no changes detected.");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize RankManager", e);
        }
    }

    private String computeYamlHash() throws IOException, NoSuchAlgorithmException {
        String content = Files.readString(ranksFile);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    public List<Rank> getRankList() {
        return Collections.unmodifiableList(rankList);
    }

    private void loadRanks() throws IOException {
        rankList.clear();
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(ranksFile)
                .build();

        CommentedConfigurationNode root = loader.load();
        ConfigurationNode ranksNode = root.node("ranks");

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
    }

    /**
     * Synchronisiert die Rangliste aus der YAML-Datei mit LuckPerms
     */
    public void syncRanksWithLuckPerms() {
        if (luckPerms == null) {
            logger.warn("LuckPerms not available. Skipping rank sync.");
            return;
        }

        String hash;
        try {
            hash = computeYamlHash();
        } catch (Exception e) {
            logger.warn("Could not compute ranks.yaml hash.", e);
            hash = "invalid";
        }

        GroupManager groupManager = luckPerms.getGroupManager();

        for (int i = 0; i < rankList.size(); i++) {
            Rank rank = rankList.get(i);
            String groupName = rank.name;
            int weight = i + 1;
            String prefix = "&7[" + groupName.toUpperCase().replace("_", " ") + "] ";

            Group group = groupManager.getGroup(groupName);
            if (group == null) {
                group = groupManager.createAndLoadGroup(groupName).join();
                logger.info("Created new group in LuckPerms: {}", groupName);
            }

            group.data().clear(n -> n instanceof PrefixNode);
            group.data().add(PrefixNode.builder(prefix, 100).build());

            group.data().add(Node.builder("weight." + weight).build());
            group.data().add(Node.builder("meta.ranks_yaml_hash." + hash).build());

            groupManager.saveGroup(group);
            logger.info("Updated LuckPerms group: {}", groupName);
        }

        logger.info("All ranks synced with LuckPerms.");
    }

    /**
     * Gibt den höchsten passenden Rang für den gegebenen Punktestand zurück.
     */
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

    /**
     * Liefert eine Info über den aktuellen und nächsten Rang inklusive verbleibender Punkte.
     *
     * @param points Der aktuelle Punktestand des Spielers
     * @return Optional mit RankProgressInfo oder leer, falls keine Ränge vorhanden
     */
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

    public static class RankProgressInfo {
        public Rank currentRank;
        public Rank nextRank;
        public int pointsUntilNext;
    }

}
