package net.corda.v5.ledger.obsolete.transactions;

import net.corda.v5.base.types.OpaqueBytes;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.obsolete.contracts.Command;
import net.corda.v5.ledger.obsolete.contracts.CommandData;
import net.corda.v5.ledger.obsolete.contracts.Contract;
import net.corda.v5.ledger.obsolete.contracts.TimeWindow;
import net.corda.v5.ledger.obsolete.crypto.TransactionDigestAlgorithmNames;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class TraversableTransactionJavaApiTest {

    private final TraversableTransaction traversableTransaction = mock(TraversableTransaction.class);
    private final List<ComponentGroup> componentGroups = List.of(new ComponentGroup(4, List.of(new OpaqueBytes("test".getBytes()))));
    private final TransactionDigestAlgorithmNames transactionDigestAlgorithmNames = new TransactionDigestAlgorithmNames();
    private final List<SecureHash> secureHashes = List.of(SecureHash.parse("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A"));
    private final PublicKey publicKey = mock(PublicKey.class);
    private final List<Command<TestContract.Create>> commands = List.of(new Command<>(new TestContract.Create(), publicKey));
    private final TimeWindow timeWindow = mock(TimeWindow.class);

    @Test
    public void getComponentGroups() {
        doReturn(componentGroups).when(traversableTransaction).getComponentGroups();

        List<ComponentGroup> result = traversableTransaction.getComponentGroups();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(componentGroups);
    }

    @Test
    public void getTransactionDigestAlgorithmNames() {
        doReturn(transactionDigestAlgorithmNames).when(traversableTransaction).getTransactionDigestAlgorithmNames();

        TransactionDigestAlgorithmNames result = traversableTransaction.getTransactionDigestAlgorithmNames();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(transactionDigestAlgorithmNames);
    }

    @Test
    public void getAttachments() {
        doReturn(secureHashes).when(traversableTransaction).getAttachments();

        List<SecureHash> result = traversableTransaction.getAttachments();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHashes);
    }

    @Test
    public void getCommands() {
        doReturn(commands).when(traversableTransaction).getCommands();

        List<Command<?>> result = traversableTransaction.getCommands();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(commands);
    }

    @Test
    public void getTimeWindow() {
        doReturn(timeWindow).when(traversableTransaction).getTimeWindow();

        TimeWindow result = traversableTransaction.getTimeWindow();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(timeWindow);
    }

    @Test
    public void getAvailableComponentGroups() {
        final List<List<?>> components = List.of(secureHashes, commands);
        doReturn(components).when(traversableTransaction).getAvailableComponentGroups();

        List<List<Object>> result = traversableTransaction.getAvailableComponentGroups();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(components);
    }

    static class TestContract implements Contract {

        static class Create implements CommandData {
        }

        @Override
        public void verify(@NotNull LedgerTransaction tx) {

        }
    }
}
