package io.github.nistroy.adventuremap;

import io.github.nistroy.adventuremap.map.MapDefinition;
import io.github.nistroy.adventuremap.map.MapNode;
import io.github.nistroy.adventuremap.map.Requirement;
import io.github.nistroy.adventuremap.map.Reward;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Au démarrage : liste les identifiants de la carte que ce serveur ne connaît pas (mod absent,
 * faute de frappe). Un objectif qui en dépend ne se validera que par ses autres conditions.
 */
final class MapCheck {
    private MapCheck() {}

    static void warnUnknownIds(MapDefinition map, MinecraftServer server) {
        var registries = server.registryAccess();
        var structures = registries.registryOrThrow(Registries.STRUCTURE);
        var enchantments = registries.registryOrThrow(Registries.ENCHANTMENT);
        Set<String> dimensions = new TreeSet<>();
        server.levelKeys().forEach(key -> dimensions.add(key.location().toString()));

        Set<String> unknown = new TreeSet<>();
        for (MapNode node : map.nodes()) {
            for (Requirement requirement : node.requires()) {
                switch (requirement) {
                    case Requirement.Advancement a -> check(unknown, "progrès", a.id(), id -> server.getAdvancements().get(id) != null);
                    case Requirement.Killed k -> check(unknown, "entité", k.entity(), BuiltInRegistries.ENTITY_TYPE::containsKey);
                    case Requirement.Visited v -> v.structures().forEach(s -> check(unknown, "structure", s, structures::containsKey));
                    case Requirement.Dimension d -> check(unknown, "dimension", d.id(), id -> dimensions.contains(id.toString()));
                    case Requirement.PickedUp p -> check(unknown, "objet", p.item(), BuiltInRegistries.ITEM::containsKey);
                    case Requirement.Used u -> check(unknown, "objet", u.item(), BuiltInRegistries.ITEM::containsKey);
                    case Requirement.CustomStat c -> check(unknown, "stat", c.id(), BuiltInRegistries.CUSTOM_STAT::containsKey);
                    case Requirement.Nodes ignored -> { }
                }
            }
            for (Reward reward : node.rewards()) {
                check(unknown, "récompense", reward.item(), BuiltInRegistries.ITEM::containsKey);
                reward.enchantments().keySet().forEach(e -> check(unknown, "enchantement", e, enchantments::containsKey));
            }
        }
        if (unknown.isEmpty()) {
            AdventureMap.LOGGER.info("Carte de l'aventurier : tous les identifiants sont connus");
        } else {
            AdventureMap.LOGGER.warn("Carte de l'aventurier : {} identifiant(s) inconnu(s) : {}", unknown.size(), unknown);
        }
    }

    private static void check(Set<String> unknown, String kind, String id, Predicate<ResourceLocation> exists) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !exists.test(location)) unknown.add(kind + " " + id);
    }
}
