package io.github.nistroy.adventuremap.network;

import io.github.nistroy.adventuremap.AdventureMap;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : la carte (JSON) et l'avancement du joueur. {@code open} = ouvrir l'écran
 * (clic droit) ; sinon simple mise à jour d'un écran déjà ouvert (après une réclamation).
 */
public record MapStatePayload(String mapJson, List<String> done, List<String> claimed, boolean open)
        implements CustomPacketPayload {
    public static final Type<MapStatePayload> TYPE = new Type<>(AdventureMap.id("state"));

    /** La carte fait ~20 Ko ; la limite par défaut des chaînes (32 767) serait trop juste pour l'enrichir. */
    private static final int MAX_JSON = 1 << 18;

    public static final StreamCodec<RegistryFriendlyByteBuf, MapStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_JSON), MapStatePayload::mapJson,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MapStatePayload::done,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MapStatePayload::claimed,
            ByteBufCodecs.BOOL, MapStatePayload::open,
            MapStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
