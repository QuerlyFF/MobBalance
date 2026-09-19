package dev.smpcristalix.mobbalance.listener;

import dev.smpcristalix.mobbalance.mob.MobBuffService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Обрабатывает только сам момент появления сущности.
 * Повторная загрузка чанка не усиливает моба второй раз.
 */
public final class MobSpawnListener implements Listener {

    private final MobBuffService mobBuffService;

    public MobSpawnListener(MobBuffService mobBuffService) {
        this.mobBuffService = mobBuffService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        mobBuffService.tryBuff(event.getEntity(), event.getSpawnReason().name());
    }
}
