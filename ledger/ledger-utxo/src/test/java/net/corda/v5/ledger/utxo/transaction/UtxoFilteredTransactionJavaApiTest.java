package net.corda.v5.ledger.utxo.transaction;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.utxo.Command;
import net.corda.v5.ledger.utxo.StateAndRef;
import net.corda.v5.ledger.utxo.StateRef;
import net.corda.v5.ledger.utxo.TimeWindow;
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData;
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

public class UtxoFilteredTransactionJavaApiTest {

    protected final SecureHash hash = SecureHash.parse("SHA256:0000000000000000000000000000000000000000000000000000000000000000");

    @Test
    void canReadFilteredTransaction() {
        // Set up Filtered TX mock
        UtxoFilteredTransaction filteredTx = Mockito.mock(UtxoFilteredTransaction.class);

        // Notary, time window commands
        Mockito.when(filteredTx.getNotary()).thenReturn(null);
        Mockito.when(filteredTx.getTimeWindow()).thenReturn(Mockito.mock(TimeWindow.class));
        Mockito.when(filteredTx.getCommands()).thenReturn(Mockito.mock(UtxoFilteredData.Removed.class));

        // Outputs are size only
        UtxoFilteredData.SizeOnly<StateAndRef<?>> outputs
                = Mockito.mock(UtxoFilteredData.SizeOnly.class);
        Mockito.when(outputs.getSize()).thenReturn(5);
        Mockito.when(filteredTx.getOutputStateAndRefs()).thenReturn(outputs);

        // Inputs is a filtered Audit proof
        UtxoFilteredData.Audit<StateRef> inputs =
                Mockito.mock(UtxoFilteredData.Audit.class);
        Mockito.when(inputs.getSize()).thenReturn(2);
        Mockito.when(inputs.getValues()).thenReturn(Map.of(1, new StateRef(hash, 1) ));
        Mockito.when(filteredTx.getInputStateRefs()).thenReturn(inputs);


        UtxoFilteredData<Command> commands = filteredTx.getCommands();
        Assertions.assertThat(commands).isInstanceOf(UtxoFilteredData.Removed.class);

        Assertions.assertThat(filteredTx.getNotary()).isNull();

        Assertions.assertThat(filteredTx.getTimeWindow()).isInstanceOf(TimeWindow.class);

        UtxoFilteredData<StateAndRef<?>> txOutputs = filteredTx.getOutputStateAndRefs();
        Assertions.assertThat(txOutputs).isInstanceOf(UtxoFilteredData.SizeOnly.class);
        UtxoFilteredData.SizeOnly<StateAndRef<?>> sizeOnlyOutputs = (UtxoFilteredData.SizeOnly<StateAndRef<?>>) txOutputs;
        Assertions.assertThat(sizeOnlyOutputs.getSize()).isEqualTo(5);

        UtxoFilteredData<StateRef> txInputs = filteredTx.getInputStateRefs();
        Assertions.assertThat(txInputs).isInstanceOf(UtxoFilteredData.Audit.class);
        UtxoFilteredData.Audit<StateRef> auditInputs
                = (UtxoFilteredData.Audit<StateRef>) txInputs;
        Assertions.assertThat(auditInputs.getSize()).isEqualTo(2);
        Assertions.assertThat(auditInputs.getValues()).hasSize(1);
        Assertions.assertThat(auditInputs.getValues().get(1).getTransactionId()).isEqualTo(hash);
    }
}
