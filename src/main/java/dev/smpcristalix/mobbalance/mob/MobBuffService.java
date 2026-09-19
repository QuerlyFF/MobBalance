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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;
import java.util.Set;
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
            EntityType.SPIDER,
            EntityType.CAVE_SPIDER,
            EntityType.CREEPER
    );

    private static final Set<EntityType> EQUIPMENT_TYPES = Set.of(
            EntityType.ZOMBIE,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.SKELETON,
            EntityType.STRAY
    );

    private final NamespacedKey buffedKey;
    private final NamespacedKey damageMultiplierKey;
    private final NamespacedKey creeperBaseSpeedKey;
    private final NamespacedKey creeperSpeedBoostedKey;
    private final EquipmentBuffService equipmentBuffService = new EquipmentBuffService();
    private volatile MobBalanceSettings settings;

    public MobBuffService(Plugin plugin, MobBalanceSettings settings) {
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
        applyStats(entity, current, random);
        if (isSpider(entity.getType())) {
            applySpiderSpeed(entity, current);
        }
        if (entity instanceof Creeper creeper) {
            applyCreeperBaseBuffs(creeper, current);
        }
        if (EQUIPMENT_TYPES.contains(entity.getType())) {
            equipmentBuffService.apply(entity, current, random);
        }
        pdc.set(buffedKey, PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    public void buffProjectile(AbstractArrow projectile) {
        ProjectileSource source = projectile.getShooter();
        if (!(source instanceof LivingEntity shooter)) return;
        if (shooter.getType() != EntityType.SKELETON && shooter.getType() != EntityType.STRAY) return;

        PersistentDataContainer pdc = shooter.getPersistentDataContainer();
        if (!pdc.has(buffedKey, PersistentDataType.BYTE)) return;

        Double damageMultiplier = pdc.get(damageMultiplierKey, PersistentDataType.DOUBLE);
        if (damageMultiplier != null) {
            projectile.setDamage(projectile.getDamage() * damageMultiplier);
        }

        if (projectile instanceof Arrow arrow) {
            applyNegativeEffect(arrow, shooter.getType(), settings, ThreadLocalRandom.current());
        }
    }

    /**
     * Обычный паук всегда накладывает Poison I, пещерный — усиленный Poison II.
     * Эффект получают только удары пауков, уже обработанных MobBalance.
     */
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

    /**
     * Creeper ускоряется только пока у него есть цель. При потере цели скорость возвращается.
     */
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
        if (baseSpeed != null) {
            movementSpeed.setBaseValue(baseSpeed);
        }
        pdc.remove(creeperBaseSpeedKey);
        pdc.remove(creeperSpeedBoostedKey);
    }

    private void applyStats(LivingEntity entity, MobBalanceSettings settings, RandomGenerator random) {
        double healthMultiplier = settings.randomHealthMultiplier(random);
        AttributeInstance maxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() * healthMultiplier);
            entity.setHealth(maxHealth.getValue());
        }

        double damageMultiplier = settings.randomDamageMultiplier(random);
        AttributeInstance attackDamage = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.setBaseValue(attackDamage.getBaseValue() * damageMultiplier);
        }
        entity.getPersistentDataContainer().set(
                damageMultiplierKey,
                PersistentDataType.DOUBLE,
                damageMultiplier
        );
    }

    private void applySpiderSpeed(LivingEntity entity, MobBalanceSettings settings) {
        AttributeInstance movementSpeed = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (movementSpeed == null) return;

        MobBalanceSettings.SpiderSpec spec = settings.spiderSpec(entity.getType());
        movementSpeed.setBaseValue(movementSpeed.getBaseValue() * spec.speedMultiplier());
    }

    private void applyCreeperBaseBuffs(Creeper creeper, MobBalanceSettings settings) {
        MobBalanceSettings.CreeperSpec spec = settings.creeperSpec();

        int buffedRadius = Math.max(1, (int) Math.round(creeper.getExplosionRadius() * spec.explosionRadiusMultiplier()));
        creeper.setExplosionRadius(buffedRadius);

        int buffedFuse = Math.max(1, (int) Math.round(creeper.getMaxFuseTicks() * spec.fuseTimeMultiplier()));
        creeper.setMaxFuseTicks(buffedFuse);
    }

    private boolean isSpider(EntityType type) {
        return type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER;
    }

    private void applyNegativeEffect(
            Arrow arrow,
            EntityType shooterType,
            MobBalanceSettings settings,
            RandomGenerator random
    ) {
        if (random.nextDouble() >= settings.negativeEffectChance(shooterType)) return;

        MobBalanceSettings.PotionEffectSpec spec = chooseEffect(settings.projectileEffects(), random);
        if (spec == null) return;

        PotionEffect effect = new PotionEffect(
                spec.type(),
                spec.durationTicks(),
                spec.amplifier(),
                false,
                true,
                true
        );
        arrow.addCustomEffect(effect, false);
    }

    private MobBalanceSettings.PotionEffectSpec chooseEffect(
            List<MobBalanceSettings.PotionEffectSpec> effects,
            RandomGenerator random
    ) {
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
