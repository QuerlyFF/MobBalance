package dev.smpcristalix.mobbalance;

import dev.smpcristalix.mobbalance.config.MobBalanceSettings;
import dev.smpcristalix.mobbalance.listener.CreeperTargetListener;
import dev.smpcristalix.mobbalance.listener.EvokerCombatListener;
import dev.smpcristalix.mobbalance.listener.MobProjectileListener;
import dev.smpcristalix.mobbalance.listener.MobSpawnListener;
import dev.smpcristalix.mobbalance.listener.SpiderAttackListener;
import dev.smpcristalix.mobbalance.mob.MobBuffService;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;

/** Главный класс MobBalance. */
public final class MobBalancePlugin extends JavaPlugin {

    private MobBuffService mobBuffService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        MobBalanceSettings settings;
        try {
            settings = MobBalanceSettings.from(getConfig());
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Ошибка config.yml: " + exception.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        mobBuffService = new MobBuffService(this, settings);

        getServer().getPluginManager().registerEvents(new MobSpawnListener(mobBuffService), this);
        getServer().getPluginManager().registerEvents(new MobProjectileListener(mobBuffService), this);
        getServer().getPluginManager().registerEvents(new SpiderAttackListener(mobBuffService), this);
        getServer().getPluginManager().registerEvents(new CreeperTargetListener(mobBuffService), this);
        getServer().getPluginManager().registerEvents(new EvokerCombatListener(mobBuffService), this);
        registerCommand();

        getLogger().info("MobBalance включён. Усиления активны для сложности " + settings.difficulty() + ".");
    }

    private void registerCommand() {
        PluginCommand command = getCommand("mobbalance");
        if (command == null) {
            getLogger().severe("Команда mobbalance отсутствует в plugin.yml");
            return;
        }

        command.setExecutor((sender, ignoredCommand, ignoredLabel, args) -> {
            if (!sender.hasPermission("mobbalance.admin")) {
                sender.sendMessage("§cНет прав.");
                return true;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
                MobBalanceSettings settings = mobBuffService.settings();
                sender.sendMessage("§6MobBalance §7— §f" + (settings.enabled() ? "включён" : "выключен"));
                sender.sendMessage("§7Сложность: §f" + settings.difficulty());
                sender.sendMessage("§7Броня: §f" + percent(settings.armorChance()));
                sender.sendMessage("§7Оружие зомби: §f" + percent(settings.zombieWeaponChance()));
                sender.sendMessage("§7Зачарование брони: §f" + percent(settings.armorEnchantChance()));
                sender.sendMessage("§7Зачарование оружия: §f" + percent(settings.weaponEnchantChance()));
                sender.sendMessage("§7Spider speed: §fx" + settings.spiderSpec(EntityType.SPIDER).speedMultiplier());
                sender.sendMessage("§7Enderman speed: §fx" + settings.endermanSpeedMultiplier());
                sender.sendMessage("§7Vindicator speed: §fx" + settings.vindicatorSpeedMultiplier());
                sender.sendMessage("§7Pillager Quick Charge: §f" + settings.pillagerQuickChargeLevel());
                sender.sendMessage("§7Evoker totem drop: §f" + percent(settings.evokerSpec().totemDropChance()));
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                String previousConfig = getConfig().saveToString();
                reloadConfig();
                MobBalanceSettings settings;
                try {
                    settings = MobBalanceSettings.from(getConfig());
                } catch (IllegalArgumentException exception) {
                    restoreConfig(previousConfig);
                    sender.sendMessage("§cКонфиг не применён: " + exception.getMessage());
                    return true;
                }
                mobBuffService.reload(settings);
                sender.sendMessage("§aMobBalance перезагружен.");
                return true;
            }

            sender.sendMessage("§cИспользование: /mobbalance <status|reload>");
            return true;
        });
    }

    private void restoreConfig(String serialized) {
        try {
            getConfig().loadFromString(serialized);
        } catch (InvalidConfigurationException impossible) {
            throw new IllegalStateException("Could not restore previously valid config", impossible);
        }
    }

    private String percent(double chance) {
        return String.format("%.1f%%", chance * 100.0);
    }
}
