package dev.smpcristalix.mobbalance.listener;

import dev.smpcristalix.mobbalance.mob.MobBuffService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Накладывает яд при успешном прямом укусе усиленного паука.
 */
public final class SpiderAttackListener implements Listener {

    private final MobBuffService mobBuffService;

    public SpiderAttackListener(MobBuffService mobBuffService) {
        this.mobBuffService = mobBuffService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpiderAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        mobBuffService.applySpiderPoison(attacker, victim);
    }
}
