package net.corda.v5.ledger.consensual.transaction;

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata;
import net.corda.v5.application.crypto.DigitalSignatureMetadata;
import net.corda.v5.crypto.DigitalSignature;
import net.corda.v5.ledger.consensual.ConsensualState;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConsensualTransactionBuilderJavaApiTest {
    private final ConsensualTransactionBuilder consensualTransactionBuilder = mock(ConsensualTransactionBuilder.class);
    private final DigitalSignature.WithKey signature = new DigitalSignature.WithKey(mock(PublicKey.class), "0".getBytes(), Map.of());
    private final DigitalSignatureMetadata signatureMetadata = new DigitalSignatureMetadata(Instant.now(), Map.of());
    private final DigitalSignatureAndMetadata signatureWithMetaData = new DigitalSignatureAndMetadata(signature, signatureMetadata);

    @Test
    public void getStates() {
        ConsensualState consensualState = mock(ConsensualState.class);
        when(consensualTransactionBuilder.getStates()).thenReturn(List.of(consensualState));

        List<ConsensualState> result = consensualTransactionBuilder.getStates();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(List.of(consensualState));
        verify(consensualTransactionBuilder, times(1)).getStates();
    }

    @Test
    public void withStatesOne() {
        final ConsensualState consensualState = mock(ConsensualState.class);
        final ConsensualTransactionBuilder mockTransactionBuilder = mock(ConsensualTransactionBuilder.class);
        when(consensualTransactionBuilder.withStates(consensualState)).thenReturn(mockTransactionBuilder);

        final ConsensualTransactionBuilder result = consensualTransactionBuilder.withStates(consensualState);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(mockTransactionBuilder);
        verify(consensualTransactionBuilder, times(1)).withStates(consensualState);
    }

    @Test
    public void withStatesTwo() {
        final ConsensualState consensualState1 = mock(ConsensualState.class);
        final ConsensualState consensualState2 = mock(ConsensualState.class);
        final ConsensualTransactionBuilder mockTransactionBuilder = mock(ConsensualTransactionBuilder.class);
        when(consensualTransactionBuilder.withStates(consensualState1, consensualState2))
                .thenReturn(mockTransactionBuilder);

        final ConsensualTransactionBuilder result = consensualTransactionBuilder
                .withStates(consensualState1, consensualState2);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(mockTransactionBuilder);
        verify(consensualTransactionBuilder, times(1))
                .withStates(consensualState1, consensualState2);
    }


    @Test
    public void signWithZeroKey() {
        final ConsensualSignedTransaction mockSignedTransaction = mock(ConsensualSignedTransaction.class);
        when(consensualTransactionBuilder.sign()).thenReturn(mockSignedTransaction);

        final ConsensualSignedTransaction result = consensualTransactionBuilder.sign();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(mockSignedTransaction);
        verify(consensualTransactionBuilder, times(1)).sign();
    }
    @Test
    public void signWithOneKey() {
        final PublicKey publicKey = mock(PublicKey.class);
        final ConsensualSignedTransaction mockSignedTransaction = mock(ConsensualSignedTransaction.class);
        when(consensualTransactionBuilder.sign(publicKey)).thenReturn(mockSignedTransaction);

        final ConsensualSignedTransaction result = consensualTransactionBuilder.sign(publicKey);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(mockSignedTransaction);
        verify(consensualTransactionBuilder, times(1)).sign(publicKey);
    }
    @Test
    public void signWithTwoKeys() {
        final PublicKey publicKey1 = mock(PublicKey.class);
        final PublicKey publicKey2 = mock(PublicKey.class);
        final ConsensualSignedTransaction mockSignedTransaction = mock(ConsensualSignedTransaction.class);
        when(consensualTransactionBuilder.sign(publicKey1, publicKey2)).thenReturn(mockSignedTransaction);

        final ConsensualSignedTransaction result = consensualTransactionBuilder.sign(publicKey1, publicKey2);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(mockSignedTransaction);
        verify(consensualTransactionBuilder, times(1)).sign(publicKey1, publicKey2);
    }
    @Test
    public void signWithListOfKeys() {
        final List<PublicKey> publicKeyList = Arrays.asList(mock(PublicKey.class), mock(PublicKey.class));
        final ConsensualSignedTransaction mockSignedTransaction = mock(ConsensualSignedTransaction.class);
        when(consensualTransactionBuilder.sign(publicKeyList)).thenReturn(mockSignedTransaction);

        final ConsensualSignedTransaction result = consensualTransactionBuilder.sign(publicKeyList);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(mockSignedTransaction);
        verify(consensualTransactionBuilder, times(1)).sign(publicKeyList);
    }


}