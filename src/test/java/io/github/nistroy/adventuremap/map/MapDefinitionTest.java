package io.github.nistroy.adventuremap.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MapDefinitionTest {
    private static final String TINY = """
            {"regions": [{"id": "r", "name": "Région", "subtitle": "Sous-titre", "description": "Desc",
              "x": 10, "y": 20, "radius": 30, "color": "#bcc289", "hiddenUntilStarted": true, "height": 900,
              "nodes": [
                {"id": "a", "label": "A", "kind": "Étape", "objective": "Faire A", "hint": "Par là", "x": 1, "y": 2,
                 "requires": [{"advancement": "minecraft:story/root"}, {"killed": "minecraft:zombie"}],
                 "rewards": [{"item": "minecraft:diamond", "count": 3}]},
                {"id": "b", "label": "B", "kind": "Boss", "objective": "Faire B", "x": 3, "y": 4, "after": ["a"],
                 "requires": [{"visited": ["x:one", "x:two"], "count": 2}, {"dimension": "minecraft:the_nether"},
                              {"picked_up": "minecraft:elytra"}, {"used": "waystones:waystone"},
                              {"stat": "minecraft:raid_win"}, {"nodes": ["a"]}, {"crafted": "motorboat:motorboat"}],
                 "rewards": [{"item": "minecraft:diamond_sword", "name": "Lame", "lore": ["l1", "l2"],
                              "enchantments": {"minecraft:sharpness": 5, "minecraft:looting": 3}}]}
              ]}]}
            """;

    @Test
    void parsesRegionsNodesRequirementsAndRewards() {
        MapDefinition map = MapDefinition.parse(TINY);
        Region region = map.regions().get(0);
        assertEquals("r", region.id());
        assertEquals("Région", region.name());
        assertEquals(0xbcc289, region.color());
        assertTrue(region.hiddenUntilStarted());
        assertEquals(30, region.radius());
        assertEquals(900, region.height());

        MapNode a = map.node("a");
        assertEquals("Par là", a.hint());
        assertEquals(List.of(), a.after());
        assertEquals(List.of(new Requirement.Advancement("minecraft:story/root"), new Requirement.Killed("minecraft:zombie")), a.requires());
        assertEquals(new Reward("minecraft:diamond", 3, null, List.of(), Map.of()), a.rewards().get(0));

        MapNode b = map.node("b");
        assertNull(b.hint());
        assertEquals(List.of("a"), b.after());
        assertEquals(new Requirement.Visited(List.of("x:one", "x:two"), 2), b.requires().get(0));
        assertEquals(new Requirement.Dimension("minecraft:the_nether"), b.requires().get(1));
        assertEquals(new Requirement.PickedUp("minecraft:elytra"), b.requires().get(2));
        assertEquals(new Requirement.Used("waystones:waystone"), b.requires().get(3));
        assertEquals(new Requirement.CustomStat("minecraft:raid_win"), b.requires().get(4));
        assertEquals(new Requirement.Nodes(List.of("a")), b.requires().get(5));
        assertEquals(new Requirement.Crafted("motorboat:motorboat"), b.requires().get(6));
        Reward sword = b.rewards().get(0);
        assertEquals("Lame", sword.name());
        assertTrue(sword.unique());
        assertEquals(1, sword.count());
        assertEquals(List.of("l1", "l2"), sword.lore());
        assertEquals(List.of("minecraft:sharpness", "minecraft:looting"), List.copyOf(sword.enchantments().keySet()));
        assertNull(map.node("nope"));
        assertEquals(region, map.regionOf("b"));
    }

    @Test
    void regionHeightDefaultsToTheWorldMapHeight() {
        String json = TINY.replace(", \"height\": 900", "");
        assertEquals(MapDefinition.HEIGHT, MapDefinition.parse(json).regions().get(0).height());
    }

    @Test
    void rejectsNodesOutsideTheirRegion() {
        assertThrows(IllegalArgumentException.class, () -> MapDefinition.parse(TINY.replace("\"x\": 3, \"y\": 4", "\"x\": 3, \"y\": 901")));
        assertThrows(IllegalArgumentException.class, () -> MapDefinition.parse(TINY.replace("\"x\": 3, \"y\": 4", "\"x\": 1001, \"y\": 4")));
    }

    @Test
    void visitedCountDefaultsToOne() {
        String json = TINY.replace(", \"count\": 2", "");
        assertEquals(1, ((Requirement.Visited) MapDefinition.parse(json).node("b").requires().get(0)).count());
    }

    @Test
    void rejectsUnknownRequirement() {
        String json = TINY.replace("{\"killed\": \"minecraft:zombie\"}", "{\"teleported\": \"x\"}");
        assertThrows(IllegalArgumentException.class, () -> MapDefinition.parse(json));
    }

    @Test
    void rejectsLinksToMissingNodes() {
        assertThrows(IllegalArgumentException.class, () -> MapDefinition.parse(TINY.replace("\"after\": [\"a\"]", "\"after\": [\"zz\"]")));
        assertThrows(IllegalArgumentException.class, () -> MapDefinition.parse(TINY.replace("{\"nodes\": [\"a\"]}", "{\"nodes\": [\"zz\"]}")));
    }

    @Test
    void rejectsDuplicateIds() {
        assertThrows(IllegalArgumentException.class, () -> MapDefinition.parse(TINY.replace("\"id\": \"b\"", "\"id\": \"a\"")));
    }

    @Test
    void rejectsNodesWithoutRequirements() {
        assertThrows(IllegalArgumentException.class, () -> MapDefinition.parse(TINY.replaceFirst(
                "\"requires\": \\[\\{\"advancement\": \"minecraft:story/root\"}, \\{\"killed\": \"minecraft:zombie\"}]", "\"requires\": []")));
    }

    @Test
    void theBundledMapIsValid() {
        MapDefinition map = MapDefinition.bundled();
        assertEquals(List.of("surface", "mers", "profondeurs", "aether", "nether", "end", "crepuscule"),
                map.regions().stream().map(Region::id).toList());
        Set<String> ids = new HashSet<>();
        for (Region region : map.regions()) {
            assertTrue(region.x() >= 0 && region.x() <= MapDefinition.WIDTH, region.id());
            assertTrue(region.y() >= 0 && region.y() <= MapDefinition.HEIGHT, region.id());
            for (MapNode node : region.nodes()) {
                assertTrue(ids.add(node.id()), node.id());
                assertTrue(!node.rewards().isEmpty(), node.id());
                assertTrue(node.hint() != null && !node.hint().isBlank(), node.id() + " : indice « où chercher »");
                // Une branche ne sort pas de sa région : le double-clic n'ouvre qu'une région à la fois.
                node.after().forEach(parent -> assertEquals(region, map.regionOf(parent), node.id() + " <- " + parent));
            }
            assertEquals(1, region.nodes().stream().filter(n -> n.after().isEmpty()).count(), region.id() + " : une seule entrée");
        }
    }

    @Test
    void collectsEveryIdTheServerHasToWatch() {
        MapDefinition map = MapDefinition.parse(TINY);
        assertEquals(Set.of("x:one", "x:two"), map.watchedStructures());
        assertEquals(Set.of("minecraft:zombie"), map.watchedKills());
        assertInstanceOf(Requirement.Killed.class, map.node("a").requires().get(1));
    }
}
