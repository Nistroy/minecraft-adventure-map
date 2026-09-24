package io.github.nistroy.adventuremap.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;

/**
 * Une façon de réussir un objectif. Un objectif en liste plusieurs : une seule suffit. C'est ce qui
 * rattrape le passé d'un joueur (stats et progrès déjà gagnés avant l'arrivée du mod).
 */
public sealed interface Requirement {
    boolean isMet(PlayerFacts facts, Set<String> doneNodes);

    /** Progrès vanilla ou d'un mod, par joueur. */
    record Advancement(String id) implements Requirement {
        @Override
        public boolean isMet(PlayerFacts facts, Set<String> doneNodes) {
            return facts.hasAdvancement(id);
        }
    }

    /** Stat de mobs tués, ou présent à la mort du boss (le coup final ne compte pas pour les autres sinon). */
    record Killed(String entity) implements Requirement {
        @Override
        public boolean isMet(PlayerFacts facts, Set<String> doneNodes) {
            return facts.killed(entity) > 0 || facts.witnessedKill(entity);
        }
    }

    /** Entré dans {@code count} structures différentes de la liste. */
    record Visited(List<String> structures, int count) implements Requirement {
        public Visited {
            structures = List.copyOf(structures);
        }

        @Override
        public boolean isMet(PlayerFacts facts, Set<String> doneNodes) {
            return structures.stream().filter(facts::visited).count() >= count;
        }
    }

    record Dimension(String id) implements Requirement {
        @Override
        public boolean isMet(PlayerFacts facts, Set<String> doneNodes) {
            return facts.enteredDimension(id);
        }
    }

    record PickedUp(String item) implements Requirement {
        @Override
        public boolean isMet(PlayerFacts facts, Set<String> doneNodes) {
            return facts.pickedUp(item) > 0;
        }
    }

    /** Stat « utilisé » : pour un bloc, c'est le fait de l'avoir posé. */
    record Used(String item) implements Requirement {
        @Override
        public boolean isMet(PlayerFacts facts, Set<String> doneNodes) {
            return facts.used(item) > 0;
        }
    }

    /** Stat personnalisée vanilla, par ex. {@code minecraft:raid_win}. */
    record CustomStat(String id) implements Requirement {
        @Override
        public boolean isMet(PlayerFacts facts, Set<String> doneNodes) {
            return facts.customStat(id) > 0;
        }
    }

    /** Épreuve finale : toutes les étapes listées sont réussies. */
    record Nodes(List<String> ids) implements Requirement {
        public Nodes {
            ids = List.copyOf(ids);
        }

        @Override
        public boolean isMet(PlayerFacts facts, Set<String> doneNodes) {
            return doneNodes.containsAll(ids);
        }
    }

    static Requirement parse(JsonObject json) {
        if (json.has("advancement")) return new Advancement(json.get("advancement").getAsString());
        if (json.has("killed")) return new Killed(json.get("killed").getAsString());
        if (json.has("visited")) {
            int count = json.has("count") ? json.get("count").getAsInt() : 1;
            return new Visited(strings(json.getAsJsonArray("visited")), count);
        }
        if (json.has("dimension")) return new Dimension(json.get("dimension").getAsString());
        if (json.has("picked_up")) return new PickedUp(json.get("picked_up").getAsString());
        if (json.has("used")) return new Used(json.get("used").getAsString());
        if (json.has("stat")) return new CustomStat(json.get("stat").getAsString());
        if (json.has("nodes")) return new Nodes(strings(json.getAsJsonArray("nodes")));
        throw new IllegalArgumentException("condition inconnue : " + json);
    }

    static List<String> strings(JsonArray array) {
        return array == null ? List.of() : array.asList().stream().map(e -> e.getAsString()).toList();
    }
}
