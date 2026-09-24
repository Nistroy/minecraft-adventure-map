package io.github.nistroy.adventuremap.server;

import io.github.nistroy.adventuremap.AdventureMap;
import io.github.nistroy.adventuremap.Rewards;
import io.github.nistroy.adventuremap.map.MapDefinition;
import io.github.nistroy.adventuremap.map.MapNode;
import io.github.nistroy.adventuremap.map.Progress;
import io.github.nistroy.adventuremap.map.Reward;
import io.github.nistroy.adventuremap.network.MapStatePayload;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Avancement d'un joueur : l'évaluer, l'envoyer à son écran, lui remettre ses récompenses et sa carte. */
public final class MapService {
    private final MapDefinition map;

    public MapService(MapDefinition map) {
        this.map = map;
    }

    public MapDefinition map() {
        return map;
    }

    public static PlayerRecord record(ServerPlayer player) {
        return player.getAttachedOrElse(AdventureMap.PLAYER_RECORD, PlayerRecord.EMPTY);
    }

    public static void update(ServerPlayer player, UnaryOperator<PlayerRecord> change) {
        PlayerRecord before = record(player);
        PlayerRecord after = change.apply(before);
        if (after != before) player.setAttached(AdventureMap.PLAYER_RECORD, after);
    }

    public Progress progress(ServerPlayer player) {
        PlayerRecord record = record(player);
        return Progress.evaluate(map, new ServerFacts(player, record), record.claimed());
    }

    public void sendState(ServerPlayer player, boolean open) {
        Progress progress = progress(player);
        ServerPlayNetworking.send(player, new MapStatePayload(
                map.json(), List.copyOf(progress.done()), List.copyOf(progress.claimedIds()), open));
    }

    /** Refus silencieux si la demande ne tient pas (client modifié, double clic réseau) : l'état renvoyé corrige l'écran. */
    public void claim(ServerPlayer player, String nodeId) {
        MapNode node = map.node(nodeId);
        if (node != null && progress(player).claimable(nodeId)) {
            update(player, r -> r.withClaimed(nodeId));
            for (Reward reward : node.rewards()) {
                ItemStack stack = Rewards.toStack(reward, player.server.registryAccess());
                if (stack.isEmpty()) {
                    AdventureMap.LOGGER.warn("Récompense introuvable pour {} : {}", nodeId, reward.item());
                } else {
                    // Inventaire plein : le reste tombe aux pieds du joueur.
                    player.getInventory().placeItemBackInInventory(stack);
                }
            }
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.2F);
        }
        sendState(player, false);
    }

    public boolean hasMap(ServerPlayer player) {
        return player.getInventory().contains(stack -> stack.is(AdventureMap.ADVENTURER_MAP));
    }

    public void giveMap(ServerPlayer player) {
        player.getInventory().placeItemBackInInventory(new ItemStack(AdventureMap.ADVENTURER_MAP));
        update(player, PlayerRecord::withMapGiven);
    }

    /**
     * Signale les nouveaux sceaux. Au tout premier passage d'un joueur, tout ce qu'il avait déjà fait
     * est noté sans message : pas d'avalanche de notifications à l'installation du mod.
     */
    public void announce(ServerPlayer player) {
        PlayerRecord record = record(player);
        Set<String> done = progress(player).done();
        if (record.initialized()) {
            Set<String> fresh = new HashSet<>(done);
            fresh.removeAll(record.announced());
            for (String id : fresh) {
                player.sendSystemMessage(Component.literal("✦ Nouveau sceau sur ta carte : ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(map.node(id).label()).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" — récompense à réclamer.").withStyle(ChatFormatting.GRAY)));
            }
            if (!fresh.isEmpty()) {
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.0F);
            }
        }
        update(player, r -> r.withAnnounced(done));
    }
}
