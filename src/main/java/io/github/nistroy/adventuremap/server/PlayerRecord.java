package io.github.nistroy.adventuremap.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ce que le mod retient d'un joueur, en plus de ses stats et progrès vanilla : structures et
 * dimensions visitées, boss vus tomber, récompenses réclamées, sceaux déjà annoncés. Immuable :
 * chaque changement passe par {@code setAttached}, qui marque le joueur à sauvegarder.
 */
public record PlayerRecord(Set<String> visited, Set<String> dimensions, Set<String> witnessed, Set<String> claimed,
        Set<String> announced, boolean initialized, boolean mapGiven) {
    public static final PlayerRecord EMPTY = new PlayerRecord(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false);

    private static final Codec<Set<String>> IDS = Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf);

    public static final Codec<PlayerRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            IDS.optionalFieldOf("visited", Set.of()).forGetter(PlayerRecord::visited),
            IDS.optionalFieldOf("dimensions", Set.of()).forGetter(PlayerRecord::dimensions),
            IDS.optionalFieldOf("witnessed", Set.of()).forGetter(PlayerRecord::witnessed),
            IDS.optionalFieldOf("claimed", Set.of()).forGetter(PlayerRecord::claimed),
            IDS.optionalFieldOf("announced", Set.of()).forGetter(PlayerRecord::announced),
            Codec.BOOL.optionalFieldOf("initialized", false).forGetter(PlayerRecord::initialized),
            Codec.BOOL.optionalFieldOf("map_given", false).forGetter(PlayerRecord::mapGiven)
    ).apply(i, PlayerRecord::new));

    public PlayerRecord {
        visited = Set.copyOf(visited);
        dimensions = Set.copyOf(dimensions);
        witnessed = Set.copyOf(witnessed);
        claimed = Set.copyOf(claimed);
        announced = Set.copyOf(announced);
    }

    public PlayerRecord withVisited(String structure) {
        return visited.contains(structure) ? this
                : new PlayerRecord(plus(visited, structure), dimensions, witnessed, claimed, announced, initialized, mapGiven);
    }

    public PlayerRecord withDimension(String dimension) {
        return dimensions.contains(dimension) ? this
                : new PlayerRecord(visited, plus(dimensions, dimension), witnessed, claimed, announced, initialized, mapGiven);
    }

    public PlayerRecord withWitnessed(String entity) {
        return witnessed.contains(entity) ? this
                : new PlayerRecord(visited, dimensions, plus(witnessed, entity), claimed, announced, initialized, mapGiven);
    }

    public PlayerRecord withClaimed(String node) {
        return claimed.contains(node) ? this
                : new PlayerRecord(visited, dimensions, witnessed, plus(claimed, node), announced, initialized, mapGiven);
    }

    /** Sceaux déjà signalés au joueur ; le premier appel marque le joueur comme initialisé (rattrapage muet). */
    public PlayerRecord withAnnounced(Set<String> nodes) {
        return initialized && announced.containsAll(nodes) ? this
                : new PlayerRecord(visited, dimensions, witnessed, claimed, union(announced, nodes), true, mapGiven);
    }

    public PlayerRecord withMapGiven() {
        return mapGiven ? this : new PlayerRecord(visited, dimensions, witnessed, claimed, announced, initialized, true);
    }

    private static Set<String> plus(Set<String> set, String id) {
        return union(set, Set.of(id));
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.addAll(b);
        return result;
    }
}
