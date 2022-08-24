package net.corda.v5.ledger.obsolete.transactions;

import net.corda.v5.base.types.OpaqueBytes;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.obsolete.contracts.ComponentGroupEnum;
import net.corda.v5.ledger.obsolete.contracts.StateRef;
import net.corda.v5.ledger.obsolete.merkle.PartialMerkleTree;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

public class FilteredTransactionJavaApiTest {

    private final FilteredTransaction filteredTransaction = mock(FilteredTransaction.class);
    private final SecureHash secureHash = SecureHash.parse("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final List<SecureHash> secureHashes = List.of(secureHash);
    private final PartialMerkleTree.PartialTree partialTree = mock(PartialMerkleTree.PartialTree.class);
    private final PartialMerkleTree partialMerkleTree = new PartialMerkleTree(partialTree);
    private final List<FilteredComponentGroup> filteredComponentGroup = List.of(
            new FilteredComponentGroup(1, List.of(new OpaqueBytes("test".getBytes())), secureHashes, partialMerkleTree)
    );
    private final String reason = "There is no reason";

    @Test
    public void getFilteredComponentGroups() {
        doReturn(filteredComponentGroup).when(filteredTransaction).getFilteredComponentGroups();

        List<FilteredComponentGroup> result = filteredTransaction.getFilteredComponentGroups();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(filteredComponentGroup);
    }

    @Test
    public void getGroupHashes() {
        doReturn(secureHashes).when(filteredTransaction).getGroupHashes();

        List<SecureHash> result = filteredTransaction.getGroupHashes();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHashes);
    }

    @Test
    public void verify() {
        filteredTransaction.verify();
        org.mockito.Mockito.verify(filteredTransaction, times(1)).verify();
    }

    @Test
    public void checkWithFun() {
        doReturn(true).when(filteredTransaction).checkWithFun(any());

        boolean result = filteredTransaction.checkWithFun((f1) -> true);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isTrue();
    }

    @Test
    public void checkAllComponentsVisible() {
        filteredTransaction.checkAllComponentsVisible(ComponentGroupEnum.ATTACHMENTS_GROUP);
        org.mockito.Mockito.verify(filteredTransaction, times(1)).checkAllComponentsVisible(ComponentGroupEnum.ATTACHMENTS_GROUP);
    }

    @Test
    public void checkCommandVisibility() {
        final PublicKey publicKey = mock(PublicKey.class);
        filteredTransaction.checkCommandVisibility(publicKey);
        org.mockito.Mockito.verify(filteredTransaction, times(1)).checkCommandVisibility(publicKey);
    }

    @Nested
    public class FilteredComponentGroupJavaApiTest {

        private final List<OpaqueBytes> opaqueBytes = List.of(new OpaqueBytes("test".getBytes()));
        private final FilteredComponentGroup filteredComponentGroup = new FilteredComponentGroup(
                1, opaqueBytes, secureHashes, partialMerkleTree
        );

        @Test
        public void getGroupIndex() {
            int result = filteredComponentGroup.getGroupIndex();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(1);
        }

        @Test
        public void getComponents() {
            List<OpaqueBytes> result = filteredComponentGroup.getComponents();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(opaqueBytes);
        }

        @Test
        public void getNonces() {
            List<SecureHash> result = filteredComponentGroup.getNonces();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(secureHashes);
        }

        @Test
        public void getPartialMerkleTree() {
            PartialMerkleTree result = filteredComponentGroup.getPartialMerkleTree();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(partialMerkleTree);
        }
    }

    @Nested
    public class ComponentVisibilityExceptionJavaApiTest {
        private final ComponentVisibilityException exception = new ComponentVisibilityException(secureHash, reason);

        @Test
        public void getId() {
            SecureHash result = exception.getId();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(secureHash);
        }

        @Test
        public void getReason() {
            String result = exception.getReason();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(reason);
        }
    }

    @Nested
    public class FilteredTransactionVerificationExceptionJavaApiTest {
        private final FilteredTransactionVerificationException exception = new FilteredTransactionVerificationException(secureHash, reason);

        @Test
        public void getId() {
            SecureHash result = exception.getId();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(secureHash);
        }

        @Test
        public void getReason() {
            String result = exception.getReason();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(reason);
        }
    }

    @Nested
    public class ReferenceStateRefJavaApiTest {
        private final StateRef stateRef = new StateRef(secureHash, 1);
        private final ReferenceStateRef referenceStateRef = new ReferenceStateRef(stateRef);

        @Test
        public void getStateRef() {
            StateRef result = referenceStateRef.getStateRef();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(stateRef);
        }
    }
}