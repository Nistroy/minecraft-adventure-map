package io.github.nistroy.adventuremap.client;

import io.github.nistroy.adventuremap.map.MapDefinition;
import io.github.nistroy.adventuremap.network.MapStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class AdventureMapClient implements ClientModInitializer {
    private static String lastJson;
    private static MapDefinition lastMap;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MapStatePayload.TYPE, (payload, context) -> {
            MapDefinition map = parse(payload.mapJson());
            if (context.client().screen instanceof AdventureMapScreen screen) {
                screen.update(map, payload.done(), payload.claimed());
            } else if (payload.open()) {
                context.client().setScreen(new AdventureMapScreen(map, payload.done(), payload.claimed()));
            }
        });
    }

    /** La carte est renvoyée à chaque ouverture : on ne la relit que si elle a changé. */
    private static MapDefinition parse(String json) {
        if (!json.equals(lastJson)) {
            lastMap = MapDefinition.parse(json);
            lastJson = json;
        }
        return lastMap;
    }
}
