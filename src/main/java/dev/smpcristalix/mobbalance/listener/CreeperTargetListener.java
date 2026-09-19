package dev.smpcristalix.mobbalance.listener;

import dev.smpcristalix.mobbalance.mob.MobBuffService;
import org.bukkit.entity.Creeper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * Ускоряет усиленного Creeper только пока он преследует живую цель.
 */
public final class CreeperTargetListener implements Listener {

    private final MobBuffService mobBuffService;

    public CreeperTargetListener(MobBuffService mobBuffService) {
        this.mobBuffService = mobBuffService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper)) return;
        mobBuffService.setCreeperAggro(creeper, event.getTarget() != null);
    }
}
