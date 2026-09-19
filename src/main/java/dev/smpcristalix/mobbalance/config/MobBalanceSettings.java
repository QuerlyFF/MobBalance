package dev.smpcristalix.mobbalance.config;

import org.bukkit.Difficulty;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Неизменяемый снимок настроек MobBalance.
 * После /mobbalance reload создаётся новый экземпляр и атомарно подменяется в сервисе.
 */
public final class MobBalanceSettings {

    private final boolean enabled;
    private final Difficulty difficulty;
    private final Set<String> spawnReasons;
    private final PercentRange healthBonus;
    private final PercentRange damageBonus;
    private final double armorChance;
    private final double zombieWeaponChance;
    private final double drownedWeaponChance;
    private final double drownedTridentShare;
    private final double generatedItemDropChance;
    private final double armorEnchantChance;
    private final double weaponEnchantChance;
    private final int[] armorTierWeights;
    private final int[] armorPieceCountWeights;
    private final double skeletonNegativeEffectChance;
    private final double strayNegativeEffectChance;
    private final List<PotionEffectSpec> projectileEffects;
    private final SpiderSpec spider;
    private final SpiderSpec caveSpider;

    private MobBalanceSettings(FileConfiguration config) {
        enabled = config.getBoolean("general.enabled", true);
        difficulty = parseDifficulty(config.getString("general.difficulty", "HARD"));
        spawnReasons = Collections.unmodifiableSet(new HashSet<>(config.getStringList("general.spawn-reasons")));

        healthBonus = new PercentRange(
                config.getDouble("stats.health-bonus-min-percent", 0.0),
                config.getDouble("stats.health-bonus-max-percent", 100.0)
        );
        damageBonus = new PercentRange(
                config.getDouble("stats.damage-bonus-min-percent", 0.0),
                config.getDouble("stats.damage-bonus-max-percent", 100.0)
        );

        double equipmentMultiplier = positive(config.getDouble("equipment.multiplier", 8.0));
        armorChance = multipliedChance(
                config.getDouble("equipment.armor-base-chance", 0.15),
                equipmentMultiplier,
                config.getDouble("equipment.armor-cap", 0.90)
        );
        zombieWeaponChance = multipliedChance(
                config.getDouble("equipment.zombie-weapon-base-chance", 0.05),
                equipmentMultiplier,
                config.getDouble("equipment.zombie-weapon-cap", 0.40)
        );
        drownedWeaponChance = probability(config.getDouble("equipment.drowned-weapon-chance", 0.40));
        drownedTridentShare = probability(config.getDouble("equipment.drowned-trident-share", 0.50));
        generatedItemDropChance = probability(config.getDouble("equipment.generated-item-drop-chance", 0.02));

        armorTierWeights = new int[]{
                weight(config, "equipment.armor-tier-weights.leather", 20),
                weight(config, "equipment.armor-tier-weights.gold", 25),
                weight(config, "equipment.armor-tier-weights.chainmail", 25),
                weight(config, "equipment.armor-tier-weights.iron", 25),
                weight(config, "equipment.armor-tier-weights.diamond", 5)
        };
        armorPieceCountWeights = new int[]{
                weight(config, "equipment.armor-piece-count-weights.one", 20),
                weight(config, "equipment.armor-piece-count-weights.two", 30),
                weight(config, "equipment.armor-piece-count-weights.three", 30),
                weight(config, "equipment.armor-piece-count-weights.four", 20)
        };

        double enchantmentMultiplier = positive(config.getDouble("enchantments.multiplier", 3.5));
        armorEnchantChance = multipliedChance(
                config.getDouble("enchantments.armor-base-chance", 0.50),
                enchantmentMultiplier,
                config.getDouble("enchantments.armor-cap", 0.75)
        );
        weaponEnchantChance = multipliedChance(
                config.getDouble("enchantments.weapon-base-chance", 0.25),
                enchantmentMultiplier,
                config.getDouble("enchantments.weapon-cap", 0.70)
        );

        skeletonNegativeEffectChance = probability(
                config.getDouble("projectiles.skeleton.negative-effect-chance", 0.35)
        );
        strayNegativeEffectChance = probability(
                config.getDouble("projectiles.stray.negative-effect-chance", 0.35)
        );
        projectileEffects = Collections.unmodifiableList(readProjectileEffects(config));

        spider = readSpiderSpec(config, "spiders.spider", 1.20, 100, 0);
        caveSpider = readSpiderSpec(config, "spiders.cave-spider", 1.25, 300, 1);
    }

    public static MobBalanceSettings from(FileConfiguration config) {
        return new MobBalanceSettings(config);
    }

    public boolean enabled() {
        return enabled;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public boolean acceptsSpawnReason(String spawnReason) {
        return spawnReasons.isEmpty() || spawnReasons.contains(spawnReason);
    }

    public double randomHealthMultiplier(RandomGenerator random) {
        return healthBonus.randomMultiplier(random);
    }

    public double randomDamageMultiplier(RandomGenerator random) {
        return damageBonus.randomMultiplier(random);
    }

    public double armorChance() {
        return armorChance;
    }

    public double zombieWeaponChance() {
        return zombieWeaponChance;
    }

    public double drownedWeaponChance() {
        return drownedWeaponChance;
    }

    public double drownedTridentShare() {
        return drownedTridentShare;
    }

    public float generatedItemDropChance() {
        return (float) generatedItemDropChance;
    }

    public double armorEnchantChance() {
        return armorEnchantChance;
    }

    public double weaponEnchantChance() {
        return weaponEnchantChance;
    }

    /** Порядок: leather, gold, chainmail, iron, diamond. */
    public int[] armorTierWeights() {
        return armorTierWeights.clone();
    }

    /** Порядок: 1, 2, 3, 4 предмета брони. */
    public int[] armorPieceCountWeights() {
        return armorPieceCountWeights.clone();
    }

    public double negativeEffectChance(EntityType shooterType) {
        return shooterType == EntityType.STRAY ? strayNegativeEffectChance : skeletonNegativeEffectChance;
    }

    public List<PotionEffectSpec> projectileEffects() {
        return projectileEffects;
    }

    public SpiderSpec spiderSpec(EntityType type) {
        return type == EntityType.CAVE_SPIDER ? caveSpider : spider;
    }

    private static SpiderSpec readSpiderSpec(
            FileConfiguration config,
            String root,
            double defaultSpeedMultiplier,
            int defaultPoisonDurationTicks,
            int defaultPoisonAmplifier
    ) {
        return new SpiderSpec(
                Math.max(0.0, config.getDouble(root + ".speed-multiplier", defaultSpeedMultiplier)),
                Math.max(1, config.getInt(root + ".poison-duration-ticks", defaultPoisonDurationTicks)),
                Math.max(0, config.getInt(root + ".poison-amplifier", defaultPoisonAmplifier))
        );
    }

    private static List<PotionEffectSpec> readProjectileEffects(FileConfiguration config) {
        List<PotionEffectSpec> result = new ArrayList<>();
        addEffect(result, config, "slowness", PotionEffectType.SLOWNESS, 35, 120, 0);
        addEffect(result, config, "weakness", PotionEffectType.WEAKNESS, 30, 160, 0);
        addEffect(result, config, "poison", PotionEffectType.POISON, 25, 100, 0);
        addEffect(result, config, "wither", PotionEffectType.WITHER, 10, 60, 0);
        return result;
    }

    private static void addEffect(
            List<PotionEffectSpec> target,
            FileConfiguration config,
            String key,
            PotionEffectType type,
            int defaultWeight,
            int defaultDuration,
            int defaultAmplifier
    ) {
        String root = "projectiles.effects." + key;
        int weight = weight(config, root + ".weight", defaultWeight);
        int duration = Math.max(1, config.getInt(root + ".duration-ticks", defaultDuration));
        int amplifier = Math.max(0, config.getInt(root + ".amplifier", defaultAmplifier));
        if (weight > 0) {
            target.add(new PotionEffectSpec(type, weight, duration, amplifier));
        }
    }

    private static Difficulty parseDifficulty(String raw) {
        try {
            return Difficulty.valueOf(raw == null ? "HARD" : raw.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return Difficulty.HARD;
        }
    }

    private static double multipliedChance(double base, double multiplier, double cap) {
        return Math.min(probability(cap), probability(base) * positive(multiplier));
    }

    private static double probability(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double positive(double value) {
        return Math.max(0.0, value);
    }

    private static int weight(FileConfiguration config, String path, int fallback) {
        return Math.max(0, config.getInt(path, fallback));
    }

    public record PotionEffectSpec(PotionEffectType type, int weight, int durationTicks, int amplifier) {
    }

    public record SpiderSpec(double speedMultiplier, int poisonDurationTicks, int poisonAmplifier) {
    }

    private record PercentRange(double minPercent, double maxPercent) {
        private PercentRange {
            minPercent = Math.max(0.0, minPercent);
            maxPercent = Math.max(minPercent, maxPercent);
        }

        private double randomMultiplier(RandomGenerator random) {
            double percent = random.nextDouble(minPercent, Math.nextUp(maxPercent));
            return 1.0 + (percent / 100.0);
        }
    }
}
