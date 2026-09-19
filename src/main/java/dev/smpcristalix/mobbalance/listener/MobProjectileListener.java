package dev.smpcristalix.mobbalance.listener;

import dev.smpcristalix.mobbalance.mob.MobBuffService;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/** Усиливает стрелы, огненные шары, wind charge и броски зелий Witch. */
public final class MobProjectileListener implements Listener {

    private final MobBuffService mobBuffService;

    public MobProjectileListener(MobBuffService mobBuffService) {
        this.mobBuffService = mobBuffService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        mobBuffService.buffProjectile(projectile);
        if (projectile instanceof ThrownPotion potion) {
            mobBuffService.buffWitchPotion(potion);
        }
    }
}
