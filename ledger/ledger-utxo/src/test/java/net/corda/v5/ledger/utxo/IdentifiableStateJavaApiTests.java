package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.Set;
import java.util.UUID;

public final class IdentifiableStateJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getParticipantsShouldReturnTheExpectedValue() {
        Set<PublicKey> value = identifiableState.getParticipants();
        Assertions.assertEquals(keys, value);
    }

    @Test
    public void getIdShouldReturnTheExpectedValue() {
        UUID value = identifiableState.getId();
        Assertions.assertEquals(id, value);
    }
}
