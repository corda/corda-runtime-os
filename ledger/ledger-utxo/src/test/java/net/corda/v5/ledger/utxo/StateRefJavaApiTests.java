package net.corda.v5.ledger.utxo;

import net.corda.v5.crypto.SecureHash;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public final class StateRefJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void parseShouldReturnTheExpectedValue() {
        StateRef value = StateRef.parse(hash + ":123");
        Assertions.assertEquals(hash, value.getTransactionId());
        Assertions.assertEquals(123, value.getIndex());
    }

    @Test
    public void parseShouldFailIfTheTransactionHashIsInvalid() {
        IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> StateRef.parse("INVALID_TRANSACTION_HASH:123")
        );

        Assertions.assertEquals(
                "Failed to parse a StateRef from the specified value. The transaction id is malformed: INVALID_TRANSACTION_HASH:123.",
                exception.getMessage()
        );
    }

    @Test
    public void parseShouldFailIfTheIndexIsInvalid() {
        IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> StateRef.parse(hash + ":INVALID_INDEX")
        );

        Assertions.assertEquals(
                "Failed to parse a StateRef from the specified value. The index is malformed: SHA256:0000000000000000000000000000000000000000000000000000000000000000:INVALID_INDEX.",
                exception.getMessage()
        );
    }

    @Test
    public void getIndexShouldReturnTheExpectedValue() {
        int value = stateRef.getIndex();
        Assertions.assertEquals(0, value);
    }

    @Test
    public void getTransactionShouldReturnTheExpectedValue() {
        SecureHash value = stateRef.getTransactionId();
        Assertions.assertEquals(hash, value);
    }
}
