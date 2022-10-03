package net.corda.v5.ledger.utxo;

import net.corda.v5.ledger.common.Party;
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction;
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;
import java.util.Set;

public final class UtxoTransactionBuilderJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getNotaryShouldReturnTheExpectedValue() {
        Party value = utxoTransactionBuilder.getNotary();
        Assertions.assertEquals(notaryParty, value);
    }

    @Test
    public void getTimeWindowShouldReturnTheExpectedValue() {
        TimeWindow value = utxoTransactionBuilder.getTimeWindow();
        Assertions.assertEquals(timeWindow, value);
    }

    @Test
    public void getCommandsShouldReturnTheExpectedValue() {
        List<Command> value = utxoTransactionBuilder.getCommands();
        Assertions.assertEquals(commands, value);
    }

    @Test
    public void getSignatoriesShouldReturnTheExpectedValue() {
        Set<PublicKey> value = utxoTransactionBuilder.getSignatories();
        Assertions.assertEquals(keys, value);
    }

    @Test
    public void getInputStateAndRefsShouldReturnTheExpectedValue() {
        List<StateAndRef<?>> value = utxoTransactionBuilder.getInputStateAndRefs();
        Assertions.assertEquals(List.of(contractStateAndRef), value);
    }

    @Test
    public void getReferenceInputStateAndRefsShouldReturnTheExpectedValue() {
        List<StateAndRef<?>> value = utxoTransactionBuilder.getReferenceInputStateAndRefs();
        Assertions.assertEquals(List.of(contractStateAndRef), value);
    }

    @Test
    public void getOutputTransactionStatesShouldReturnTheExpectedValue() {
        List<TransactionState<?>> value = utxoTransactionBuilder.getOutputTransactionStates();
        Assertions.assertEquals(List.of(contractTransactionState), value);
    }

    @Test
    public void addAttachmentShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addAttachment(hash);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addCommandOfTypeCreateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addCommand(createCommand);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addCommandOfTypeUpdateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addCommand(updateCommand);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addSignatoriesShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addSignatories(keys);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addCommandAndSignatoriesOfTypeCreateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addCommandAndSignatories(createCommand, keys);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addCommandAndSignatoriesOfTypeUpdateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addCommandAndSignatories(updateCommand, keys);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addCommandAndSignatoriesOfTypeCreateWithVarargKeysShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addCommandAndSignatories(createCommand, aliceKey, bobKey);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addCommandAndSignatoriesOfTypeUpdateWithVarargKeysShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addCommandAndSignatories(updateCommand, aliceKey, bobKey);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addInputStateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addInputState(contractStateAndRef);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addReferenceInputStateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addReferenceInputState(contractStateAndRef);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addOutputStateOfContractStateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addOutputState(contractState);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addOutputStateOfContractStateAndEncumbranceShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addOutputState(contractState, 0);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void setTimeWindowFromShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.setTimeWindowFrom(minInstant);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void setTimeWindowUntilShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.setTimeWindowUntil(maxInstant);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void setTimeWindowBetweenOfInstantAndInstantShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.setTimeWindowBetween(minInstant, maxInstant);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void setTimeWindowBetweenOfInstantAndDurationShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.setTimeWindowBetween(midpoint, duration);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void signShouldReturnTheExpectedValue() {
        UtxoSignedTransaction value = utxoTransactionBuilder.sign();
        Assertions.assertEquals(utxoSignedTransaction, value);
    }

    @Test
    public void signWithKeysShouldReturnTheExpectedValue() {
        UtxoSignedTransaction value = utxoTransactionBuilder.sign(keys);
        Assertions.assertEquals(utxoSignedTransaction, value);
    }

    @Test
    public void signWithVarargKeysShouldReturnTheExpectedValue() {
        UtxoSignedTransaction value = utxoTransactionBuilder.sign(aliceKey, bobKey);
        Assertions.assertEquals(utxoSignedTransaction, value);
    }

    @Test
    public void verifyShouldBeCallable() {
        utxoTransactionBuilder.verify();
    }

    @Test
    public void verifyAndSignShouldReturnTheExpectedValue() {
        UtxoSignedTransaction value = utxoTransactionBuilder.verifyAndSign();
        Assertions.assertEquals(utxoSignedTransaction, value);
    }

    @Test
    public void verifyAndSignWithKeysShouldReturnTheExpectedValue() {
        UtxoSignedTransaction value = utxoTransactionBuilder.verifyAndSign(keys);
        Assertions.assertEquals(utxoSignedTransaction, value);
    }

    @Test
    public void verifyAndSignWithVarargKeysShouldReturnTheExpectedValue() {
        UtxoSignedTransaction value = utxoTransactionBuilder.verifyAndSign(aliceKey, bobKey);
        Assertions.assertEquals(utxoSignedTransaction, value);
    }
}
