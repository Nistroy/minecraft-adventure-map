package io.github.nistroy.adventuremap.map;

import java.util.HashSet;
import java.util.Set;

/**
 * Où en est un joueur : objectifs réussis, récompenses réclamées, et ce que la carte en montre.
 * Le serveur l'évalue ({@link #evaluate}), le client le reconstruit à partir des deux ensembles reçus ({@link #of}).
 */
public final class Progress {
    public enum RegionState { HIDDEN, OPEN, DONE }

    private final MapDefinition map;
    private final Set<String> done;
    private final Set<String> claimed;

    private Progress(MapDefinition map, Set<String> done, Set<String> claimed) {
        this.map = map;
        this.done = Set.copyOf(done);
        Set<String> kept = new HashSet<>(claimed);
        kept.removeIf(id -> map.node(id) == null);
        this.claimed = Set.copyOf(kept);
    }

    public static Progress evaluate(MapDefinition map, PlayerFacts facts, Set<String> claimed) {
        // Point fixe : une épreuve finale dépend d'autres objectifs, dans n'importe quel ordre du fichier.
        Set<String> done = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (MapNode node : map.nodes()) {
                if (!done.contains(node.id()) && node.requires().stream().anyMatch(r -> r.isMet(facts, done))) {
                    done.add(node.id());
                    changed = true;
                }
            }
        }
        return new Progress(map, done, claimed);
    }

    public static Progress of(MapDefinition map, Set<String> done, Set<String> claimed) {
        Set<String> known = new HashSet<>(done);
        known.removeIf(id -> map.node(id) == null);
        return new Progress(map, known, claimed);
    }

    public Set<String> done() {
        return done;
    }

    public Set<String> claimedIds() {
        return claimed;
    }

    public boolean isDone(String id) {
        return done.contains(id);
    }

    /** Un sceau gagné se voit toujours ; sinon il faut l'entrée de région ou une étape d'avant réussie. */
    public boolean visible(String id) {
        MapNode node = map.node(id);
        return done.contains(id) || node.after().isEmpty() || node.after().stream().anyMatch(done::contains);
    }

    public boolean available(String id) {
        MapNode node = map.node(id);
        return !done.contains(id) && done.containsAll(node.after());
    }

    public boolean claimed(String id) {
        return claimed.contains(id);
    }

    public boolean claimable(String id) {
        return done.contains(id) && !claimed.contains(id);
    }

    public int unclaimedCount() {
        return (int) done.stream().filter(id -> !claimed.contains(id)).count();
    }

    public int doneCount() {
        return done.size();
    }

    public int totalCount() {
        int total = 0;
        for (MapNode ignored : map.nodes()) total++;
        return total;
    }

    public RegionState regionState(String regionId) {
        Region region = map.region(regionId);
        long count = region.nodes().stream().filter(n -> done.contains(n.id())).count();
        if (count == region.nodes().size()) return RegionState.DONE;
        if (region.hiddenUntilStarted() && count == 0) return RegionState.HIDDEN;
        return RegionState.OPEN;
    }
}
