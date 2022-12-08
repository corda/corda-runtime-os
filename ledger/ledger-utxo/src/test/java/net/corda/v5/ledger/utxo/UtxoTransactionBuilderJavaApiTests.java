package net.corda.v5.ledger.utxo;

import net.corda.v5.ledger.common.Party;
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction;
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public final class UtxoTransactionBuilderJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getNotaryShouldReturnTheExpectedValue() {
        Party value = utxoTransactionBuilder.getNotary();
        Assertions.assertEquals(notaryParty, value);
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
    public void addInputStateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addInputState(contractStateRef);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addInputStatesShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addInputStates(List.of(contractStateRef, contractStateRef));
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addInputStatesVarargShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addInputStates(contractStateRef, contractStateRef);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addReferenceInputStateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addReferenceInputState(contractStateRef);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addReferenceInputStatesShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addReferenceInputStates(List.of(contractStateRef, contractStateRef));
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addReferenceInputStatesVarargShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addReferenceInputStates(contractStateRef, contractStateRef);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addOutputStateOfContractStateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addOutputState(contractState);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addOutputStatesOfContractStateShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addOutputStates(List.of(contractState, contractState));
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addOutputStatesOfContractStatesVarargShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addOutputStates(contractState, contractState);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addEncumberedOutputStatesOfListOfContractStatesShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addEncumberedOutputStates(encumbranceTag1, List.of(contractState, contractState));
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void addEncumberedOutputStatesOfVarargContractStatesShouldReturnTheExpectedValue() {
        UtxoTransactionBuilder value = utxoTransactionBuilder.addEncumberedOutputStates(encumbranceTag1, contractState, contractState);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void getEncumbranceGroupShouldReturnTheExpectedValue() {
        List<ContractState> value = utxoTransactionBuilder.getEncumbranceGroup(encumbranceTag1);
        Assertions.assertEquals(List.of(contractState, contractState), value);
    }

    @Test
    public void getEncumbranceGroupsShouldReturnTheExpectedValue() {
        Map<String, List<ContractState>> value = utxoTransactionBuilder.getEncumbranceGroups();
        Assertions.assertEquals(encumbranceGroups, value);
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
    public void toSignedTransactionShouldReturnTheExpectedValue() {
        UtxoSignedTransaction value = utxoTransactionBuilder.toSignedTransaction();
        Assertions.assertEquals(utxoSignedTransaction, value);
    }
}
