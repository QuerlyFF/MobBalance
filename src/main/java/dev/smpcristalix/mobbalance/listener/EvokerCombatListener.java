package dev.smpcristalix.mobbalance.listener;

import dev.smpcristalix.mobbalance.mob.MobBuffService;
import org.bukkit.entity.Evoker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpellCastEvent;

/** Усиливает способности Evoker и режет шанс выпадения тотема. */
public final class EvokerCombatListener implements Listener {

    private final MobBuffService mobBuffService;

    public EvokerCombatListener(MobBuffService mobBuffService) {
        this.mobBuffService = mobBuffService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpellCast(EntitySpellCastEvent event) {
        if (event.getEntity() instanceof Evoker evoker) {
            mobBuffService.onEvokerSpellCast(evoker, event.getSpell());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEvokerDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Evoker evoker) {
            mobBuffService.handleEvokerDeath(evoker, event.getDrops());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpecialDamage(EntityDamageByEntityEvent event) {
        double multiplier = mobBuffService.specialDamageMultiplier(event.getDamager());
        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }
}
