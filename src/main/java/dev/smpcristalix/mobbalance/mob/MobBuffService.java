package dev.smpcristalix.mobbalance.mob;

import dev.smpcristalix.mobbalance.config.MobBalanceSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Witch;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Центральный сервис усиления мобов.
 * Характеристики роллятся один раз при спавне и сохраняются в PDC сущности.
 */
public final class MobBuffService {

    private static final Set<EntityType> SUPPORTED_TYPES = Set.of(
            EntityType.ZOMBIE,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.SKELETON,
            EntityType.STRAY,
            EntityType.BOGGED,
            EntityType.SPIDER,
            EntityType.CAVE_SPIDER,
            EntityType.CREEPER,
            EntityType.SLIME,
            EntityType.WITCH,
            EntityType.ENDERMAN,
            EntityType.PHANTOM
    );

    private static final Set<EntityType> EQUIPMENT_TYPES = Set.of(
            EntityType.ZOMBIE,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.SKELETON,
            EntityType.STRAY,
            EntityType.BOGGED
    );

    private final Plugin plugin;
    private final NamespacedKey buffedKey;
    private final NamespacedKey damageMultiplierKey;
    private final NamespacedKey creeperBaseSpeedKey;
    private final NamespacedKey creeperSpeedBoostedKey;
    private final EquipmentBuffService equipmentBuffService = new EquipmentBuffService();
    private final Set<UUID> witchExtraPotionGuard = new HashSet<>();
    private volatile MobBalanceSettings settings;

    public MobBuffService(Plugin plugin, MobBalanceSettings settings) {
        this.plugin = plugin;
        this.buffedKey = new NamespacedKey(plugin, "buffed");
        this.damageMultiplierKey = new NamespacedKey(plugin, "damage_multiplier");
        this.creeperBaseSpeedKey = new NamespacedKey(plugin, "creeper_base_speed");
        this.creeperSpeedBoostedKey = new NamespacedKey(plugin, "creeper_speed_boosted");
        this.settings = settings;
    }

    public void reload(MobBalanceSettings newSettings) {
        this.settings = newSettings;
    }

    public MobBalanceSettings settings() {
        return settings;
    }

    public boolean tryBuff(LivingEntity entity, String spawnReason) {
        MobBalanceSettings current = settings;
        if (!current.enabled()) return false;
        if (entity.getWorld().getDifficulty() != current.difficulty()) return false;
        if (!SUPPORTED_TYPES.contains(entity.getType())) return false;
        if (!current.acceptsSpawnReason(spawnReason)) return false;

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(buffedKey, PersistentDataType.BYTE)) return false;

        RandomGenerator random = ThreadLocalRandom.current();
        EntityType type = entity.getType();

        boolean buffHealth = type != EntityType.PHANTOM;
        boolean buffDamage = type != EntityType.WITCH;
        applyStats(entity, current, random, buffHealth, buffDamage);

        if (isSpider(type)) {
            applySpiderSpeed(entity, current);
        } else if (type == EntityType.ENDERMAN) {
            multiplyAttribute(entity, Attribute.GENERIC_MOVEMENT_SPEED, current.endermanSpeedMultiplier());
        } else if (type == EntityType.PHANTOM) {
            applyPhantomSpeed(entity, current.phantomSpeedMultiplier());
        }

        if (entity instanceof Creeper creeper) {
            applyCreeperBaseBuffs(creeper, current);
        }
        if (EQUIPMENT_TYPES.contains(type)) {
            equipmentBuffService.apply(entity, current, random);
        }

        pdc.set(buffedKey, PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    public void buffProjectile(AbstractArrow projectile) {
        ProjectileSource source = projectile.getShooter();
        if (!(source instanceof LivingEntity shooter)) return;
        if (!isSkeletonFamily(shooter.getType())) return;

        PersistentDataContainer pdc = shooter.getPersistentDataContainer();
        if (!pdc.has(buffedKey, PersistentDataType.BYTE)) return;

        Double damageMultiplier = pdc.get(damageMultiplierKey, PersistentDataType.DOUBLE);
        if (damageMultiplier != null) {
            projectile.setDamage(projectile.getDamage() * damageMultiplier);
        }

        if (projectile instanceof Arrow arrow) {
            if (shooter.getType() == EntityType.BOGGED) {
                arrow.addCustomEffect(new PotionEffect(
                        PotionEffectType.POISON,
                        settings.boggedPoisonDurationTicks(),
                        settings.boggedPoisonAmplifier(),
                        false,
                        true,
                        true
                ), true);
            }
            applyNegativeEffect(arrow, shooter.getType(), settings, ThreadLocalRandom.current());
        }
    }

    /**
     * Ведьма иногда делает второй бросок того же зелья через короткую задержку.
     * Guard не даёт дополнительному броску породить бесконечную цепочку.
     */
    public void buffWitchPotion(ThrownPotion potion) {
        if (!(potion.getShooter() instanceof Witch witch)) return;
        if (!witch.getPersistentDataContainer().has(buffedKey, PersistentDataType.BYTE)) return;
        if (witchExtraPotionGuard.contains(witch.getUniqueId())) return;

        MobBalanceSettings current = settings;
        if (ThreadLocalRandom.current().nextDouble() >= current.witchExtraPotionChance()) return;

        ItemStack potionItem = potion.getItem().clone();
        Vector velocity = potion.getVelocity().clone();
        UUID witchId = witch.getUniqueId();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!witch.isValid() || witch.isDead()) return;

            witchExtraPotionGuard.add(witchId);
            try {
                ThrownPotion extra = witch.launchProjectile(ThrownPotion.class, velocity);
                extra.setItem(potionItem);
            } finally {
                witchExtraPotionGuard.remove(witchId);
            }
        }, current.witchExtraPotionDelayTicks());
    }

    /** Обычный паук всегда накладывает Poison I, пещерный — Poison II. */
    public void applySpiderPoison(LivingEntity attacker, LivingEntity victim) {
        if (!isSpider(attacker.getType())) return;
        if (!attacker.getPersistentDataContainer().has(buffedKey, PersistentDataType.BYTE)) return;

        MobBalanceSettings.SpiderSpec spec = settings.spiderSpec(attacker.getType());
        victim.addPotionEffect(new PotionEffect(
                PotionEffectType.POISON,
                spec.poisonDurationTicks(),
                spec.poisonAmplifier(),
                false,
                true,
                true
        ));
    }

    /** Creeper ускоряется только пока у него есть цель. */
    public void setCreeperAggro(Creeper creeper, boolean aggressive) {
        PersistentDataContainer pdc = creeper.getPersistentDataContainer();
        if (!pdc.has(buffedKey, PersistentDataType.BYTE)) return;

        AttributeInstance movementSpeed = creeper.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (movementSpeed == null) return;

        boolean boosted = pdc.has(creeperSpeedBoostedKey, PersistentDataType.BYTE);
        if (aggressive) {
            if (boosted) return;
            double baseSpeed = movementSpeed.getBaseValue();
            pdc.set(creeperBaseSpeedKey, PersistentDataType.DOUBLE, baseSpeed);
            movementSpeed.setBaseValue(baseSpeed * settings.creeperSpec().aggroSpeedMultiplier());
            pdc.set(creeperSpeedBoostedKey, PersistentDataType.BYTE, (byte) 1);
            return;
        }

        if (!boosted) return;
        Double baseSpeed = pdc.get(creeperBaseSpeedKey, PersistentDataType.DOUBLE);
        if (baseSpeed != null) movementSpeed.setBaseValue(baseSpeed);
        pdc.remove(creeperBaseSpeedKey);
        pdc.remove(creeperSpeedBoostedKey);
    }

    private void applyStats(LivingEntity entity, MobBalanceSettings settings, RandomGenerator random,
                            boolean buffHealth, boolean buffDamage) {
        if (buffHealth) {
            double healthMultiplier = settings.randomHealthMultiplier(random);
            AttributeInstance maxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(maxHealth.getBaseValue() * healthMultiplier);
                entity.setHealth(maxHealth.getValue());
            }
        }

        double damageMultiplier = 1.0;
        if (buffDamage) {
            damageMultiplier = settings.randomDamageMultiplier(random);
            AttributeInstance attackDamage = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (attackDamage != null) {
                attackDamage.setBaseValue(attackDamage.getBaseValue() * damageMultiplier);
            }
        }

        entity.getPersistentDataContainer().set(damageMultiplierKey, PersistentDataType.DOUBLE, damageMultiplier);
    }

    private void applySpiderSpeed(LivingEntity entity, MobBalanceSettings settings) {
        multiplyAttribute(entity, Attribute.GENERIC_MOVEMENT_SPEED, settings.spiderSpec(entity.getType()).speedMultiplier());
    }

    private void applyPhantomSpeed(LivingEntity entity, double multiplier) {
        AttributeInstance flyingSpeed = entity.getAttribute(Attribute.GENERIC_FLYING_SPEED);
        if (flyingSpeed != null) {
            flyingSpeed.setBaseValue(flyingSpeed.getBaseValue() * multiplier);
            return;
        }
        multiplyAttribute(entity, Attribute.GENERIC_MOVEMENT_SPEED, multiplier);
    }

    private void multiplyAttribute(LivingEntity entity, Attribute attribute, double multiplier) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(instance.getBaseValue() * multiplier);
    }

    private void applyCreeperBaseBuffs(Creeper creeper, MobBalanceSettings settings) {
        MobBalanceSettings.CreeperSpec spec = settings.creeperSpec();
        int radius = Math.max(1, (int) Math.round(creeper.getExplosionRadius() * spec.explosionRadiusMultiplier()));
        creeper.setExplosionRadius(radius);
        int fuse = Math.max(1, (int) Math.round(creeper.getMaxFuseTicks() * spec.fuseTimeMultiplier()));
        creeper.setMaxFuseTicks(fuse);
    }

    private boolean isSpider(EntityType type) {
        return type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER;
    }

    private boolean isSkeletonFamily(EntityType type) {
        return type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED;
    }

    private void applyNegativeEffect(Arrow arrow, EntityType shooterType, MobBalanceSettings settings, RandomGenerator random) {
        if (random.nextDouble() >= settings.negativeEffectChance(shooterType)) return;
        MobBalanceSettings.PotionEffectSpec spec = chooseEffect(settings.projectileEffects(), random);
        if (spec == null) return;

        arrow.addCustomEffect(new PotionEffect(
                spec.type(), spec.durationTicks(), spec.amplifier(), false, true, true
        ), false);
    }

    private MobBalanceSettings.PotionEffectSpec chooseEffect(List<MobBalanceSettings.PotionEffectSpec> effects,
                                                               RandomGenerator random) {
        int totalWeight = effects.stream().mapToInt(MobBalanceSettings.PotionEffectSpec::weight).sum();
        if (totalWeight <= 0) return null;

        int roll = random.nextInt(totalWeight);
        for (MobBalanceSettings.PotionEffectSpec effect : effects) {
            roll -= effect.weight();
            if (roll < 0) return effect;
        }
        return effects.getLast();
    }
}
