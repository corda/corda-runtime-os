package net.corda.v5.ledger.utxo;

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata;
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction;
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.Set;

public final class UtxoSignedTransactionJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void addSignaturesShouldReturnTheExpectedValue() {
        UtxoSignedTransaction transaction = utxoSignedTransaction.addSignatures(Set.of(signatureAndMetadata));
        Set<DigitalSignatureAndMetadata> value = transaction.getSignatures();
        Assertions.assertEquals(Set.of(signatureAndMetadata), value);
    }

    @Test
    public void addSignaturesVarargShouldReturnTheExpectedValue() {
        UtxoSignedTransaction transaction = utxoSignedTransaction.addSignatures(signatureAndMetadata);
        Set<DigitalSignatureAndMetadata> value = transaction.getSignatures();
        Assertions.assertEquals(Set.of(signatureAndMetadata), value);
    }

    @Test
    public void getMissingSignatoriesShouldReturnTheExpectedValue() {
        Set<PublicKey> value = utxoSignedTransaction.getMissingSignatories(serializationService);
        Assertions.assertEquals(keys, value);
    }

    @Test
    public void toLedgerTransactionShouldReturnTheExpectedValue() {
        UtxoLedgerTransaction value = utxoSignedTransaction.toLedgerTransaction(serializationService);
        Assertions.assertEquals(utxoLedgerTransaction, value);
    }
}
