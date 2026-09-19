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
    private final double boggedNegativeEffectChance;
    private final double raiderNegativeEffectChance;
    private final int boggedPoisonDurationTicks;
    private final int boggedPoisonAmplifier;
    private final List<PotionEffectSpec> projectileEffects;
    private final SpiderSpec spider;
    private final SpiderSpec caveSpider;
    private final CreeperSpec creeper;
    private final double witchExtraPotionChance;
    private final int witchExtraPotionDelayTicks;
    private final double endermanSpeedMultiplier;
    private final double phantomSpeedMultiplier;
    private final int pillagerQuickChargeLevel;
    private final double vindicatorSpeedMultiplier;
    private final EvokerSpec evoker;
    private final RangedRateSpec breeze;
    private final RangedRateSpec blaze;
    private final GhastSpec ghast;

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

        skeletonNegativeEffectChance = probability(config.getDouble("projectiles.skeleton.negative-effect-chance", 0.35));
        strayNegativeEffectChance = probability(config.getDouble("projectiles.stray.negative-effect-chance", 0.35));
        boggedNegativeEffectChance = probability(config.getDouble("projectiles.bogged.negative-effect-chance", 0.35));
        raiderNegativeEffectChance = probability(config.getDouble("projectiles.raiders.negative-effect-chance", 0.35));
        boggedPoisonDurationTicks = Math.max(1, config.getInt("projectiles.bogged.base-poison-duration-ticks", 160));
        boggedPoisonAmplifier = Math.max(0, config.getInt("projectiles.bogged.base-poison-amplifier", 1));
        projectileEffects = Collections.unmodifiableList(readProjectileEffects(config));

        spider = readSpiderSpec(config, "spiders.spider", 1.20, 100, 0);
        caveSpider = readSpiderSpec(config, "spiders.cave-spider", 1.25, 300, 1);
        creeper = readCreeperSpec(config);

        witchExtraPotionChance = probability(config.getDouble("witch.extra-potion-chance", 0.65));
        witchExtraPotionDelayTicks = Math.max(1, config.getInt("witch.extra-potion-delay-ticks", 8));
        endermanSpeedMultiplier = positiveOrOne(config.getDouble("enderman.speed-multiplier", 1.55));
        phantomSpeedMultiplier = positiveOrOne(config.getDouble("phantom.speed-multiplier", 1.35));

        pillagerQuickChargeLevel = Math.max(1, config.getInt("pillager.quick-charge-level", 3));
        vindicatorSpeedMultiplier = positiveOrOne(config.getDouble("vindicator.speed-multiplier", 1.25));
        evoker = new EvokerSpec(
                probability(config.getDouble("evoker.totem-drop-chance", 0.50)),
                probability(config.getDouble("evoker.repeat-ability-chance", 0.60)),
                Math.max(1, config.getInt("evoker.repeat-delay-ticks", 20)),
                positiveOrOne(config.getDouble("evoker.fang-damage-multiplier", 1.50)),
                Math.max(0, config.getInt("evoker.extra-vex-count", 1))
        );
        breeze = readRateSpec(config, "breeze", 0.50, 6);
        blaze = readRateSpec(config, "blaze", 0.50, 6);
        ghast = new GhastSpec(
                probability(config.getDouble("ghast.extra-shot-chance", 0.40)),
                Math.max(1, config.getInt("ghast.extra-shot-delay-ticks", 10)),
                positiveOrOne(config.getDouble("ghast.damage-multiplier", 1.35))
        );
    }

    public static MobBalanceSettings from(FileConfiguration config) { return new MobBalanceSettings(config); }

    public boolean enabled() { return enabled; }
    public Difficulty difficulty() { return difficulty; }
    public boolean acceptsSpawnReason(String spawnReason) { return spawnReasons.isEmpty() || spawnReasons.contains(spawnReason); }
    public double randomHealthMultiplier(RandomGenerator random) { return healthBonus.randomMultiplier(random); }
    public double randomDamageMultiplier(RandomGenerator random) { return damageBonus.randomMultiplier(random); }
    public double armorChance() { return armorChance; }
    public double zombieWeaponChance() { return zombieWeaponChance; }
    public double drownedWeaponChance() { return drownedWeaponChance; }
    public double drownedTridentShare() { return drownedTridentShare; }
    public float generatedItemDropChance() { return (float) generatedItemDropChance; }
    public double armorEnchantChance() { return armorEnchantChance; }
    public double weaponEnchantChance() { return weaponEnchantChance; }
    public int[] armorTierWeights() { return armorTierWeights.clone(); }
    public int[] armorPieceCountWeights() { return armorPieceCountWeights.clone(); }

    public double negativeEffectChance(EntityType shooterType) {
        return switch (shooterType) {
            case STRAY -> strayNegativeEffectChance;
            case BOGGED -> boggedNegativeEffectChance;
            case PILLAGER, PIGLIN -> raiderNegativeEffectChance;
            default -> skeletonNegativeEffectChance;
        };
    }

    public int boggedPoisonDurationTicks() { return boggedPoisonDurationTicks; }
    public int boggedPoisonAmplifier() { return boggedPoisonAmplifier; }
    public List<PotionEffectSpec> projectileEffects() { return projectileEffects; }
    public SpiderSpec spiderSpec(EntityType type) { return type == EntityType.CAVE_SPIDER ? caveSpider : spider; }
    public CreeperSpec creeperSpec() { return creeper; }
    public double witchExtraPotionChance() { return witchExtraPotionChance; }
    public int witchExtraPotionDelayTicks() { return witchExtraPotionDelayTicks; }
    public double endermanSpeedMultiplier() { return endermanSpeedMultiplier; }
    public double phantomSpeedMultiplier() { return phantomSpeedMultiplier; }
    public int pillagerQuickChargeLevel() { return pillagerQuickChargeLevel; }
    public double vindicatorSpeedMultiplier() { return vindicatorSpeedMultiplier; }
    public EvokerSpec evokerSpec() { return evoker; }
    public RangedRateSpec breezeSpec() { return breeze; }
    public RangedRateSpec blazeSpec() { return blaze; }
    public GhastSpec ghastSpec() { return ghast; }

    private static SpiderSpec readSpiderSpec(FileConfiguration config, String root, double speed, int duration, int amplifier) {
        return new SpiderSpec(
                Math.max(0.0, config.getDouble(root + ".speed-multiplier", speed)),
                Math.max(1, config.getInt(root + ".poison-duration-ticks", duration)),
                Math.max(0, config.getInt(root + ".poison-amplifier", amplifier))
        );
    }

    private static CreeperSpec readCreeperSpec(FileConfiguration config) {
        return new CreeperSpec(
                positiveOrOne(config.getDouble("creeper.aggro-speed-multiplier", 1.15)),
                positiveOrOne(config.getDouble("creeper.explosion-radius-multiplier", 1.25)),
                positiveOrOne(config.getDouble("creeper.fuse-time-multiplier", 0.80))
        );
    }

    private static RangedRateSpec readRateSpec(FileConfiguration config, String root, double chance, int delay) {
        return new RangedRateSpec(
                probability(config.getDouble(root + ".extra-shot-chance", chance)),
                Math.max(1, config.getInt(root + ".extra-shot-delay-ticks", delay))
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

    private static void addEffect(List<PotionEffectSpec> target, FileConfiguration config, String key,
                                  PotionEffectType type, int defaultWeight, int defaultDuration, int defaultAmplifier) {
        String root = "projectiles.effects." + key;
        int weight = weight(config, root + ".weight", defaultWeight);
        int duration = Math.max(1, config.getInt(root + ".duration-ticks", defaultDuration));
        int amplifier = Math.max(0, config.getInt(root + ".amplifier", defaultAmplifier));
        if (weight > 0) target.add(new PotionEffectSpec(type, weight, duration, amplifier));
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

    private static double probability(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    private static double positive(double value) { return Math.max(0.0, value); }
    private static double positiveOrOne(double value) { return value > 0.0 ? value : 1.0; }
    private static int weight(FileConfiguration config, String path, int fallback) { return Math.max(0, config.getInt(path, fallback)); }

    public record PotionEffectSpec(PotionEffectType type, int weight, int durationTicks, int amplifier) {}
    public record SpiderSpec(double speedMultiplier, int poisonDurationTicks, int poisonAmplifier) {}
    public record CreeperSpec(double aggroSpeedMultiplier, double explosionRadiusMultiplier, double fuseTimeMultiplier) {}
    public record EvokerSpec(double totemDropChance, double repeatAbilityChance, int repeatDelayTicks,
                             double fangDamageMultiplier, int extraVexCount) {}
    public record RangedRateSpec(double extraShotChance, int extraShotDelayTicks) {}
    public record GhastSpec(double extraShotChance, int extraShotDelayTicks, double damageMultiplier) {}

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
