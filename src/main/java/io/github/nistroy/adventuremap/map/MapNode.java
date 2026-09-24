package io.github.nistroy.adventuremap.map;

import java.util.List;

/**
 * Un ✕ de la carte. {@code after} = les étapes d'où part son chemin (brouillard levé dès qu'une est
 * réussie) ; {@code requires} = les façons de le réussir. Position dans le repère 1000 × 600 de la région.
 */
public record MapNode(String id, String label, String kind, String objective, int x, int y,
        List<String> after, List<Requirement> requires, List<Reward> rewards) {
    public MapNode {
        after = List.copyOf(after);
        requires = List.copyOf(requires);
        rewards = List.copyOf(rewards);
    }
}
