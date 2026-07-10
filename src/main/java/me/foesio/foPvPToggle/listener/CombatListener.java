package me.foesio.foPvPToggle.listener;

import me.foesio.foPvPToggle.PvpToggleService;
import me.foesio.core.message.FoMessageService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatListener implements Listener {
    private static final long BLOCK_EXPLOSION_OWNER_TTL_MILLIS = 4000L;
    private static final long CRYSTAL_OWNER_TTL_MILLIS = 120_000L;
    private static final long OWNER_PRUNE_INTERVAL_MILLIS = 1000L;
    private static final long BLOCKED_MESSAGE_COOLDOWN_MILLIS = 1500L;
    private static final double BLOCK_EXPLOSION_MATCH_DISTANCE_SQUARED = 64.0D;
    private static final int MAX_CRYSTAL_OWNERS = 2048;
    private static final int MAX_BLOCK_EXPLOSION_OWNERS = 256;

    private final PvpToggleService toggleService;
    private final FoMessageService messages;
    private final Map<UUID, ExplosionOwner> crystalOwners = new ConcurrentHashMap<>();
    private final Map<UUID, Long> blockedMessageCooldowns = new ConcurrentHashMap<>();
    private final List<BlockExplosionOwner> blockExplosionOwners = new ArrayList<>();
    private volatile long lastCrystalOwnerPruneMillis;

    public CombatListener(PvpToggleService toggleService, FoMessageService messages) {
        this.toggleService = toggleService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof EnderCrystal) {
            rememberCrystalOwner(event.getEntity().getUniqueId(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Material type = event.getClickedBlock().getType();
        if (!isExplosiveRespawnBlock(type, event.getClickedBlock().getWorld())) {
            return;
        }

        recordBlockExplosionOwner(event.getPlayer(), event.getClickedBlock().getLocation().add(0.5D, 0.5D, 0.5D));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity livingVictim)) {
            return;
        }

        DamageSource damageSource = event.getDamageSource();
        UUID ownerId = resolveDamageSourceOwner(damageSource);
        if (ownerId == null && event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            Location sourceLocation = damageSource == null ? null : damageSource.getSourceLocation();
            ownerId = findRecentBlockExplosionOwner(sourceLocation, livingVictim.getLocation());
        }

        if (ownerId == null) {
            return;
        }

        if (livingVictim instanceof Player victim && toggleService.shouldCancelPvp(ownerId, victim.getUniqueId())) {
            event.setCancelled(true);
            notifyBlocked(ownerId);
            notifyBlocked(victim);
            return;
        }

        if (!(livingVictim instanceof Player) && toggleService.isPveProtected(ownerId)) {
            event.setCancelled(true);
            notifyBlocked(ownerId);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player damager = resolvePlayer(event.getDamager(), event.getDamageSource());
        Player victim = event.getEntity() instanceof Player player ? player : null;
        UUID indirectOwner = null;
        boolean indirectOwnerResolved = false;

        if (event.getEntity() instanceof EnderCrystal) {
            if (damager == null) {
                indirectOwner = resolveIndirectOwner(event.getDamager(), event.getDamageSource());
                indirectOwnerResolved = true;
            }
            UUID ownerId = damager != null ? damager.getUniqueId() : indirectOwner;
            if (ownerId != null) {
                rememberCrystalOwner(event.getEntity().getUniqueId(), ownerId);
            }
        }

        if (damager != null && victim != null && toggleService.shouldCancelPvp(damager, victim)) {
            event.setCancelled(true);
            notifyBlocked(damager);
            notifyBlocked(victim);
            return;
        }

        if (damager == null && victim != null && resolveLivingSource(event.getDamager()) != null) {
            if (toggleService.shouldCancelMobToPlayer(victim)) {
                event.setCancelled(true);
                notifyBlocked(victim);
            }
            return;
        }

        if (damager == null && !indirectOwnerResolved) {
            indirectOwner = resolveIndirectOwner(event.getDamager(), event.getDamageSource());
        }

        if (damager == null && indirectOwner != null && victim != null
                && toggleService.shouldCancelPvp(indirectOwner, victim.getUniqueId())) {
            event.setCancelled(true);
            notifyBlocked(indirectOwner);
            notifyBlocked(victim);
            return;
        }

        if (damager != null && victim == null && event.getEntity() instanceof LivingEntity) {
            if (toggleService.shouldCancelPlayerToMob(damager)) {
                event.setCancelled(true);
                notifyBlocked(damager);
            }
            return;
        }

        if (damager == null && indirectOwner != null && victim == null && event.getEntity() instanceof LivingEntity
                && toggleService.isPveProtected(indirectOwner)) {
            event.setCancelled(true);
            notifyBlocked(indirectOwner);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        if (!toggleService.shouldCancelMobToPlayer(player)) {
            return;
        }
        event.setCancelled(true);
        event.setTarget(null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        blockedMessageCooldowns.remove(event.getPlayer().getUniqueId());
    }

    private Player resolvePlayer(Entity damager, DamageSource damageSource) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        Entity causingEntity = damageSource == null ? null : damageSource.getCausingEntity();
        if (causingEntity instanceof Player player) {
            return player;
        }
        return null;
    }

    private UUID resolveIndirectOwner(Entity damager, DamageSource damageSource) {
        UUID damageSourceOwner = resolveDamageSourceOwner(damageSource);
        if (damageSourceOwner != null) {
            return damageSourceOwner;
        }

        if (damager instanceof TNTPrimed tnt) {
            Entity source = tnt.getSource();
            if (source instanceof Player player) {
                return player.getUniqueId();
            }
            if (source instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
                return player.getUniqueId();
            }
        }

        if (damager instanceof EnderCrystal crystal) {
            UUID ownerId = findCrystalOwner(crystal.getUniqueId());
            if (ownerId != null) {
                return ownerId;
            }
        }

        if (damager != null && damager.getType() == EntityType.TNT_MINECART) {
            return findRecentBlockExplosionOwner(damager.getLocation(), damager.getLocation());
        }

        return null;
    }

    private UUID resolveDamageSourceOwner(DamageSource damageSource) {
        if (damageSource == null) {
            return null;
        }

        Entity causingEntity = damageSource.getCausingEntity();
        if (causingEntity instanceof Player player) {
            return player.getUniqueId();
        }
        if (causingEntity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player.getUniqueId();
        }

        Entity directEntity = damageSource.getDirectEntity();
        if (directEntity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player.getUniqueId();
        }
        if (directEntity instanceof EnderCrystal crystal) {
            UUID ownerId = findCrystalOwner(crystal.getUniqueId());
            if (ownerId != null) {
                return ownerId;
            }
        }
        return null;
    }

    private LivingEntity resolveLivingSource(Entity damager) {
        if (damager instanceof LivingEntity livingEntity) {
            return livingEntity;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
        }
        return null;
    }

    private boolean isExplosiveRespawnBlock(Material material, org.bukkit.World world) {
        if (material == Material.RESPAWN_ANCHOR) {
            return world == null || !world.isRespawnAnchorWorks();
        }
        if (material.name().endsWith("_BED")) {
            return world == null || !world.isBedWorks();
        }
        return false;
    }

    private void rememberCrystalOwner(UUID crystalId, UUID ownerId) {
        long now = System.currentTimeMillis();
        pruneCrystalOwners(now, crystalOwners.size() >= MAX_CRYSTAL_OWNERS);
        crystalOwners.put(
                crystalId,
                new ExplosionOwner(ownerId, now + CRYSTAL_OWNER_TTL_MILLIS)
        );
        trimCrystalOwners();
    }

    private UUID findCrystalOwner(UUID crystalId) {
        long now = System.currentTimeMillis();
        pruneCrystalOwners(now, false);
        ExplosionOwner owner = crystalOwners.get(crystalId);
        if (owner == null) {
            return null;
        }
        if (owner.expiresAtMillis() <= now) {
            crystalOwners.remove(crystalId);
            return null;
        }
        return owner.ownerId();
    }

    private void pruneCrystalOwners(long now, boolean force) {
        if (!force && now - lastCrystalOwnerPruneMillis < OWNER_PRUNE_INTERVAL_MILLIS) {
            return;
        }
        lastCrystalOwnerPruneMillis = now;
        crystalOwners.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    private void trimCrystalOwners() {
        int overflow = crystalOwners.size() - MAX_CRYSTAL_OWNERS;
        if (overflow <= 0) {
            return;
        }
        crystalOwners.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().expiresAtMillis()))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(crystalOwners::remove);
    }

    private void recordBlockExplosionOwner(Player player, Location location) {
        synchronized (blockExplosionOwners) {
            pruneBlockExplosionOwners();
            blockExplosionOwners.add(new BlockExplosionOwner(
                    player.getUniqueId(),
                    location.clone(),
                    System.currentTimeMillis() + BLOCK_EXPLOSION_OWNER_TTL_MILLIS
            ));
            trimBlockExplosionOwners();
        }
    }

    private UUID findRecentBlockExplosionOwner(Location sourceLocation, Location victimLocation) {
        Location matchLocation = sourceLocation == null ? victimLocation : sourceLocation;
        if (matchLocation == null || matchLocation.getWorld() == null) {
            return null;
        }

        synchronized (blockExplosionOwners) {
            pruneBlockExplosionOwners();
            BlockExplosionOwner best = null;
            double bestDistance = Double.MAX_VALUE;
            for (BlockExplosionOwner owner : blockExplosionOwners) {
                if (owner.location().getWorld() != matchLocation.getWorld()) {
                    continue;
                }
                double distance = owner.location().distanceSquared(matchLocation);
                if (distance > BLOCK_EXPLOSION_MATCH_DISTANCE_SQUARED || distance >= bestDistance) {
                    continue;
                }
                best = owner;
                bestDistance = distance;
            }
            return best == null ? null : best.ownerId();
        }
    }

    private void pruneBlockExplosionOwners() {
        long now = System.currentTimeMillis();
        Iterator<BlockExplosionOwner> iterator = blockExplosionOwners.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAtMillis() <= now) {
                iterator.remove();
            }
        }
    }

    private void trimBlockExplosionOwners() {
        int overflow = blockExplosionOwners.size() - MAX_BLOCK_EXPLOSION_OWNERS;
        if (overflow > 0) {
            blockExplosionOwners.subList(0, overflow).clear();
        }
    }

    private void notifyBlocked(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            notifyBlocked(player);
        }
    }

    private void notifyBlocked(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastSent = blockedMessageCooldowns.get(player.getUniqueId());
        if (lastSent != null && now - lastSent < BLOCKED_MESSAGE_COOLDOWN_MILLIS) {
            return;
        }

        blockedMessageCooldowns.put(player.getUniqueId(), now);
        messages.send(player, "damage-blocked", "{prefix}{bad}Damage blocked because /pvp is disabled.");
    }

    private record BlockExplosionOwner(UUID ownerId, Location location, long expiresAtMillis) {
    }

    private record ExplosionOwner(UUID ownerId, long expiresAtMillis) {
    }
}
