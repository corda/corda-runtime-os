package net.corda.v5.ledger.consensual;

import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConsensualStateJavaApiTest {
    private final Party Party = mock(Party.class);
    private final List<Party> participants = List.of(Party);
    private final ConsensualState consensualState = mock(ConsensualState.class);

    @Test
    public void participants() {
        when(consensualState.getParticipants()).thenReturn(participants);

        final List<Party> result = consensualState.getParticipants();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(participants);
        verify(consensualState, times(1)).getParticipants();
    }

    @Test
    public void verifyMethod() {
        final ConsensualLedgerTransaction mockLedgerTx = mock(ConsensualLedgerTransaction.class);
        doNothing().when(consensualState).verify(mockLedgerTx);

        consensualState.verify(mockLedgerTx);

        verify(consensualState, times(1)).verify(mockLedgerTx);
    }

    @Test
    public void customConsensualStateClass() {
        final CustomConsensualState customConsensualState = new CustomConsensualState(participants);

        Assertions.assertThat(customConsensualState).isNotNull();
        Assertions.assertThat(customConsensualState.getParticipants()).isEqualTo(participants);
    }

    class CustomConsensualState implements ConsensualState {
        private final List<Party> participants;

        public CustomConsensualState(List<Party> participants) {
            this.participants = participants;
        }

        @NotNull
        @Override
        public List<Party> getParticipants() {
            return participants;
        }

        @Override
        public void verify(@NotNull ConsensualLedgerTransaction ledgerTransaction) {
        }
    }
}
