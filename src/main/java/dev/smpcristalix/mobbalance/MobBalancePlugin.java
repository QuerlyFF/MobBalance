package dev.smpcristalix.mobbalance;

import dev.smpcristalix.mobbalance.config.MobBalanceSettings;
import dev.smpcristalix.mobbalance.listener.CreeperTargetListener;
import dev.smpcristalix.mobbalance.listener.MobProjectileListener;
import dev.smpcristalix.mobbalance.listener.MobSpawnListener;
import dev.smpcristalix.mobbalance.listener.SpiderAttackListener;
import dev.smpcristalix.mobbalance.mob.MobBuffService;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Главный класс MobBalance.
 */
public final class MobBalancePlugin extends JavaPlugin {

    private MobBuffService mobBuffService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        MobBalanceSettings settings = MobBalanceSettings.from(getConfig());
        mobBuffService = new MobBuffService(this, settings);

        getServer().getPluginManager().registerEvents(new MobSpawnListener(mobBuffService), this);
        getServer().getPluginManager().registerEvents(new MobProjectileListener(mobBuffService), this);
        getServer().getPluginManager().registerEvents(new SpiderAttackListener(mobBuffService), this);
        getServer().getPluginManager().registerEvents(new CreeperTargetListener(mobBuffService), this);
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
            if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
                MobBalanceSettings settings = mobBuffService.settings();
                sender.sendMessage("§6MobBalance §7— §f" + (settings.enabled() ? "включён" : "выключен"));
                sender.sendMessage("§7Сложность: §f" + settings.difficulty());
                sender.sendMessage("§7Броня: §f" + percent(settings.armorChance()));
                sender.sendMessage("§7Оружие зомби: §f" + percent(settings.zombieWeaponChance()));
                sender.sendMessage("§7Зачарование брони: §f" + percent(settings.armorEnchantChance()));
                sender.sendMessage("§7Зачарование оружия: §f" + percent(settings.weaponEnchantChance()));
                sender.sendMessage("§7Spider speed: §fx" + settings.spiderSpec(EntityType.SPIDER).speedMultiplier());
                sender.sendMessage("§7Cave Spider speed: §fx" + settings.spiderSpec(EntityType.CAVE_SPIDER).speedMultiplier());
                sender.sendMessage("§7Creeper aggro speed: §fx" + settings.creeperSpec().aggroSpeedMultiplier());
                sender.sendMessage("§7Creeper explosion radius: §fx" + settings.creeperSpec().explosionRadiusMultiplier());
                sender.sendMessage("§7Creeper fuse time: §fx" + settings.creeperSpec().fuseTimeMultiplier());
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                MobBalanceSettings settings = MobBalanceSettings.from(getConfig());
                mobBuffService.reload(settings);
                sender.sendMessage("§aMobBalance перезагружен.");
                return true;
            }

            sender.sendMessage("§cИспользование: /mobbalance <status|reload>");
            return true;
        });
    }

    private String percent(double chance) {
        return String.format("%.1f%%", chance * 100.0);
    }
}
