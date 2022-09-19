package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.Set;

public final class IssuableStateJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getParticipantsShouldReturnTheExpectedValue() {
        Set<PublicKey> value = issuableState.getParticipants();
        Assertions.assertEquals(keys, value);
    }

    @Test
    public void getIssuerShouldReturnTheExpectedValue() {
        PublicKey value = issuableState.getIssuer();
        Assertions.assertEquals(aliceKey, value);
    }
}
