package io.github.nistroy.adventuremap.network;

import io.github.nistroy.adventuremap.AdventureMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → serveur : réclamer la récompense d'un objectif. Le serveur revérifie tout. */
public record ClaimPayload(String nodeId) implements CustomPacketPayload {
    public static final Type<ClaimPayload> TYPE = new Type<>(AdventureMap.id("claim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClaimPayload::nodeId, ClaimPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
