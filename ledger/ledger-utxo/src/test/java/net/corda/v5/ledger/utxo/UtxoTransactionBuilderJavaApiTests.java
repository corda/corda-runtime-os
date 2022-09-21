package net.corda.v5.ledger.utxo;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction;
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder;
import net.corda.v5.ledger.utxo.transaction.UtxoWireTransaction;
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
        UtxoTransactionBuilder builder = utxoTransactionBuilder.addCommandAndSignatories(command, aliceKey, bobKey);
        List<CommandAndSignatories<?>> value = builder.getCommands();
        Assertions.assertEquals(List.of(commandAndSignatories), value);
    }

    @Test
    public void addInputStateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.addInputState(contractStateAndRef);
        List<StateAndRef<?>> value = builder.getInputStateAndRefs();
        Assertions.assertEquals(List.of(contractStateAndRef), value);
    }

    @Test
    public void addReferenceInputStateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.addReferenceInputState(contractStateAndRef);
        List<StateAndRef<?>> value = builder.getReferenceInputStateAndRefs();
        Assertions.assertEquals(List.of(contractStateAndRef), value);
    }

    @Test
    public void addOutputStateOfTransactionStateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.addOutputState(contractTransactionState);
        List<TransactionState<?>> value = builder.getOutputTransactionStates();
        Assertions.assertEquals(List.of(contractTransactionState), value);
    }

    @Test
    public void addOutputStateOfContractStateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.addOutputState(contractState);
        List<TransactionState<?>> value = builder.getOutputTransactionStates();
        Assertions.assertEquals(List.of(contractTransactionState), value);
    }

    @Test
    public void addOutputStateOfContractStateAndNotaryShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.addOutputState(contractState, notaryParty);
        List<TransactionState<?>> value = builder.getOutputTransactionStates();
        Assertions.assertEquals(List.of(contractTransactionState), value);
    }

    @Test
    public void addOutputStateOfContractStateAndContractIdShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.addOutputState(contractState, contractId);
        List<TransactionState<?>> value = builder.getOutputTransactionStates();
        Assertions.assertEquals(List.of(contractTransactionState), value);
    }

    @Test
    public void addOutputStateOfContractStateAndContractIdAndNotaryShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.addOutputState(contractState, contractId, notaryParty);
        List<TransactionState<?>> value = builder.getOutputTransactionStates();
        Assertions.assertEquals(List.of(contractTransactionState), value);
    }

    @Test
    public void addOutputStateOfContractStateAndContractIdAndNotaryAndEncumbranceShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.addOutputState(contractState, contractId, notaryParty, 0);
        List<TransactionState<?>> value = builder.getOutputTransactionStates();
        Assertions.assertEquals(List.of(contractTransactionState), value);
    }

    @Test
    public void setTimeWindowFromShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.setTimeWindowFrom(minInstant);
        TimeWindow value = builder.getTimeWindow();
        Assertions.assertEquals(timeWindow, value);
    }

    @Test
    public void setTimeWindowUntilShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.setTimeWindowUntil(maxInstant);
        TimeWindow value = builder.getTimeWindow();
        Assertions.assertEquals(timeWindow, value);
    }

    @Test
    public void setTimeWindowBetweenOfInstantAndInstantShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.setTimeWindowBetween(minInstant, maxInstant);
        TimeWindow value = builder.getTimeWindow();
        Assertions.assertEquals(timeWindow, value);
    }

    @Test
    public void setTimeWindowBetweenOfInstantAndDurationShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder builder = utxoTransactionBuilder.setTimeWindowBetween(midpoint, duration);
        TimeWindow value = builder.getTimeWindow();
        Assertions.assertEquals(timeWindow, value);
    }

    @Test
    public void signShouldReturnTheExpectedValue() {
        UtxoSignedTransaction value = utxoTransactionBuilder.sign();
        Assertions.assertEquals(utxoSignedTransaction, value);
    }

    @Test
    public void signWithMultipleKeysShouldReturnTheExpectedValue() {
        UtxoSignedTransaction value = utxoTransactionBuilder.sign(keys);
        Assertions.assertEquals(utxoSignedTransaction, value);
    }

    @Test
    public void signWithSingleKeyShouldReturnTheExpectedValue() {
        UtxoSignedTransaction value = utxoTransactionBuilder.sign(aliceKey);
        Assertions.assertEquals(utxoSignedTransaction, value);
    }

    @Test
    public void toWireTransactionShouldReturnTheExpectedValue() {
        UtxoWireTransaction value = utxoTransactionBuilder.toWireTransaction();
        Assertions.assertEquals(utxoWireTransaction, value);
    }
}
