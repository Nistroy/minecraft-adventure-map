package io.github.nistroy.adventuremap.map;

import java.util.List;

/**
 * Un ✕ de la carte. {@code after} = les étapes d'où part son chemin (brouillard levé dès qu'une est
 * réussie) ; {@code requires} = les façons de le réussir ; {@code hint} = où chercher, {@code null} si
 * absent. Position dans le repère de la région (1000 de large, {@link Region#height()} de haut).
 */
public record MapNode(String id, String label, String kind, String objective, String hint, int x, int y,
        List<String> after, List<Requirement> requires, List<Reward> rewards) {
    public MapNode {
        after = List.copyOf(after);
        requires = List.copyOf(requires);
        rewards = List.copyOf(rewards);
    }
}
