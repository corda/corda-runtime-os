package net.corda.v5.ledger.utxo;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public final class UtxoTransactionBuilderJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void addAttachmentShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.addAttachment(hash);
        List<SecureHash> value = builder.getAttachments();
        Assertions.assertEquals(List.of(hash), value);
    }

    @Test
    public void addCommandAndSignatoriesShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.addCommandAndSignatories(commandAndSignatories);
        List<CommandAndSignatories<?>> value = builder.getCommands();
        Assertions.assertEquals(List.of(commandAndSignatories), value);
    }
}
