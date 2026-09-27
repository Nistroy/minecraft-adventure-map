package io.github.nistroy.adventuremap.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La carte entière : régions, objectifs, récompenses. Lue en JSON côté serveur puis envoyée telle
 * quelle au client, qui n'a donc pas besoin de la même version du fichier.
 */
public final class MapDefinition {
    /** Repère commun de la carte du monde et des cartes de région. */
    public static final int WIDTH = 1000;
    public static final int HEIGHT = 600;

    private static final String BUNDLED = "/adventuremap/default_map.json";

    private final String json;
    private final List<Region> regions;
    private final Map<String, MapNode> nodes = new LinkedHashMap<>();
    private final Map<String, Region> regionOfNode = new LinkedHashMap<>();

    private MapDefinition(String json, List<Region> regions) {
        this.json = json;
        this.regions = List.copyOf(regions);
        for (Region region : this.regions) {
            for (MapNode node : region.nodes()) {
                if (nodes.put(node.id(), node) != null) {
                    throw new IllegalArgumentException("objectif en double : " + node.id());
                }
                regionOfNode.put(node.id(), region);
            }
        }
        for (Region region : this.regions) {
            for (MapNode node : region.nodes()) {
                if (node.x() <= 0 || node.x() >= WIDTH || node.y() <= 0 || node.y() >= region.height()) {
                    throw new IllegalArgumentException("objectif hors de sa région : " + node.id());
                }
            }
        }
        for (MapNode node : nodes.values()) {
            if (node.requires().isEmpty()) {
                throw new IllegalArgumentException("objectif sans condition : " + node.id());
            }
            for (String parent : node.after()) requireNode(node, parent);
            for (Requirement requirement : node.requires()) {
                if (requirement instanceof Requirement.Nodes join) join.ids().forEach(id -> requireNode(node, id));
            }
        }
    }

    private void requireNode(MapNode from, String id) {
        if (!nodes.containsKey(id)) {
            throw new IllegalArgumentException(from.id() + " renvoie à un objectif inconnu : " + id);
        }
    }

    public static MapDefinition parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<Region> regions = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("regions")) {
            regions.add(parseRegion(element.getAsJsonObject()));
        }
        return new MapDefinition(json, regions);
    }

    public static MapDefinition bundled() {
        return parse(bundledJson());
    }

    public static String bundledJson() {
        try (InputStream in = MapDefinition.class.getResourceAsStream(BUNDLED)) {
            if (in == null) throw new IllegalStateException("carte absente du jar : " + BUNDLED);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Region parseRegion(JsonObject json) {
        List<MapNode> nodes = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("nodes")) {
            nodes.add(parseNode(element.getAsJsonObject()));
        }
        return new Region(
                json.get("id").getAsString(),
                json.get("name").getAsString(),
                json.get("subtitle").getAsString(),
                json.get("description").getAsString(),
                json.get("x").getAsInt(),
                json.get("y").getAsInt(),
                json.get("radius").getAsInt(),
                Integer.parseInt(json.get("color").getAsString().substring(1), 16),
                json.has("hiddenUntilStarted") && json.get("hiddenUntilStarted").getAsBoolean(),
                json.has("height") ? json.get("height").getAsInt() : HEIGHT,
                nodes);
    }

    private static MapNode parseNode(JsonObject json) {
        List<Requirement> requires = new ArrayList<>();
        json.getAsJsonArray("requires").forEach(e -> requires.add(Requirement.parse(e.getAsJsonObject())));
        List<Reward> rewards = new ArrayList<>();
        json.getAsJsonArray("rewards").forEach(e -> rewards.add(Reward.parse(e.getAsJsonObject())));
        return new MapNode(
                json.get("id").getAsString(),
                json.get("label").getAsString(),
                json.get("kind").getAsString(),
                json.get("objective").getAsString(),
                json.has("hint") ? json.get("hint").getAsString() : null,
                json.get("x").getAsInt(),
                json.get("y").getAsInt(),
                Requirement.strings(json.getAsJsonArray("after")),
                requires,
                rewards);
    }

    public String json() {
        return json;
    }

    public List<Region> regions() {
        return regions;
    }

    public Iterable<MapNode> nodes() {
        return nodes.values();
    }

    public MapNode node(String id) {
        return nodes.get(id);
    }

    public Region regionOf(String nodeId) {
        return regionOfNode.get(nodeId);
    }

    public Region region(String id) {
        return regions.stream().filter(r -> r.id().equals(id)).findFirst().orElse(null);
    }

    /** Structures que le serveur doit repérer autour des joueurs. */
    public Set<String> watchedStructures() {
        Set<String> ids = new HashSet<>();
        for (MapNode node : nodes.values()) {
            for (Requirement r : node.requires()) {
                if (r instanceof Requirement.Visited visited) ids.addAll(visited.structures());
            }
        }
        return ids;
    }

    /** Boss dont la mort compte pour tous les joueurs proches. */
    public Set<String> watchedKills() {
        Set<String> ids = new HashSet<>();
        for (MapNode node : nodes.values()) {
            for (Requirement r : node.requires()) {
                if (r instanceof Requirement.Killed killed) ids.add(killed.entity());
            }
        }
        return ids;
    }
}
