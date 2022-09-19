package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.Set;

public final class FungibleStateJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getParticipantsShouldReturnTheExpectedValue() {
        Set<PublicKey> value = fungibleState.getParticipants();
        Assertions.assertEquals(participants, value);
    }

    @Test
    public void getQuantityShouldReturnTheExpectedValue() {
        BigDecimal value = fungibleState.getQuantity();
        Assertions.assertEquals(quantity, value);
    }
}
