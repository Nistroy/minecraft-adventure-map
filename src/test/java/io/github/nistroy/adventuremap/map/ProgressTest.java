package io.github.nistroy.adventuremap.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProgressTest {
    /** Chaîne a → b → d et a → c → d, d = « terminer b et c ». Région cachée tant que rien n'est fait. */
    private static final MapDefinition MAP = MapDefinition.parse("""
            {"regions": [
              {"id": "r", "name": "R", "subtitle": "", "description": "", "x": 1, "y": 1, "radius": 1, "color": "#000000",
               "nodes": [
                {"id": "a", "label": "A", "kind": "", "objective": "", "x": 1, "y": 1, "requires": [{"advancement": "m:a"}], "rewards": []},
                {"id": "b", "label": "B", "kind": "", "objective": "", "x": 1, "y": 1, "after": ["a"],
                 "requires": [{"killed": "m:boss"}, {"advancement": "m:boss"}], "rewards": []},
                {"id": "c", "label": "C", "kind": "", "objective": "", "x": 1, "y": 1, "after": ["a"],
                 "requires": [{"visited": ["s:1", "s:2", "s:3"], "count": 2}], "rewards": []},
                {"id": "d", "label": "D", "kind": "", "objective": "", "x": 1, "y": 1, "after": ["b", "c"],
                 "requires": [{"nodes": ["b", "c"]}], "rewards": []}
               ]},
              {"id": "sky", "name": "S", "subtitle": "", "description": "", "x": 1, "y": 1, "radius": 1, "color": "#000000",
               "hiddenUntilStarted": true,
               "nodes": [
                {"id": "s", "label": "S", "kind": "", "objective": "", "x": 1, "y": 1,
                 "requires": [{"dimension": "m:sky"}, {"picked_up": "m:feather"}, {"used": "m:stone"}, {"stat": "m:raid"},
                              {"crafted": "m:boat"}], "rewards": []}
               ]}
            ]}
            """);

    private static final class Facts implements PlayerFacts {
        final Set<String> advancements = new HashSet<>();
        final Map<String, Integer> kills = new HashMap<>();
        final Set<String> witnessed = new HashSet<>();
        final Set<String> visited = new HashSet<>();
        final Set<String> dimensions = new HashSet<>();
        final Map<String, Integer> pickedUp = new HashMap<>();
        final Map<String, Integer> used = new HashMap<>();
        final Map<String, Integer> stats = new HashMap<>();
        final Map<String, Integer> crafted = new HashMap<>();

        @Override public boolean hasAdvancement(String id) { return advancements.contains(id); }
        @Override public int killed(String entity) { return kills.getOrDefault(entity, 0); }
        @Override public boolean witnessedKill(String entity) { return witnessed.contains(entity); }
        @Override public boolean visited(String structure) { return visited.contains(structure); }
        @Override public boolean enteredDimension(String dimension) { return dimensions.contains(dimension); }
        @Override public int pickedUp(String item) { return pickedUp.getOrDefault(item, 0); }
        @Override public int used(String item) { return used.getOrDefault(item, 0); }
        @Override public int customStat(String stat) { return stats.getOrDefault(stat, 0); }
        @Override public int crafted(String item) { return crafted.getOrDefault(item, 0); }
    }

    @Test
    void nothingDoneShowsOnlyTheEntrance() {
        Progress progress = Progress.evaluate(MAP, new Facts(), Set.of());
        assertEquals(Set.of(), progress.done());
        assertTrue(progress.visible("a"));
        assertTrue(progress.available("a"));
        assertFalse(progress.visible("b"));
        assertFalse(progress.available("b"));
        assertEquals(Progress.RegionState.OPEN, progress.regionState("r"));
        assertEquals(Progress.RegionState.HIDDEN, progress.regionState("sky"));
    }

    @Test
    void doingAStepLiftsTheFogOnItsChildren() {
        Facts facts = new Facts();
        facts.advancements.add("m:a");
        Progress progress = Progress.evaluate(MAP, facts, Set.of());
        assertEquals(Set.of("a"), progress.done());
        assertTrue(progress.visible("b") && progress.available("b"));
        assertTrue(progress.visible("c") && progress.available("c"));
        assertFalse(progress.visible("d"));
    }

    @Test
    void aJoinIsVisibleFromOneParentButOnlyAvailableWithAll() {
        Facts facts = new Facts();
        facts.advancements.add("m:a");
        facts.kills.put("m:boss", 1);
        Progress progress = Progress.evaluate(MAP, facts, Set.of());
        assertTrue(progress.visible("d"));
        assertFalse(progress.available("d"));
    }

    @Test
    void anyAlternativeCompletesAStep() {
        Facts byWitness = new Facts();
        byWitness.witnessed.add("m:boss");
        assertTrue(Progress.evaluate(MAP, byWitness, Set.of()).done().contains("b"));
        Facts byAdvancement = new Facts();
        byAdvancement.advancements.add("m:boss");
        assertTrue(Progress.evaluate(MAP, byAdvancement, Set.of()).done().contains("b"));
    }

    @Test
    void stepsCountEvenOutOfOrderSoPastFeatsAreKept() {
        Facts facts = new Facts();
        facts.kills.put("m:boss", 2);
        Progress progress = Progress.evaluate(MAP, facts, Set.of());
        assertEquals(Set.of("b"), progress.done());
        assertTrue(progress.visible("b"), "un sceau déjà gagné n'est jamais caché");
    }

    @Test
    void visitedNeedsEnoughDistinctStructures() {
        Facts facts = new Facts();
        facts.visited.add("s:1");
        assertFalse(Progress.evaluate(MAP, facts, Set.of()).done().contains("c"));
        facts.visited.add("s:3");
        assertTrue(Progress.evaluate(MAP, facts, Set.of()).done().contains("c"));
    }

    @Test
    void joinsCompleteOnceEveryListedStepIsDone() {
        Facts facts = new Facts();
        facts.advancements.add("m:a");
        facts.kills.put("m:boss", 1);
        facts.visited.addAll(Set.of("s:1", "s:2"));
        Progress progress = Progress.evaluate(MAP, facts, Set.of());
        assertEquals(Set.of("a", "b", "c", "d"), progress.done());
        assertEquals(Progress.RegionState.DONE, progress.regionState("r"));
    }

    @Test
    void everyStatKindCounts() {
        for (int kind = 0; kind < 5; kind++) {
            Facts facts = new Facts();
            switch (kind) {
                case 0 -> facts.dimensions.add("m:sky");
                case 1 -> facts.pickedUp.put("m:feather", 1);
                case 2 -> facts.used.put("m:stone", 1);
                case 3 -> facts.stats.put("m:raid", 1);
                default -> facts.crafted.put("m:boat", 1);
            }
            Progress progress = Progress.evaluate(MAP, facts, Set.of());
            assertTrue(progress.done().contains("s"), "cas " + kind);
            assertEquals(Progress.RegionState.DONE, progress.regionState("sky"));
        }
    }

    @Test
    void rewardsAreClaimableOnceWhenDone() {
        Facts facts = new Facts();
        facts.advancements.add("m:a");
        Progress progress = Progress.evaluate(MAP, facts, Set.of());
        assertTrue(progress.claimable("a"));
        assertFalse(progress.claimable("b"));
        assertEquals(1, progress.unclaimedCount());

        Progress claimed = Progress.evaluate(MAP, facts, Set.of("a"));
        assertFalse(claimed.claimable("a"));
        assertTrue(claimed.claimed("a"));
        assertEquals(0, claimed.unclaimedCount());
    }

    @Test
    void theClientRebuildsTheSameStateFromTheSyncedSets() {
        Progress synced = Progress.of(MAP, Set.of("a"), Set.of());
        assertTrue(synced.available("b"));
        assertTrue(synced.claimable("a"));
        assertEquals(1, synced.doneCount());
        assertEquals(5, synced.totalCount());
    }

    @Test
    void theToDoListHoldsAvailableStepsInMapOrder() {
        assertEquals(List.of("a"), Progress.of(MAP, Set.of(), Set.of()).todo());
        assertEquals(List.of("b", "c"), Progress.of(MAP, Set.of("a"), Set.of()).todo());
        assertEquals(List.of("c"), Progress.of(MAP, Set.of("a", "b"), Set.of()).todo());
        assertEquals(List.of("d"), Progress.of(MAP, Set.of("a", "b", "c"), Set.of()).todo());
    }

    @Test
    void theToDoListKeepsHiddenRegionsSecretUntilStarted() {
        assertFalse(Progress.of(MAP, Set.of(), Set.of()).todo().contains("s"));
    }

    @Test
    void theToDoListCanBeNarrowedToOneRegion() {
        Progress progress = Progress.of(MAP, Set.of("a"), Set.of());
        assertEquals(List.of("b", "c"), progress.todo("r"));
        assertEquals(List.of(), progress.todo("sky"));
    }

    @Test
    void unclaimedRewardsAreListedInMapOrder() {
        assertEquals(List.of("a", "c"), Progress.of(MAP, Set.of("c", "a", "b"), Set.of("b")).unclaimed());
    }

    @Test
    void unknownIdsInSyncedSetsAreIgnored() {
        Progress synced = Progress.of(MAP, Set.of("a", "old"), Set.of("gone"));
        assertEquals(Set.of("a"), synced.done());
        assertFalse(synced.claimed("gone"));
    }
}
