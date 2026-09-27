package io.github.nistroy.adventuremap.server;

import io.github.nistroy.adventuremap.map.PlayerFacts;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

/** Faits d'un joueur lus en direct : ses progrès et stats vanilla (donc son passé) + ce que le mod a noté. */
final class ServerFacts implements PlayerFacts {
    private final ServerPlayer player;
    private final PlayerRecord record;

    ServerFacts(ServerPlayer player, PlayerRecord record) {
        this.player = player;
        this.record = record;
    }

    @Override
    public boolean hasAdvancement(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return false;
        AdvancementHolder holder = player.server.getAdvancements().get(location);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    // Les registres d'objets et d'entités ont une valeur par défaut (air, cochon) : getOptional évite
    // qu'un identifiant de mod absent compte les cochons tués.
    @Override
    public int killed(String entity) {
        return location(entity)
                .flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional)
                .map(type -> player.getStats().getValue(Stats.ENTITY_KILLED, type))
                .orElse(0);
    }

    @Override
    public boolean witnessedKill(String entity) {
        return record.witnessed().contains(entity);
    }

    @Override
    public boolean visited(String structure) {
        return record.visited().contains(structure);
    }

    @Override
    public boolean enteredDimension(String dimension) {
        return record.dimensions().contains(dimension);
    }

    @Override
    public int pickedUp(String item) {
        return location(item)
                .flatMap(BuiltInRegistries.ITEM::getOptional)
                .map(value -> player.getStats().getValue(Stats.ITEM_PICKED_UP, value))
                .orElse(0);
    }

    @Override
    public int used(String item) {
        return location(item)
                .flatMap(BuiltInRegistries.ITEM::getOptional)
                .map(value -> player.getStats().getValue(Stats.ITEM_USED, value))
                .orElse(0);
    }

    @Override
    public int crafted(String item) {
        return location(item)
                .flatMap(BuiltInRegistries.ITEM::getOptional)
                .map(value -> player.getStats().getValue(Stats.ITEM_CRAFTED, value))
                .orElse(0);
    }

    // StatType garde ses stats par identité : il faut l'instance enregistrée, pas un ResourceLocation égal.
    @Override
    public int customStat(String stat) {
        return location(stat)
                .flatMap(BuiltInRegistries.CUSTOM_STAT::getOptional)
                .map(registered -> player.getStats().getValue(Stats.CUSTOM, registered))
                .orElse(0);
    }

    private static java.util.Optional<ResourceLocation> location(String id) {
        return java.util.Optional.ofNullable(ResourceLocation.tryParse(id));
    }
}
