package net.corda.v5.ledger.contracts;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.UniqueIdentifier;
import net.corda.v5.ledger.identity.AbstractParty;
import net.corda.v5.ledger.identity.Party;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StatesJavaApiTest {
    private final CommandData commandData = mock(CommandData.class);
    private final OwnableState ownableState = mock(OwnableState.class);
    private final SecureHash secureHash =
            SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final StateRef stateRef = new StateRef(secureHash, 5);
    private final CPKConstraint cpkConstraint = mock(CPKConstraint.class);
    private final Party notary = mock(Party.class);
    private final ContractState contractState = mock(ContractState.class);
    private final String contract = "ContractClassName";
    private final TransactionState<ContractState> transactionState = new TransactionState<>(
            contractState,
            contract,
            notary,
            5,
            cpkConstraint
    );
    private final StateAndRef<ContractState> stateAndRef = new StateAndRef<>(transactionState, stateRef);
    private final AbstractParty abstractParty = mock(AbstractParty.class);
    private final List<AbstractParty> participants = List.of(abstractParty);



    @Nested
    public class ContractStateJavaApiTest {

        @Test
        public void participants() {

            final ContractState contractState = mock(ContractState.class);
            when(contractState.getParticipants()).thenReturn(participants);

            final List<AbstractParty> participants1 = contractState.getParticipants();

            Assertions.assertThat(participants).isEqualTo(participants1);
        }

        @Test
        public void customContractStateClass() {
            final CustomContractState customContractState = new CustomContractState(participants);

            Assertions.assertThat(customContractState).isNotNull();
            Assertions.assertThat(customContractState.getParticipants()).isEqualTo(participants);
        }

        class CustomContractState implements ContractState {
            private final List<AbstractParty> participants;

            public CustomContractState(List<AbstractParty> participants) {
                this.participants = participants;
            }

            @NotNull
            @Override
            public List<AbstractParty> getParticipants() {
                return participants;
            }
        }
    }

    @Nested
    public class LinearStateJavaApiTest {
        final UniqueIdentifier identifier = new UniqueIdentifier();

        @Test
        public void linearId() {
            final LinearState linearState = mock(LinearState.class);
            when(linearState.getLinearId()).thenReturn(identifier);

            final UniqueIdentifier identifier1 = linearState.getLinearId();

            Assertions.assertThat(identifier1).isEqualTo(identifier);
        }

        @Test
        public void customLinearStateClass() {
            final CustomLinearState customLinearState = new CustomLinearState(participants, identifier);

            Assertions.assertThat(customLinearState).isNotNull();
            Assertions.assertThat(customLinearState.getLinearId()).isEqualTo(identifier);
            Assertions.assertThat(customLinearState.getParticipants()).isEqualTo(participants);
        }

        class CustomLinearState implements LinearState {
            private final List<AbstractParty> participants;
            private final UniqueIdentifier linearId;

            public CustomLinearState(List<AbstractParty> participants, UniqueIdentifier linearId) {
                this.participants = participants;
                this.linearId = linearId;
            }

            @NotNull
            @Override
            public List<AbstractParty> getParticipants() {
                return participants;
            }

            @NotNull
            @Override
            public UniqueIdentifier getLinearId() {
                return linearId;
            }
        }
    }

    @Nested
    public class FungibleStateJavaApiTest {

        @Test
        public void amount() {
            final FungibleState<Integer> fungibleState = mock(FungibleState.class);
            final Integer integer = 5;
            final Amount<Integer> integerAmount = Amount.zero(integer);
            when(fungibleState.getAmount()).thenReturn(integerAmount);

            final Amount<Integer> amount = fungibleState.getAmount();

            Assertions.assertThat(amount).isEqualTo(integerAmount);
        }

        @Test
        public void customFungibleStateClass() {
            final Amount<Currency> amount = Amount.parseCurrency("£25000000");
            final CustomFungibleState customFungibleState = new CustomFungibleState(participants, amount);

            Assertions.assertThat(customFungibleState).isNotNull();
            Assertions.assertThat(customFungibleState.getAmount()).isEqualTo(amount);
            Assertions.assertThat(customFungibleState.getParticipants()).isEqualTo(participants);
        }

        class CustomFungibleState implements FungibleState<Currency> {
            private final List<AbstractParty> participants;
            private final Amount<Currency> amount;

            public CustomFungibleState(List<AbstractParty> participants, Amount<Currency> amount) {
                this.participants = participants;
                this.amount = amount;
            }

            @NotNull
            @Override
            public List<AbstractParty> getParticipants() {
                return participants;
            }

            @NotNull
            @Override
            public Amount<Currency> getAmount() {
                return amount;
            }
        }
    }

    @Nested
    public class OwnableStateJavaApiTest {
        final AbstractParty abstractParty1 = mock(AbstractParty.class);

        @Test
        public void owner() {
            when(ownableState.getOwner()).thenReturn(abstractParty);

            Assertions.assertThat(ownableState.getOwner()).isEqualTo(abstractParty);
        }

        @Test
        public void withNewOwner() {
            final CommandAndState commandAndState = new CommandAndState(commandData, ownableState);
            when(ownableState.withNewOwner(abstractParty)).thenReturn(commandAndState);

            final CommandAndState commandAndState1 = ownableState.withNewOwner(abstractParty);

            Assertions.assertThat(commandAndState1).isEqualTo(commandAndState);
        }

        @Test
        public void customOwnableStateClass() {
            final CustomOwnableState customOwnableState = new CustomOwnableState(abstractParty, participants);

            Assertions.assertThat(customOwnableState).isNotNull();
            Assertions.assertThat(customOwnableState.getOwner()).isEqualTo(abstractParty);
            Assertions.assertThat(customOwnableState.getParticipants()).isEqualTo(participants);
        }

        @Test
        public void customOwnableStateClass_withNewOwner() {
            final CustomOwnableState customOwnableState = new CustomOwnableState(abstractParty, participants);

            final CustomOwnableState customOwnableState1 =
                    (CustomOwnableState) customOwnableState.withNewOwner(abstractParty1).getOwnableState();

            Assertions.assertThat(customOwnableState1).isNotNull();
            Assertions.assertThat(customOwnableState1.getOwner()).isEqualTo(abstractParty1);
            Assertions.assertThat(customOwnableState1.getParticipants()).isEqualTo(participants);
        }

        class CustomOwnableState implements OwnableState {
            private AbstractParty owner;
            private final List<AbstractParty> participants;

            public CustomOwnableState(AbstractParty owner, List<AbstractParty> participants) {
                this.owner = owner;
                this.participants = participants;
            }

            @NotNull
            @Override
            public List<AbstractParty> getParticipants() {
                return participants;
            }

            @NotNull
            @Override
            public AbstractParty getOwner() {
                return owner;
            }

            @NotNull
            @Override
            public CommandAndState withNewOwner(@NotNull AbstractParty newOwner) {
                return new CommandAndState(commandData, new CustomOwnableState(newOwner, participants));
            }
        }
    }

    @Nested
    public class CommandAndStateJavaApiTest {
        private final CommandAndState commandAndState = new CommandAndState(commandData, ownableState);

        @Test
        public void command() {
            final CommandData commandData1 = commandAndState.getCommand();

            Assertions.assertThat(commandData1).isEqualTo(commandData);
        }

        @Test
        public void ownableState() {
            final OwnableState ownableState1 = commandAndState.getOwnableState();

            Assertions.assertThat(ownableState1).isEqualTo(ownableState);
        }
    }

    @Nested
    public class StateRefJavaApiTest {

        @Test
        public void txhash() {
            final SecureHash secureHash1 = stateRef.getTxhash();

            Assertions.assertThat(secureHash1).isEqualTo(secureHash);
        }

        @Test
        public void index() {
            final int index = stateRef.getIndex();

            Assertions.assertThat(index).isEqualTo(5);
        }
    }

    @Nested
    public class StateAndRefJavaApiTest {

        @Test
        public void state() {
            final TransactionState<ContractState> state = stateAndRef.getState();

            Assertions.assertThat(state).isEqualTo(transactionState);
        }

        @Test
        public void ref() {
            final StateRef stateRef1 = stateAndRef.getRef();

            Assertions.assertThat(stateRef1).isEqualTo(stateRef);
        }

        @Test
        public void referenced() {
            final ReferencedStateAndRef<ContractState> referencedStateAndRef = stateAndRef.referenced();

            Assertions.assertThat(referencedStateAndRef).isNotNull();
        }
    }

    @Nested
    public class ReferencedStateAndRefJavaApiTest {

        @Test
        public void stateAndRef() {
            final ReferencedStateAndRef<ContractState> referencedStateAndRef = new ReferencedStateAndRef<>(stateAndRef);

            final StateAndRef<ContractState> stateAndRef1 = referencedStateAndRef.getStateAndRef();

            Assertions.assertThat(stateAndRef1).isEqualTo(stateAndRef);
        }
    }

    @Nested
    public class StateAndContractJavaApiTest {
        private final StateAndContract stateAndContract = new StateAndContract(contractState, contract);

        @Test
        public void state() {
            final ContractState contractState1 = stateAndContract.getState();

            Assertions.assertThat(contractState1).isEqualTo(contractState);
        }

        @Test
        public void contract() {
            final String contract1 = stateAndContract.getContract();

            Assertions.assertThat(contract1).isEqualTo(contract);
        }
    }

    @Nested
    public class TransactionStateJavaApiTest {

        @Test
        public void data() {
            final ContractState contractState1 = transactionState.getData();

            Assertions.assertThat(contractState1).isEqualTo(contractState);
        }

        @Test
        public void contract() {
            final String contract1 = transactionState.getContract();

            Assertions.assertThat(contract1).isEqualTo(contract);
        }

        @Test
        public void notary() {
            final Party notary1 = transactionState.getNotary();

            Assertions.assertThat(notary1).isEqualTo(notary);
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        public void encumbrance() {
            final int encumbrance = transactionState.getEncumbrance();

            Assertions.assertThat(encumbrance).isEqualTo(5);
        }

        @Test
        public void constraint() {
            final CPKConstraint cpkConstraint1 = transactionState.getConstraint();

            Assertions.assertThat(cpkConstraint1).isEqualTo(cpkConstraint);
        }
    }

    @Nested
    public class StateInfoJavaApiTest {
        private final StateInfo stateInfo = new StateInfo(contract, notary, 5, cpkConstraint);

        @Test
        public void contract() {
            final String contract1 = stateInfo.getContract();

            Assertions.assertThat(contract1).isEqualTo(contract);
        }

        @Test
        public void notary() {
            final Party notary1 = stateInfo.getNotary();

            Assertions.assertThat(notary1).isEqualTo(notary);
        }

        @Test
        public void encumbrance() {
            @SuppressWarnings("ConstantConditions")
            final int encumbrance1 = stateInfo.getEncumbrance();

            Assertions.assertThat(encumbrance1).isEqualTo(5);
        }

        @Test
        public void constraint() {
            final CPKConstraint cpkConstraint1 = stateInfo.getConstraint();

            Assertions.assertThat(cpkConstraint1).isEqualTo(cpkConstraint);
        }
    }

    @Nested
    public class ContractStateDataJavaApiTest {

        @Test
        public void data() {
            final ContractStateData<ContractState> contractStateData = new ContractStateData<>(contractState);
            final ContractState contractState1 = contractStateData.getData();

            Assertions.assertThat(contractState1).isEqualTo(contractState);
        }
    }
}
