package dev.smpcristalix.mobbalance.mob;

import dev.smpcristalix.mobbalance.config.MobBalanceSettings;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.random.RandomGenerator;

/** Усиливает экипировку мобов. */
public final class EquipmentBuffService {

    public void apply(LivingEntity entity, MobBalanceSettings settings, RandomGenerator random) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;

        if (random.nextDouble() < settings.armorChance()) applyArmor(equipment, settings, random);
        enchantArmor(equipment, settings, random);

        EntityType type = entity.getType();
        if (type == EntityType.DROWNED) {
            applyDrownedWeapon(equipment, settings, random);
        } else if (isZombieFamily(type)) {
            applyZombieWeapon(equipment, settings, random);
        } else if (isBowSkeletonFamily(type)) {
            ensureBow(equipment, settings);
        }

        enchantMainHand(equipment, settings, random);
    }

    public void applyCrossbowBuff(LivingEntity entity, MobBalanceSettings settings) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;

        ItemStack weapon = equipment.getItemInMainHand();
        if (entity.getType() == EntityType.PILLAGER && weapon.getType() != Material.CROSSBOW) {
            weapon = new ItemStack(Material.CROSSBOW);
            equipment.setItemInMainHand(weapon);
            equipment.setItemInMainHandDropChance(settings.generatedItemDropChance());
        }
        if (weapon.getType() != Material.CROSSBOW) return;

        addAtLeast(weapon, Enchantment.QUICK_CHARGE, settings.pillagerQuickChargeLevel());
        equipment.setItemInMainHandDropChance(settings.generatedItemDropChance());
    }

    private void applyArmor(EntityEquipment equipment, MobBalanceSettings settings, RandomGenerator random) {
        ArmorTier tier = ArmorTier.values()[weightedIndex(settings.armorTierWeights(), random)];
        int pieceCount = weightedIndex(settings.armorPieceCountWeights(), random) + 1;
        int[] slots = {0, 1, 2, 3};
        shuffle(slots, random);
        for (int i = 0; i < pieceCount; i++) upgradeArmorSlot(equipment, slots[i], tier, settings.generatedItemDropChance());
    }

    private void upgradeArmorSlot(EntityEquipment equipment, int slot, ArmorTier tier, float dropChance) {
        ItemStack current = getArmor(equipment, slot);
        ItemStack candidate = new ItemStack(tier.material(slot));
        if (!shouldReplaceArmor(current, candidate)) return;
        setArmor(equipment, slot, candidate);
        setArmorDropChance(equipment, slot, dropChance);
    }

    private boolean shouldReplaceArmor(ItemStack current, ItemStack candidate) {
        if (current == null || current.getType().isAir()) return true;
        return armorScore(candidate.getType()) > armorScore(current.getType());
    }

    private void applyZombieWeapon(EntityEquipment equipment, MobBalanceSettings settings, RandomGenerator random) {
        if (random.nextDouble() >= settings.zombieWeaponChance()) return;
        Material weapon = random.nextDouble() < 0.70 ? Material.IRON_SWORD : Material.IRON_SHOVEL;
        equipment.setItemInMainHand(new ItemStack(weapon));
        equipment.setItemInMainHandDropChance(settings.generatedItemDropChance());
    }

    private void applyDrownedWeapon(EntityEquipment equipment, MobBalanceSettings settings, RandomGenerator random) {
        if (random.nextDouble() >= settings.drownedWeaponChance()) return;
        Material weapon = random.nextDouble() < settings.drownedTridentShare() ? Material.TRIDENT : Material.IRON_SWORD;
        equipment.setItemInMainHand(new ItemStack(weapon));
        equipment.setItemInMainHandDropChance(settings.generatedItemDropChance());
    }

    private void ensureBow(EntityEquipment equipment, MobBalanceSettings settings) {
        ItemStack current = equipment.getItemInMainHand();
        if (current.getType() == Material.BOW) return;
        equipment.setItemInMainHand(new ItemStack(Material.BOW));
        equipment.setItemInMainHandDropChance(settings.generatedItemDropChance());
    }

    private void enchantArmor(EntityEquipment equipment, MobBalanceSettings settings, RandomGenerator random) {
        for (int slot = 0; slot < 4; slot++) enchantArmorPiece(equipment, slot, settings, random);
    }

    private void enchantArmorPiece(EntityEquipment equipment, int slot, MobBalanceSettings settings, RandomGenerator random) {
        ItemStack item = getArmor(equipment, slot);
        if (item == null || item.getType().isAir() || random.nextDouble() >= settings.armorEnchantChance()) return;
        addAtLeast(item, Enchantment.PROTECTION, random.nextInt(1, 4));
        if (random.nextDouble() < 0.20) addAtLeast(item, Enchantment.UNBREAKING, random.nextInt(1, 4));
        if (random.nextDouble() < 0.10) addAtLeast(item, Enchantment.THORNS, 1);
        setArmorDropChance(equipment, slot, settings.generatedItemDropChance());
    }

    private void enchantMainHand(EntityEquipment equipment, MobBalanceSettings settings, RandomGenerator random) {
        ItemStack weapon = equipment.getItemInMainHand();
        if (weapon.getType().isAir() || random.nextDouble() >= settings.weaponEnchantChance()) return;

        switch (weapon.getType()) {
            case BOW -> {
                addAtLeast(weapon, Enchantment.POWER, random.nextInt(1, 4));
                if (random.nextDouble() < 0.15) addAtLeast(weapon, Enchantment.PUNCH, 1);
                if (random.nextDouble() < 0.08) addAtLeast(weapon, Enchantment.FLAME, 1);
            }
            case TRIDENT -> addAtLeast(weapon, Enchantment.IMPALING, random.nextInt(1, 4));
            case IRON_SWORD, STONE_SWORD -> {
                addAtLeast(weapon, Enchantment.SHARPNESS, random.nextInt(1, 4));
                if (random.nextDouble() < 0.15) addAtLeast(weapon, Enchantment.KNOCKBACK, 1);
                if (random.nextDouble() < 0.08) addAtLeast(weapon, Enchantment.FIRE_ASPECT, 1);
            }
            case IRON_SHOVEL -> addAtLeast(weapon, Enchantment.SHARPNESS, random.nextInt(1, 3));
            default -> { return; }
        }
        equipment.setItemInMainHandDropChance(settings.generatedItemDropChance());
    }

    private void addAtLeast(ItemStack item, Enchantment enchantment, int level) {
        if (level > item.getEnchantmentLevel(enchantment)) item.addUnsafeEnchantment(enchantment, level);
    }

    private boolean isZombieFamily(EntityType type) {
        return type == EntityType.ZOMBIE || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.HUSK;
    }

    private boolean isBowSkeletonFamily(EntityType type) {
        return type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED;
    }

    private int weightedIndex(int[] weights, RandomGenerator random) {
        long total = 0;
        for (int weight : weights) total += Math.max(0, weight);
        if (total <= 0) return 0;
        long roll = random.nextLong(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= Math.max(0, weights[i]);
            if (roll < 0) return i;
        }
        return weights.length - 1;
    }

    private void shuffle(int[] values, RandomGenerator random) {
        for (int i = values.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = values[i]; values[i] = values[j]; values[j] = tmp;
        }
    }

    private ItemStack getArmor(EntityEquipment equipment, int slot) {
        return switch (slot) {
            case 0 -> equipment.getHelmet();
            case 1 -> equipment.getChestplate();
            case 2 -> equipment.getLeggings();
            case 3 -> equipment.getBoots();
            default -> throw new IllegalArgumentException("Unknown armor slot: " + slot);
        };
    }

    private void setArmor(EntityEquipment equipment, int slot, ItemStack item) {
        switch (slot) {
            case 0 -> equipment.setHelmet(item);
            case 1 -> equipment.setChestplate(item);
            case 2 -> equipment.setLeggings(item);
            case 3 -> equipment.setBoots(item);
            default -> throw new IllegalArgumentException("Unknown armor slot: " + slot);
        }
    }

    private void setArmorDropChance(EntityEquipment equipment, int slot, float chance) {
        switch (slot) {
            case 0 -> equipment.setHelmetDropChance(chance);
            case 1 -> equipment.setChestplateDropChance(chance);
            case 2 -> equipment.setLeggingsDropChance(chance);
            case 3 -> equipment.setBootsDropChance(chance);
            default -> throw new IllegalArgumentException("Unknown armor slot: " + slot);
        }
    }

    private int armorScore(Material material) {
        String name = material.name();
        if (name.startsWith("DIAMOND_")) return 5;
        if (name.startsWith("IRON_")) return 4;
        if (name.startsWith("CHAINMAIL_")) return 3;
        if (name.startsWith("GOLDEN_")) return 2;
        if (name.startsWith("LEATHER_")) return 1;
        return 0;
    }

    private enum ArmorTier {
        LEATHER, GOLD, CHAINMAIL, IRON, DIAMOND;
        private Material material(int slot) {
            return switch (this) {
                case LEATHER -> new Material[]{Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS}[slot];
                case GOLD -> new Material[]{Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS}[slot];
                case CHAINMAIL -> new Material[]{Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS}[slot];
                case IRON -> new Material[]{Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS}[slot];
                case DIAMOND -> new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}[slot];
            };
        }
    }
}
