package io.github.nistroy.adventuremap.server;

import io.github.nistroy.adventuremap.map.MapDefinition;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Repère ce que les stats vanilla ne gardent pas : structures et dimensions visitées (une fois par
 * seconde), boss tombés à côté du joueur.
 */
public final class Tracker {
    private static final int SCAN_TICKS = 20;
    private static final int ANNOUNCE_TICKS = 100;
    /** Rayon d'un combat de boss à plusieurs : tout le groupe présent gagne le sceau. */
    private static final double WITNESS_RADIUS = 64;

    private final MapService service;
    private final Set<String> structures;
    private final Set<String> kills;
    private int ticks;

    public Tracker(MapService service) {
        this.service = service;
        MapDefinition map = service.map();
        this.structures = map.watchedStructures();
        this.kills = map.watchedKills();
    }

    public void tick(MinecraftServer server) {
        ticks++;
        if (ticks % SCAN_TICKS == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) scan(player);
        }
        if (ticks % ANNOUNCE_TICKS == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) service.announce(player);
        }
    }

    private void scan(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        String dimension = level.dimension().location().toString();
        MapService.update(player, r -> r.withDimension(dimension));

        BlockPos pos = player.blockPosition();
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        // Seules les structures qui touchent ce chunk : on ne teste pas les ~100 structures suivies à chaque fois.
        for (Structure structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
            ResourceLocation id = registry.getKey(structure);
            if (id == null || !structures.contains(id.toString())) continue;
            if (MapService.record(player).visited().contains(id.toString())) continue;
            if (level.structureManager().getStructureWithPieceAt(pos, structure).isValid()) {
                MapService.update(player, r -> r.withVisited(id.toString()));
            }
        }
    }

    public void onDeath(LivingEntity entity) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (!kills.contains(id) || !(entity.level() instanceof ServerLevel level)) return;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(entity) <= WITNESS_RADIUS * WITNESS_RADIUS) {
                MapService.update(player, r -> r.withWitnessed(id));
            }
        }
    }
}
