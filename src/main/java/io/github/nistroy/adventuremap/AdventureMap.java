package io.github.nistroy.adventuremap;

import io.github.nistroy.adventuremap.map.MapDefinition;
import io.github.nistroy.adventuremap.network.ClaimPayload;
import io.github.nistroy.adventuremap.network.MapStatePayload;
import io.github.nistroy.adventuremap.server.MapCommand;
import io.github.nistroy.adventuremap.server.MapService;
import io.github.nistroy.adventuremap.server.PlayerRecord;
import io.github.nistroy.adventuremap.server.Tracker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Point d'entrée commun : objet, données par joueur, réseau, suivi et commande. L'écran est côté client. */
public final class AdventureMap implements ModInitializer {
    public static final String MOD_ID = "adventuremap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final ResourceKey<CreativeModeTab> TOOLS_AND_UTILITIES =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("tools_and_utilities"));

    public static final Item ADVENTURER_MAP = Registry.register(BuiltInRegistries.ITEM, id("adventurer_map"),
            new AdventureMapItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Copié à la mort : la progression survit, même si la carte reste dans la tombe. */
    public static final AttachmentType<PlayerRecord> PLAYER_RECORD = AttachmentRegistry.<PlayerRecord>builder()
            .persistent(PlayerRecord.CODEC)
            .copyOnDeath()
            .buildAndRegister(id("player_record"));

    private static MapService service;

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static MapService service() {
        return service;
    }

    @Override
    public void onInitialize() {
        service = new MapService(loadMap());
        Tracker tracker = new Tracker(service);

        PayloadTypeRegistry.playS2C().register(MapStatePayload.TYPE, MapStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClaimPayload.TYPE, ClaimPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ClaimPayload.TYPE,
                (payload, context) -> service.claim(context.player(), payload.nodeId()));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!MapService.record(handler.getPlayer()).mapGiven()) service.giveMap(handler.getPlayer());
        });
        ServerTickEvents.END_SERVER_TICK.register(tracker::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> tracker.onDeath(entity));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> MapCheck.warnUnknownIds(service.map(), server));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, selection) -> MapCommand.register(dispatcher, service));
        ItemGroupEvents.modifyEntriesEvent(TOOLS_AND_UTILITIES).register(entries -> entries.accept(ADVENTURER_MAP));
    }

    /** {@code config/adventuremap/map.json} remplace la carte du jar : ajuster objectifs et récompenses sans nouvelle version. */
    private static MapDefinition loadMap() {
        Path override = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("map.json");
        if (Files.isRegularFile(override)) {
            try {
                MapDefinition map = MapDefinition.parse(Files.readString(override, StandardCharsets.UTF_8));
                LOGGER.info("Carte lue dans {}", override);
                return map;
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Carte {} illisible, carte par défaut utilisée", override, e);
            }
        }
        return MapDefinition.bundled();
    }
}
