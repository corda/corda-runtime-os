package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.Set;

public final class ContractStateJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getParticipantsShouldReturnTheExpectedValue() {
        Set<PublicKey> value = contractState.getParticipants();
        Assertions.assertEquals(participants, value);
    }
}
