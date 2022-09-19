package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.Set;

public final class BearableStateJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getParticipantsShouldReturnTheExpectedValue() {
        Set<PublicKey> value = bearableState.getParticipants();
        Assertions.assertEquals(participants, value);
    }

    @Test
    public void getIssuerShouldReturnTheExpectedValue() {
        PublicKey value = bearableState.getBearer();
        Assertions.assertEquals(bobKey, value);
    }
}
