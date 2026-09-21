package dev.smpcristalix.mobbalance.mob;

import dev.smpcristalix.mobbalance.config.MobBalanceSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Breeze;
import org.bukkit.entity.BreezeWindCharge;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Vex;
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

/** Центральный сервис усиления мобов. */
public final class MobBuffService {

    private static final Set<EntityType> SUPPORTED_TYPES = Set.of(
            EntityType.ZOMBIE,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.SKELETON,
            EntityType.STRAY,
            EntityType.BOGGED,
            EntityType.WITHER_SKELETON,
            EntityType.SPIDER,
            EntityType.CAVE_SPIDER,
            EntityType.CREEPER,
            EntityType.SLIME,
            EntityType.WITCH,
            EntityType.ENDERMAN,
            EntityType.PHANTOM,
            EntityType.PILLAGER,
            EntityType.VINDICATOR,
            EntityType.EVOKER,
            EntityType.BREEZE,
            EntityType.BLAZE,
            EntityType.GHAST,
            EntityType.PIGLIN
    );

    private static final Set<EntityType> FULL_EQUIPMENT_TYPES = Set.of(
            EntityType.ZOMBIE,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.SKELETON,
            EntityType.STRAY,
            EntityType.BOGGED,
            EntityType.WITHER_SKELETON
    );

    private final Plugin plugin;
    private final NamespacedKey buffedKey;
    private final NamespacedKey damageMultiplierKey;
    private final NamespacedKey creeperBaseSpeedKey;
    private final NamespacedKey creeperSpeedBoostedKey;
    private final EquipmentBuffService equipmentBuffService = new EquipmentBuffService();
    private final Set<UUID> witchExtraPotionGuard = new HashSet<>();
    private final Set<UUID> extraProjectileGuard = new HashSet<>();
    private volatile MobBalanceSettings settings;

    public MobBuffService(Plugin plugin, MobBalanceSettings settings) {
        this.plugin = plugin;
        this.buffedKey = new NamespacedKey(plugin, "buffed");
        this.damageMultiplierKey = new NamespacedKey(plugin, "damage_multiplier");
        this.creeperBaseSpeedKey = new NamespacedKey(plugin, "creeper_base_speed");
        this.creeperSpeedBoostedKey = new NamespacedKey(plugin, "creeper_speed_boosted");
        this.settings = settings;
    }

    public void reload(MobBalanceSettings newSettings) { this.settings = newSettings; }
    public MobBalanceSettings settings() { return settings; }

    public boolean isBuffed(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(buffedKey, PersistentDataType.BYTE);
    }

    public boolean tryBuff(LivingEntity entity, String spawnReason) {
        MobBalanceSettings current = settings;
        if (!current.enabled()) return false;
        if (entity.getWorld().getDifficulty() != current.difficulty()) return false;
        if (!SUPPORTED_TYPES.contains(entity.getType())) return false;
        if (!current.acceptsSpawnReason(spawnReason)) return false;

        EntityType configuredType = entity.getType();
        if (configuredType == EntityType.SLIME && !current.slimeEnabled()) return false;
        if (configuredType == EntityType.WITHER_SKELETON && !current.witherSkeletonEnabled()) return false;
        if (configuredType == EntityType.PIGLIN && !current.piglinCrossbowBuff()) return false;

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(buffedKey, PersistentDataType.BYTE)) return false;

        RandomGenerator random = ThreadLocalRandom.current();
        EntityType type = entity.getType();

        boolean buffHealth = switch (type) {
            case PHANTOM, PILLAGER, EVOKER, BREEZE, GHAST, PIGLIN -> false;
            default -> true;
        };
        boolean buffDamage = switch (type) {
            case WITCH, PILLAGER, VINDICATOR, EVOKER, BREEZE, BLAZE, GHAST, PIGLIN -> false;
            default -> true;
        };
        applyStats(entity, current, random, buffHealth, buffDamage);

        if (isSpider(type)) {
            multiplyAttribute(entity, Attribute.GENERIC_MOVEMENT_SPEED, current.spiderSpec(type).speedMultiplier());
        } else if (type == EntityType.ENDERMAN) {
            multiplyAttribute(entity, Attribute.GENERIC_MOVEMENT_SPEED, current.endermanSpeedMultiplier());
        } else if (type == EntityType.PHANTOM) {
            applyPhantomSpeed(entity, current.phantomSpeedMultiplier());
        } else if (type == EntityType.VINDICATOR) {
            multiplyAttribute(entity, Attribute.GENERIC_MOVEMENT_SPEED, current.vindicatorSpeedMultiplier());
        }

        if (entity instanceof Creeper creeper) applyCreeperBaseBuffs(creeper, current);
        if (FULL_EQUIPMENT_TYPES.contains(type)) equipmentBuffService.apply(entity, current, random);
        if (entity instanceof Pillager || entity instanceof Piglin) equipmentBuffService.applyCrossbowBuff(entity, current);

        pdc.set(buffedKey, PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    public void buffProjectile(Projectile projectile) {
        ProjectileSource source = projectile.getShooter();
        if (!(source instanceof LivingEntity shooter) || !isBuffed(shooter)) return;
        if (extraProjectileGuard.contains(shooter.getUniqueId())) return;

        EntityType shooterType = shooter.getType();
        if (projectile instanceof AbstractArrow arrow) {
            buffArrow(arrow, shooterType);
            return;
        }
        if (projectile instanceof BreezeWindCharge && shooter instanceof Breeze) {
            scheduleExtraProjectile(shooter, projectile.getVelocity().clone(), settings.breezeSpec(), ProjectileKind.BREEZE_WIND_CHARGE);
            return;
        }
        if (projectile instanceof SmallFireball && shooter instanceof Blaze) {
            scheduleExtraProjectile(shooter, projectile.getVelocity().clone(), settings.blazeSpec(), ProjectileKind.SMALL_FIREBALL);
            return;
        }
        if (projectile instanceof LargeFireball && shooter instanceof Ghast) {
            MobBalanceSettings.GhastSpec spec = settings.ghastSpec();
            scheduleExtraProjectile(shooter, projectile.getVelocity().clone(),
                    new MobBalanceSettings.RangedRateSpec(spec.extraShotChance(), spec.extraShotDelayTicks()),
                    ProjectileKind.LARGE_FIREBALL);
        }
    }

    private void buffArrow(AbstractArrow projectile, EntityType shooterType) {
        boolean skeleton = isSkeletonFamily(shooterType);
        boolean raider = shooterType == EntityType.PILLAGER || shooterType == EntityType.PIGLIN;
        if (!skeleton && !raider) return;

        Double damageMultiplier = projectile.getShooter() instanceof LivingEntity shooter
                ? shooter.getPersistentDataContainer().get(damageMultiplierKey, PersistentDataType.DOUBLE)
                : null;
        if (skeleton && damageMultiplier != null) projectile.setDamage(projectile.getDamage() * damageMultiplier);

        if (projectile instanceof Arrow arrow) {
            if (shooterType == EntityType.BOGGED) {
                arrow.addCustomEffect(new PotionEffect(
                        PotionEffectType.POISON,
                        settings.boggedPoisonDurationTicks(),
                        settings.boggedPoisonAmplifier(),
                        false, true, true
                ), true);
            }
            applyNegativeEffect(arrow, shooterType, settings, ThreadLocalRandom.current());
        }
    }

    public void buffWitchPotion(ThrownPotion potion) {
        if (!(potion.getShooter() instanceof Witch witch) || !isBuffed(witch)) return;
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

    public void onEvokerSpellCast(Evoker evoker, Spellcaster.Spell spell) {
        if (!isBuffed(evoker)) return;
        MobBalanceSettings.EvokerSpec spec = settings.evokerSpec();
        if (ThreadLocalRandom.current().nextDouble() >= spec.repeatAbilityChance()) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!evoker.isValid() || evoker.isDead()) return;
            if (spell == Spellcaster.Spell.FANGS) {
                spawnExtraFangs(evoker);
            } else if (spell == Spellcaster.Spell.SUMMON_VEX) {
                spawnExtraVexes(evoker, spec.extraVexCount());
            }
        }, spec.repeatDelayTicks());
    }

    public void handleEvokerDeath(Evoker evoker, List<ItemStack> drops) {
        if (!isBuffed(evoker)) return;
        if (ThreadLocalRandom.current().nextDouble() < settings.evokerSpec().totemDropChance()) return;
        drops.removeIf(item -> item.getType() == Material.TOTEM_OF_UNDYING);
    }

    public boolean shouldApplyGlobalRules(LivingEntity entity) {
        MobBalanceSettings current = settings;
        return current.enabled() && entity.getWorld().getDifficulty() == current.difficulty();
    }

    public double specialDamageMultiplier(Entity damager) {
        if (damager instanceof EvokerFangs fangs && fangs.getOwner() instanceof Evoker evoker && isBuffed(evoker)) {
            return settings.evokerSpec().fangDamageMultiplier();
        }
        if (damager instanceof LargeFireball fireball && fireball.getShooter() instanceof Ghast ghast && isBuffed(ghast)) {
            return settings.ghastSpec().damageMultiplier();
        }
        return 1.0;
    }

    public void applySpiderPoison(LivingEntity attacker, LivingEntity victim) {
        if (!isSpider(attacker.getType()) || !isBuffed(attacker)) return;
        MobBalanceSettings.SpiderSpec spec = settings.spiderSpec(attacker.getType());
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                spec.poisonDurationTicks(), spec.poisonAmplifier(), false, true, true));
    }

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

    private void scheduleExtraProjectile(LivingEntity shooter, Vector velocity,
                                         MobBalanceSettings.RangedRateSpec spec, ProjectileKind kind) {
        if (ThreadLocalRandom.current().nextDouble() >= spec.extraShotChance()) return;
        UUID shooterId = shooter.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!shooter.isValid() || shooter.isDead()) return;
            extraProjectileGuard.add(shooterId);
            try {
                switch (kind) {
                    case BREEZE_WIND_CHARGE -> shooter.launchProjectile(BreezeWindCharge.class, velocity);
                    case SMALL_FIREBALL -> shooter.launchProjectile(SmallFireball.class, velocity);
                    case LARGE_FIREBALL -> shooter.launchProjectile(LargeFireball.class, velocity);
                }
            } finally {
                extraProjectileGuard.remove(shooterId);
            }
        }, spec.extraShotDelayTicks());
    }

    private void spawnExtraFangs(Evoker evoker) {
        LivingEntity target = evoker.getTarget();
        if (target == null) return;
        Location start = evoker.getLocation();
        Vector direction = target.getLocation().toVector().subtract(start.toVector()).setY(0);
        if (direction.lengthSquared() < 1.0E-8) return;
        direction.normalize();
        double y = target.getLocation().getY();

        for (int i = 1; i <= 6; i++) {
            Location loc = start.clone().add(direction.clone().multiply(i * 1.25));
            loc.setY(y);
            int delay = i * 2;
            evoker.getWorld().spawn(loc, EvokerFangs.class, fang -> {
                fang.setOwner(evoker);
                fang.setAttackDelay(delay);
            });
        }
    }

    private void spawnExtraVexes(Evoker evoker, int count) {
        if (count <= 0) return;
        LivingEntity target = evoker.getTarget();
        for (int i = 0; i < count; i++) {
            Location loc = evoker.getLocation().clone().add(
                    ThreadLocalRandom.current().nextDouble(-1.5, 1.5), 1.0,
                    ThreadLocalRandom.current().nextDouble(-1.5, 1.5));
            evoker.getWorld().spawn(loc, Vex.class, vex -> {
                vex.setSummoner(evoker);
                if (target != null) vex.setTarget(target);
            });
        }
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
            if (attackDamage != null) attackDamage.setBaseValue(attackDamage.getBaseValue() * damageMultiplier);
        }
        entity.getPersistentDataContainer().set(damageMultiplierKey, PersistentDataType.DOUBLE, damageMultiplier);
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
        creeper.setExplosionRadius(Math.max(1,
                (int) Math.round(creeper.getExplosionRadius() * spec.explosionRadiusMultiplier())));
        creeper.setMaxFuseTicks(Math.max(1,
                (int) Math.round(creeper.getMaxFuseTicks() * spec.fuseTimeMultiplier())));
    }

    private boolean isSpider(EntityType type) {
        return type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER;
    }

    private boolean isSkeletonFamily(EntityType type) {
        return type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED;
    }

    private void applyNegativeEffect(Arrow arrow, EntityType shooterType,
                                     MobBalanceSettings settings, RandomGenerator random) {
        if (random.nextDouble() >= settings.negativeEffectChance(shooterType)) return;
        MobBalanceSettings.PotionEffectSpec spec = chooseEffect(settings.projectileEffects(), random);
        if (spec == null) return;
        arrow.addCustomEffect(new PotionEffect(spec.type(), spec.durationTicks(), spec.amplifier(), false, true, true), false);
    }

    private MobBalanceSettings.PotionEffectSpec chooseEffect(List<MobBalanceSettings.PotionEffectSpec> effects,
                                                               RandomGenerator random) {
        long totalWeight = effects.stream().mapToLong(MobBalanceSettings.PotionEffectSpec::weight).sum();
        if (totalWeight <= 0) return null;
        long roll = random.nextLong(totalWeight);
        for (MobBalanceSettings.PotionEffectSpec effect : effects) {
            roll -= effect.weight();
            if (roll < 0) return effect;
        }
        return effects.getLast();
    }

    private enum ProjectileKind {
        BREEZE_WIND_CHARGE,
        SMALL_FIREBALL,
        LARGE_FIREBALL
    }
}
