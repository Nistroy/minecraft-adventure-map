package io.github.nistroy.adventuremap.map;

/** Ce que le serveur sait d'un joueur. Identifiants au format {@code namespace:chemin}. */
public interface PlayerFacts {
    boolean hasAdvancement(String id);

    int killed(String entity);

    boolean witnessedKill(String entity);

    boolean visited(String structure);

    boolean enteredDimension(String dimension);

    int pickedUp(String item);

    int used(String item);

    int customStat(String stat);

    int crafted(String item);
}
