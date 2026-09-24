package io.github.nistroy.adventuremap.map;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Récompense d'un objectif. Avec un nom, c'est un objet unique (nom, histoire, enchantements). */
public record Reward(String item, int count, String name, List<String> lore, Map<String, Integer> enchantments) {
    public Reward {
        lore = List.copyOf(lore);
        enchantments = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(enchantments));
    }

    public boolean unique() {
        return name != null;
    }

    static Reward parse(JsonObject json) {
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        if (json.has("enchantments")) {
            json.getAsJsonObject("enchantments").entrySet()
                    .forEach(e -> enchantments.put(e.getKey(), e.getValue().getAsInt()));
        }
        return new Reward(
                json.get("item").getAsString(),
                json.has("count") ? json.get("count").getAsInt() : 1,
                json.has("name") ? json.get("name").getAsString() : null,
                Requirement.strings(json.getAsJsonArray("lore")),
                enchantments);
    }
}
