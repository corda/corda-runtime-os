package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
        List<CommandAndSignatories<?>> value = utxoLedgerTransaction.getCommands();
        Assertions.assertEquals(commands, value);
    }

    @Test
    public void getInputStateAndRefsShouldReturnTheExpectedValue() {
        List<StateAndRef<?>> value = utxoLedgerTransaction.getInputStateAndRefs();
        Assertions.assertEquals(contractStateAndRefs, value);
    }

    @Test
    public void getInputTransactionStatesShouldReturnTheExpectedValue() {
        List<TransactionState<?>> value = utxoLedgerTransaction.getInputTransactionStates();
        Assertions.assertEquals(contractTransactionStates, value);
    }

    @Test
    public void getInputContractStatesShouldReturnTheExpectedValue() {
        List<ContractState> value = utxoLedgerTransaction.getInputContractStates();
        Assertions.assertEquals(contractStates, value);
    }

    @Test
    public void getReferenceInputStateAndRefsShouldReturnTheExpectedValue() {
        List<StateAndRef<?>> value = utxoLedgerTransaction.getReferenceInputStateAndRefs();
        Assertions.assertEquals(contractStateAndRefs, value);
    }

    @Test
    public void getReferenceInputTransactionStatesShouldReturnTheExpectedValue() {
        List<TransactionState<?>> value = utxoLedgerTransaction.getReferenceInputTransactionStates();
        Assertions.assertEquals(contractTransactionStates, value);
    }

    @Test
    public void getReferenceInputContractStatesShouldReturnTheExpectedValue() {
        List<ContractState> value = utxoLedgerTransaction.getReferenceInputContractStates();
        Assertions.assertEquals(contractStates, value);
    }

    @Test
    public void getOutputStateAndRefsShouldReturnTheExpectedValue() {
        List<StateAndRef<?>> value = utxoLedgerTransaction.getOutputStateAndRefs();
        Assertions.assertEquals(contractStateAndRefs, value);
    }

    @Test
    public void getOutputTransactionStatesShouldReturnTheExpectedValue() {
        List<TransactionState<?>> value = utxoLedgerTransaction.getOutputTransactionStates();
        Assertions.assertEquals(contractTransactionStates, value);
    }

    @Test
    public void getOutputContractStatesShouldReturnTheExpectedValue() {
        List<ContractState> value = utxoLedgerTransaction.getOutputContractStates();
        Assertions.assertEquals(contractStates, value);
    }

    @Test
    public void getAttachmentByIdShouldReturnTheExpectedValue() {
        Attachment value = utxoLedgerTransaction.getAttachment(hash);
        Assertions.assertEquals(attachment, value);
    }

    @Test
    public void getCommandAndSignatoriesOfTypeVerifiableCommandShouldReturnTheExpectedValue() {
        CommandAndSignatories<VerifiableCommand> value = utxoLedgerTransaction.getCommandAndSignatories(VerifiableCommand.class);
        Assertions.assertEquals(commandAndSignatories, value);
    }

    @Test
    public void getCommandsAndSignatoriesOfTypeVerifiableCommandShouldReturnTheExpectedValue() {
        List<CommandAndSignatories<VerifiableCommand>> value = utxoLedgerTransaction.getCommandsAndSignatories(VerifiableCommand.class);
        Assertions.assertEquals(List.of(commandAndSignatories), value);
    }

    @Test
    public void getInputStateAndRefsOfTypeContractStateShouldReturnTheExpectedValue() {
        List<StateAndRef<ContractState>> value = utxoLedgerTransaction.getInputStateAndRefs(ContractState.class);
        Assertions.assertEquals(contractStateAndRefs, value);
    }

    @Test
    public void getInputStateAndRefsOfTypeFungibleStateShouldReturnTheExpectedValue() {
        List<StateAndRef<FungibleState>> value = utxoLedgerTransaction.getInputStateAndRefs(FungibleState.class);
        Assertions.assertEquals(List.of(fungibleStateAndRef), value);
    }

    @Test
    public void getInputStateAndRefsOfTypeIdentifiableStateShouldReturnTheExpectedValue() {
        List<StateAndRef<IdentifiableState>> value = utxoLedgerTransaction.getInputStateAndRefs(IdentifiableState.class);
        Assertions.assertEquals(List.of(identifiableStateAndRef), value);
    }

    @Test
    public void getInputStateAndRefsOfTypeIssuableStateShouldReturnTheExpectedValue() {
        List<StateAndRef<IssuableState>> value = utxoLedgerTransaction.getInputStateAndRefs(IssuableState.class);
        Assertions.assertEquals(List.of(issuableStateAndRef), value);
    }

    @Test
    public void getInputStateAndRefsOfTypeBearableStateShouldReturnTheExpectedValue() {
        List<StateAndRef<BearableState>> value = utxoLedgerTransaction.getInputStateAndRefs(BearableState.class);
        Assertions.assertEquals(List.of(bearableStateAndRef), value);
    }

    @Test
    public void getInputStateAndRefOfTypeContractStateShouldReturnTheExpectedValue() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> utxoLedgerTransaction.getInputStateAndRef(ContractState.class));
    }

    @Test
    public void getInputStateAndRefOfTypeFungibleStateShouldReturnTheExpectedValue() {
        StateAndRef<FungibleState> value = utxoLedgerTransaction.getInputStateAndRef(FungibleState.class);
        Assertions.assertEquals(fungibleStateAndRef, value);
    }

    @Test
    public void getInputStateAndRefOfTypeIdentifiableStateShouldReturnTheExpectedValue() {
        StateAndRef<IdentifiableState> value = utxoLedgerTransaction.getInputStateAndRef(IdentifiableState.class);
        Assertions.assertEquals(identifiableStateAndRef, value);
    }

    @Test
    public void getInputStateAndRefOfTypeIssuableStateShouldReturnTheExpectedValue() {
        StateAndRef<IssuableState> value = utxoLedgerTransaction.getInputStateAndRef(IssuableState.class);
        Assertions.assertEquals(issuableStateAndRef, value);
    }

    @Test
    public void getInputStateAndRefOfTypeBearableStateShouldReturnTheExpectedValue() {
        StateAndRef<BearableState> value = utxoLedgerTransaction.getInputStateAndRef(BearableState.class);
        Assertions.assertEquals(bearableStateAndRef, value);
    }

    @Test
    public void getInputStatesOfTypeContractStateShouldReturnTheExpectedValue() {
        List<ContractState> value = utxoLedgerTransaction.getInputStates(ContractState.class);
        Assertions.assertEquals(contractStates, value);
    }

    @Test
    public void getInputStatesOfTypeFungibleStateShouldReturnTheExpectedValue() {
        List<FungibleState> value = utxoLedgerTransaction.getInputStates(FungibleState.class);
        Assertions.assertEquals(List.of(fungibleState), value);
    }

    @Test
    public void getInputStatesOfTypeIdentifiableStateShouldReturnTheExpectedValue() {
        List<IdentifiableState> value = utxoLedgerTransaction.getInputStates(IdentifiableState.class);
        Assertions.assertEquals(List.of(identifiableState), value);
    }

    @Test
    public void getInputStatesOfTypeIssuableStateShouldReturnTheExpectedValue() {
        List<IssuableState> value = utxoLedgerTransaction.getInputStates(IssuableState.class);
        Assertions.assertEquals(List.of(issuableState), value);
    }

    @Test
    public void getInputStatesOfTypeBearableStateShouldReturnTheExpectedValue() {
        List<BearableState> value = utxoLedgerTransaction.getInputStates(BearableState.class);
        Assertions.assertEquals(List.of(bearableState), value);
    }

    @Test
    public void getInputStateOfTypeContractStateShouldReturnTheExpectedValue() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> utxoLedgerTransaction.getInputState(ContractState.class));
    }

    @Test
    public void getInputStateOfTypeFungibleStateShouldReturnTheExpectedValue() {
        FungibleState<BigDecimal> value = utxoLedgerTransaction.getInputState(FungibleState.class);
        Assertions.assertEquals(fungibleState, value);
    }

    @Test
    public void getInputStateOfTypeIdentifiableStateShouldReturnTheExpectedValue() {
        IdentifiableState value = utxoLedgerTransaction.getInputState(IdentifiableState.class);
        Assertions.assertEquals(identifiableState, value);
    }

    @Test
    public void getInputStateOfTypeIssuableStateShouldReturnTheExpectedValue() {
        IssuableState value = utxoLedgerTransaction.getInputState(IssuableState.class);
        Assertions.assertEquals(issuableState, value);
    }

    @Test
    public void getInputStateOfTypeBearableStateShouldReturnTheExpectedValue() {
        BearableState value = utxoLedgerTransaction.getInputState(BearableState.class);
        Assertions.assertEquals(bearableState, value);
    }


    @Test
    public void getReferenceInputStateAndRefsOfTypeContractStateShouldReturnTheExpectedValue() {
        List<StateAndRef<ContractState>> value = utxoLedgerTransaction.getReferenceInputStateAndRefs(ContractState.class);
        Assertions.assertEquals(contractStateAndRefs, value);
    }

    @Test
    public void getReferenceInputStateAndRefsOfTypeFungibleStateShouldReturnTheExpectedValue() {
        List<StateAndRef<FungibleState>> value = utxoLedgerTransaction.getReferenceInputStateAndRefs(FungibleState.class);
        Assertions.assertEquals(List.of(fungibleStateAndRef), value);
    }

    @Test
    public void getReferenceInputStateAndRefsOfTypeIdentifiableStateShouldReturnTheExpectedValue() {
        List<StateAndRef<IdentifiableState>> value = utxoLedgerTransaction.getReferenceInputStateAndRefs(IdentifiableState.class);
        Assertions.assertEquals(List.of(identifiableStateAndRef), value);
    }

    @Test
    public void getReferenceInputStateAndRefsOfTypeIssuableStateShouldReturnTheExpectedValue() {
        List<StateAndRef<IssuableState>> value = utxoLedgerTransaction.getReferenceInputStateAndRefs(IssuableState.class);
        Assertions.assertEquals(List.of(issuableStateAndRef), value);
    }

    @Test
    public void getReferenceInputStateAndRefsOfTypeBearableStateShouldReturnTheExpectedValue() {
        List<StateAndRef<BearableState>> value = utxoLedgerTransaction.getReferenceInputStateAndRefs(BearableState.class);
        Assertions.assertEquals(List.of(bearableStateAndRef), value);
    }


    @Test
    public void getReferenceInputStateOfTypeBearableStateShouldReturnTheExpectedValue() {
        BearableState value = utxoLedgerTransaction.getReferenceInputState(BearableState.class);
        Assertions.assertEquals(bearableState, value);
    }

    @Test
    public void getReferenceInputStateAndRefOfTypeContractStateShouldReturnTheExpectedValue() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> utxoLedgerTransaction.getReferenceInputStateAndRef(ContractState.class));
    }

    @Test
    public void getReferenceInputStateAndRefOfTypeFungibleStateShouldReturnTheExpectedValue() {
        StateAndRef<FungibleState> value = utxoLedgerTransaction.getReferenceInputStateAndRef(FungibleState.class);
        Assertions.assertEquals(fungibleStateAndRef, value);
    }

    @Test
    public void getReferenceInputStateAndRefOfTypeIdentifiableStateShouldReturnTheExpectedValue() {
        StateAndRef<IdentifiableState> value = utxoLedgerTransaction.getReferenceInputStateAndRef(IdentifiableState.class);
        Assertions.assertEquals(identifiableStateAndRef, value);
    }

    @Test
    public void getReferenceInputStateAndRefOfTypeIssuableStateShouldReturnTheExpectedValue() {
        StateAndRef<IssuableState> value = utxoLedgerTransaction.getReferenceInputStateAndRef(IssuableState.class);
        Assertions.assertEquals(issuableStateAndRef, value);
    }

    @Test
    public void getReferenceInputStateAndRefOfTypeBearableStateShouldReturnTheExpectedValue() {
        StateAndRef<BearableState> value = utxoLedgerTransaction.getReferenceInputStateAndRef(BearableState.class);
        Assertions.assertEquals(bearableStateAndRef, value);
    }

    @Test
    public void getReferenceInputStatesOfTypeContractStateShouldReturnTheExpectedValue() {
        List<ContractState> value = utxoLedgerTransaction.getReferenceInputStates(ContractState.class);
        Assertions.assertEquals(contractStates, value);
    }

    @Test
    public void getReferenceInputStatesOfTypeFungibleStateShouldReturnTheExpectedValue() {
        List<FungibleState> value = utxoLedgerTransaction.getReferenceInputStates(FungibleState.class);
        Assertions.assertEquals(List.of(fungibleState), value);
    }

    @Test
    public void getReferenceInputStatesOfTypeIdentifiableStateShouldReturnTheExpectedValue() {
        List<IdentifiableState> value = utxoLedgerTransaction.getReferenceInputStates(IdentifiableState.class);
        Assertions.assertEquals(List.of(identifiableState), value);
    }

    @Test
    public void getReferenceInputStatesOfTypeIssuableStateShouldReturnTheExpectedValue() {
        List<IssuableState> value = utxoLedgerTransaction.getReferenceInputStates(IssuableState.class);
        Assertions.assertEquals(List.of(issuableState), value);
    }

    @Test
    public void getReferenceInputStatesOfTypeBearableStateShouldReturnTheExpectedValue() {
        List<BearableState> value = utxoLedgerTransaction.getReferenceInputStates(BearableState.class);
        Assertions.assertEquals(List.of(bearableState), value);
    }

    @Test
    public void getReferenceInputStateOfTypeContractStateShouldReturnTheExpectedValue() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> utxoLedgerTransaction.getReferenceInputState(ContractState.class));
    }

    @Test
    public void getReferenceInputStateOfTypeFungibleStateShouldReturnTheExpectedValue() {
        FungibleState<BigDecimal> value = utxoLedgerTransaction.getReferenceInputState(FungibleState.class);
        Assertions.assertEquals(fungibleState, value);
    }

    @Test
    public void getReferenceInputStateOfTypeIdentifiableStateShouldReturnTheExpectedValue() {
        IdentifiableState value = utxoLedgerTransaction.getReferenceInputState(IdentifiableState.class);
        Assertions.assertEquals(identifiableState, value);
    }

    @Test
    public void getReferenceInputStateOfTypeIssuableStateShouldReturnTheExpectedValue() {
        IssuableState value = utxoLedgerTransaction.getReferenceInputState(IssuableState.class);
        Assertions.assertEquals(issuableState, value);
    }

    @Test
    public void getOutputStateAndRefsOfTypeContractStateShouldReturnTheExpectedValue() {
        List<StateAndRef<ContractState>> value = utxoLedgerTransaction.getOutputStateAndRefs(ContractState.class);
        Assertions.assertEquals(contractStateAndRefs, value);
    }

    @Test
    public void getOutputStateAndRefsOfTypeFungibleStateShouldReturnTheExpectedValue() {
        List<StateAndRef<FungibleState>> value = utxoLedgerTransaction.getOutputStateAndRefs(FungibleState.class);
        Assertions.assertEquals(List.of(fungibleStateAndRef), value);
    }

    @Test
    public void getOutputStateAndRefsOfTypeIdentifiableStateShouldReturnTheExpectedValue() {
        List<StateAndRef<IdentifiableState>> value = utxoLedgerTransaction.getOutputStateAndRefs(IdentifiableState.class);
        Assertions.assertEquals(List.of(identifiableStateAndRef), value);
    }

    @Test
    public void getOutputStateAndRefsOfTypeIssuableStateShouldReturnTheExpectedValue() {
        List<StateAndRef<IssuableState>> value = utxoLedgerTransaction.getOutputStateAndRefs(IssuableState.class);
        Assertions.assertEquals(List.of(issuableStateAndRef), value);
    }

    @Test
    public void getOutputStateAndRefsOfTypeBearableStateShouldReturnTheExpectedValue() {
        List<StateAndRef<BearableState>> value = utxoLedgerTransaction.getOutputStateAndRefs(BearableState.class);
        Assertions.assertEquals(List.of(bearableStateAndRef), value);
    }

    @Test
    public void getOutputStateAndRefOfTypeContractStateShouldReturnTheExpectedValue() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> utxoLedgerTransaction.getOutputStateAndRef(ContractState.class));
    }

    @Test
    public void getOutputStateAndRefOfTypeFungibleStateShouldReturnTheExpectedValue() {
        StateAndRef<FungibleState> value = utxoLedgerTransaction.getOutputStateAndRef(FungibleState.class);
        Assertions.assertEquals(fungibleStateAndRef, value);
    }

    @Test
    public void getOutputStateAndRefOfTypeIdentifiableStateShouldReturnTheExpectedValue() {
        StateAndRef<IdentifiableState> value = utxoLedgerTransaction.getOutputStateAndRef(IdentifiableState.class);
        Assertions.assertEquals(identifiableStateAndRef, value);
    }

    @Test
    public void getOutputStateAndRefOfTypeIssuableStateShouldReturnTheExpectedValue() {
        StateAndRef<IssuableState> value = utxoLedgerTransaction.getOutputStateAndRef(IssuableState.class);
        Assertions.assertEquals(issuableStateAndRef, value);
    }

    @Test
    public void getOutputStateAndRefOfTypeBearableStateShouldReturnTheExpectedValue() {
        StateAndRef<BearableState> value = utxoLedgerTransaction.getOutputStateAndRef(BearableState.class);
        Assertions.assertEquals(bearableStateAndRef, value);
    }

    @Test
    public void getOutputStatesOfTypeContractStateShouldReturnTheExpectedValue() {
        List<ContractState> value = utxoLedgerTransaction.getOutputStates(ContractState.class);
        Assertions.assertEquals(contractStates, value);
    }

    @Test
    public void getOutputStatesOfTypeFungibleStateShouldReturnTheExpectedValue() {
        List<FungibleState> value = utxoLedgerTransaction.getOutputStates(FungibleState.class);
        Assertions.assertEquals(List.of(fungibleState), value);
    }

    @Test
    public void getOutputStatesOfTypeIdentifiableStateShouldReturnTheExpectedValue() {
        List<IdentifiableState> value = utxoLedgerTransaction.getOutputStates(IdentifiableState.class);
        Assertions.assertEquals(List.of(identifiableState), value);
    }

    @Test
    public void getOutputStatesOfTypeIssuableStateShouldReturnTheExpectedValue() {
        List<IssuableState> value = utxoLedgerTransaction.getOutputStates(IssuableState.class);
        Assertions.assertEquals(List.of(issuableState), value);
    }

    @Test
    public void getOutputStatesOfTypeBearableStateShouldReturnTheExpectedValue() {
        List<BearableState> value = utxoLedgerTransaction.getOutputStates(BearableState.class);
        Assertions.assertEquals(List.of(bearableState), value);
    }

    @Test
    public void getOutputStateOfTypeContractStateShouldReturnTheExpectedValue() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> utxoLedgerTransaction.getOutputState(ContractState.class));
    }

    @Test
    public void getOutputStateOfTypeFungibleStateShouldReturnTheExpectedValue() {
        FungibleState<BigDecimal> value = utxoLedgerTransaction.getOutputState(FungibleState.class);
        Assertions.assertEquals(fungibleState, value);
    }

    @Test
    public void getOutputStateOfTypeIdentifiableStateShouldReturnTheExpectedValue() {
        IdentifiableState value = utxoLedgerTransaction.getOutputState(IdentifiableState.class);
        Assertions.assertEquals(identifiableState, value);
    }

    @Test
    public void getOutputStateOfTypeIssuableStateShouldReturnTheExpectedValue() {
        IssuableState value = utxoLedgerTransaction.getOutputState(IssuableState.class);
        Assertions.assertEquals(issuableState, value);
    }

    @Test
    public void getOutputStateOfTypeBearableStateShouldReturnTheExpectedValue() {
        BearableState value = utxoLedgerTransaction.getOutputState(BearableState.class);
        Assertions.assertEquals(bearableState, value);
    }
}
