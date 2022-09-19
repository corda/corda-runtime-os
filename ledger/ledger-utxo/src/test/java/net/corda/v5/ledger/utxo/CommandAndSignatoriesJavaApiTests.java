package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.Set;

public final class CommandAndSignatoriesJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getCommandShouldReturnTheExpectedValue() {
        VerifiableCommand value = commandAndSignatories.getCommand();
        Assertions.assertEquals(command, value);
    }

    @Test
    public void getSignatoriesShouldReturnTheExpectedValue() {
        Set<PublicKey> value = commandAndSignatories.getSignatories();
        Assertions.assertEquals(keys, value);
    }
}
