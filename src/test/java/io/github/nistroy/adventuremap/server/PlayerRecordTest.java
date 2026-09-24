package io.github.nistroy.adventuremap.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlayerRecordTest {
    @Test
    void startsEmpty() {
        PlayerRecord empty = PlayerRecord.EMPTY;
        assertEquals(Set.of(), empty.visited());
        assertFalse(empty.mapGiven());
        assertFalse(empty.initialized());
    }

    @Test
    void addingReturnsANewRecordOnlyWhenSomethingChanges() {
        PlayerRecord one = PlayerRecord.EMPTY.withVisited("x:tavern");
        assertEquals(Set.of("x:tavern"), one.visited());
        assertSame(one, one.withVisited("x:tavern"), "rien de neuf : même objet, pas de sauvegarde inutile");
        assertEquals(Set.of("minecraft:the_nether"), one.withDimension("minecraft:the_nether").dimensions());
        assertEquals(Set.of("m:lich"), one.withWitnessed("m:lich").witnessed());
        assertEquals(Set.of("s1"), one.withClaimed("s1").claimed());
        assertTrue(one.withMapGiven().mapGiven());
        assertEquals(Set.of("s1", "s2"), one.withAnnounced(Set.of("s1", "s2")).announced());
        assertTrue(one.withAnnounced(Set.of()).initialized());
    }

    @Test
    void roundTripsThroughTheCodec() {
        PlayerRecord record = PlayerRecord.EMPTY.withVisited("x:a").withVisited("x:b").withDimension("d:1")
                .withWitnessed("m:boss").withClaimed("s1").withAnnounced(Set.of("s1")).withMapGiven();
        JsonElement json = PlayerRecord.CODEC.encodeStart(JsonOps.INSTANCE, record).getOrThrow();
        assertEquals(record, PlayerRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void missingFieldsDecodeAsEmptySoFutureFieldsStayCompatible() {
        PlayerRecord decoded = PlayerRecord.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"claimed\": [\"s1\"]}")).getOrThrow();
        assertEquals(Set.of("s1"), decoded.claimed());
        assertEquals(Set.of(), decoded.visited());
        assertFalse(decoded.mapGiven());
    }
}
