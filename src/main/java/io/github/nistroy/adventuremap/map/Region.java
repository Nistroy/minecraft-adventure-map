package io.github.nistroy.adventuremap.map;

import java.util.List;

/**
 * Une région de la carte du monde. {@code hiddenUntilStarted} : dans le brouillard tant que le
 * joueur n'a réussi aucun de ses objectifs. Position et rayon dans le repère 1000 × 600 du monde ;
 * {@code height} = hauteur du repère de la région, plus de 600 quand ses routes défilent.
 */
public record Region(String id, String name, String subtitle, String description, int x, int y, int radius,
        int color, boolean hiddenUntilStarted, int height, List<MapNode> nodes) {
    public Region {
        nodes = List.copyOf(nodes);
    }
}
