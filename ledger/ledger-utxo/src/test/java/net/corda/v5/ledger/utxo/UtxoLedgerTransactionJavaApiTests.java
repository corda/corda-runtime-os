package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;

public final class UtxoLedgerTransactionJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getTimeWindowShouldReturnTheExpectedValue() {
        TimeWindow value = utxoLedgerTransaction.getTimeWindow();
        Assertions.assertEquals(timeWindow, value);
    }

    @Test
    public void getAttachmentsShouldReturnTheExpectedValue() {
        List<Attachment> value = utxoLedgerTransaction.getAttachments();
        Assertions.assertEquals(List.of(attachment), value);
    }

    @Test
    public void getCommandsShouldReturnTheExpectedValue() {
        List<Command> value = utxoLedgerTransaction.getCommands();
        Assertions.assertEquals(commands, value);
    }

    @Test
    public void getSignatoriesShouldReturnTheExpectedValue() {
        List<PublicKey> value = utxoLedgerTransaction.getSignatories();
        Assertions.assertEquals(keys, value);
    }

    @Test
    public void getInputStateAndRefsShouldReturnTheExpectedValue() {
        List<StateAndRef<?>> value = utxoLedgerTransaction.getInputStateAndRefs();
        Assertions.assertEquals(List.of(contractStateAndRef), value);
    }

    @Test
    public void getInputTransactionStatesShouldReturnTheExpectedValue() {
        List<TransactionState<?>> value = utxoLedgerTransaction.getInputTransactionStates();
        Assertions.assertEquals(List.of(contractTransactionState), value);
    }

    @Test
    public void getInputContractStatesShouldReturnTheExpectedValue() {
        List<ContractState> value = utxoLedgerTransaction.getInputContractStates();
        Assertions.assertEquals(List.of(contractState), value);
    }

    @Test
    public void getReferenceInputStateAndRefsShouldReturnTheExpectedValue() {
        List<StateAndRef<?>> value = utxoLedgerTransaction.getReferenceInputStateAndRefs();
        Assertions.assertEquals(List.of(contractStateAndRef), value);
    }

    @Test
    public void getReferenceInputTransactionStatesShouldReturnTheExpectedValue() {
        List<TransactionState<?>> value = utxoLedgerTransaction.getReferenceInputTransactionStates();
        Assertions.assertEquals(List.of(contractTransactionState), value);
    }

    @Test
    public void getReferenceInputContractStatesShouldReturnTheExpectedValue() {
        List<ContractState> value = utxoLedgerTransaction.getReferenceInputContractStates();
        Assertions.assertEquals(List.of(contractState), value);
    }

    @Test
    public void getOutputStateAndRefsShouldReturnTheExpectedValue() {
        List<StateAndRef<?>> value = utxoLedgerTransaction.getOutputStateAndRefs();
        Assertions.assertEquals(List.of(contractStateAndRef), value);
    }

    @Test
    public void getOutputTransactionStatesShouldReturnTheExpectedValue() {
        List<TransactionState<?>> value = utxoLedgerTransaction.getOutputTransactionStates();
        Assertions.assertEquals(List.of(contractTransactionState), value);
    }

    @Test
    public void getOutputContractStatesShouldReturnTheExpectedValue() {
        List<ContractState> value = utxoLedgerTransaction.getOutputContractStates();
        Assertions.assertEquals(List.of(contractState), value);
    }

    @Test
    public void getAttachmentShouldReturnTheExpectedValue() {
        Attachment value = utxoLedgerTransaction.getAttachment(hash);
        Assertions.assertEquals(attachment, value);
    }

    @Test
    public void getCommandsOfTypeCreateShouldReturnTheExpectedValue() {
        List<Create> value = utxoLedgerTransaction.getCommands(Create.class);
        Assertions.assertEquals(List.of(createCommand), value);
    }

    @Test
    public void getCommandsOfTypeUpdateShouldReturnTheExpectedValue() {
        List<Update> value = utxoLedgerTransaction.getCommands(Update.class);
        Assertions.assertEquals(List.of(updateCommand), value);
    }

    @Test
    public void getInputStateAndRefsOfTypeContractStateShouldReturnTheExpectedValue() {
        List<StateAndRef<ContractState>> value = utxoLedgerTransaction.getInputStateAndRefs(ContractState.class);
        Assertions.assertEquals(List.of(contractStateAndRef), value);
    }

    @Test
    public void getInputStatesOfTypeContractStateShouldReturnTheExpectedValue() {
        List<ContractState> value = utxoLedgerTransaction.getInputStates(ContractState.class);
        Assertions.assertEquals(List.of(contractState), value);
    }

    @Test
    public void getReferenceInputStateAndRefsOfTypeContractStateShouldReturnTheExpectedValue() {
        List<StateAndRef<ContractState>> value = utxoLedgerTransaction.getReferenceInputStateAndRefs(ContractState.class);
        Assertions.assertEquals(List.of(contractStateAndRef), value);
    }

    @Test
    public void getReferenceInputStatesOfTypeContractStateShouldReturnTheExpectedValue() {
        List<ContractState> value = utxoLedgerTransaction.getReferenceInputStates(ContractState.class);
        Assertions.assertEquals(List.of(contractState), value);
    }

    @Test
    public void getOutputStateAndRefsOfTypeContractStateShouldReturnTheExpectedValue() {
        List<StateAndRef<ContractState>> value = utxoLedgerTransaction.getOutputStateAndRefs(ContractState.class);
        Assertions.assertEquals(List.of(contractStateAndRef), value);
    }

    @Test
    public void getOutputStatesOfTypeContractStateShouldReturnTheExpectedValue() {
        List<ContractState> value = utxoLedgerTransaction.getOutputStates(ContractState.class);
        Assertions.assertEquals(List.of(contractState), value);
    }
}
