package dev.smpcristalix.mobbalance.listener;

import dev.smpcristalix.mobbalance.mob.MobBuffService;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/**
 * Усиливает стрелы Skeleton/Stray/Bogged и броски зелий Witch.
 */
public final class MobProjectileListener implements Listener {

    private final MobBuffService mobBuffService;

    public MobProjectileListener(MobBuffService mobBuffService) {
        this.mobBuffService = mobBuffService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof AbstractArrow arrow) {
            mobBuffService.buffProjectile(arrow);
            return;
        }
        if (event.getEntity() instanceof ThrownPotion potion) {
            mobBuffService.buffWitchPotion(potion);
        }
    }
}
