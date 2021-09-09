package net.corda.v5.ledger.services;

import net.corda.v5.application.crypto.DigitalSignatureAndMeta;
import net.corda.v5.application.crypto.SignatureMetadata;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.transactions.FilteredTransaction;
import net.corda.v5.ledger.transactions.SignedTransaction;
import net.corda.v5.ledger.transactions.TransactionBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TransactionServiceJavaApiTest {

    private final TransactionService transactionService = mock(TransactionService.class);
    private final SignedTransaction signedTransaction = mock(SignedTransaction.class);
    private final TransactionBuilder transactionBuilder = mock(TransactionBuilder.class);
    private final FilteredTransaction filteredTransaction = mock(FilteredTransaction.class);
    private final PublicKey publicKey = mock(PublicKey.class);
    private final SignatureMetadata signatureMetadata = new SignatureMetadata(1);
    private final DigitalSignatureAndMeta digitalSignatureAndMeta = new DigitalSignatureAndMeta(new byte[]{101}, publicKey, signatureMetadata);
    private final SecureHash secureHash = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");

    @Test
    public void recordWithNotifyVaultFirstAndSignedTransaction() {
        SignedTransaction anotherMockedSignedTransaction = mock(SignedTransaction.class);
        transactionService.record(true, signedTransaction, anotherMockedSignedTransaction);
        verify(transactionService, times(1)).record(true, signedTransaction, anotherMockedSignedTransaction);
    }

    @Test
    public void recordWithNotifyVaultAndTransactions() {
        transactionService.record(true, Collections.singletonList(signedTransaction));
        verify(transactionService, times(1)).record(true, Collections.singletonList(signedTransaction));
    }

    @Test
    public void recordWithStatesToRecordAndTransactions() {
        transactionService.record(StatesToRecord.ONLY_RELEVANT, Collections.singletonList(signedTransaction));
        verify(transactionService, times(1)).record(StatesToRecord.ONLY_RELEVANT, Collections.singletonList(signedTransaction));
    }

    @Test
    public void recordWithFirstAndSignedTransaction() {
        SignedTransaction anotherMockedSignedTransaction = mock(SignedTransaction.class);
        transactionService.record(signedTransaction, anotherMockedSignedTransaction);
        verify(transactionService, times(1)).record(signedTransaction, anotherMockedSignedTransaction);
    }

    @Test
    public void recordWithSignedTransaction() {
        transactionService.record(signedTransaction);
        verify(transactionService, times(1)).record(signedTransaction);
    }

    @Test
    public void signWithTransactionBuilderAndPublicKeys() {
        when(transactionService.sign(transactionBuilder, Collections.singletonList(publicKey))).thenReturn(signedTransaction);

        SignedTransaction result = transactionService.sign(transactionBuilder, Collections.singletonList(publicKey));

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(signedTransaction);
    }

    @Test
    public void signWithTransactionBuilderAndPublicKey() {
        when(transactionService.sign(transactionBuilder, publicKey)).thenReturn(signedTransaction);

        SignedTransaction result = transactionService.sign(transactionBuilder, publicKey);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(signedTransaction);
    }

    @Test
    public void signWithTransactionBuilder() {
        when(transactionService.sign(transactionBuilder)).thenReturn(signedTransaction);

        SignedTransaction result = transactionService.sign(transactionBuilder);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(signedTransaction);
    }

    @Test
    public void signWithSignedTransactionAndPublicKey() {
        SignedTransaction anotherMockedSignedTransaction = mock(SignedTransaction.class);
        when(transactionService.sign(anotherMockedSignedTransaction, publicKey)).thenReturn(signedTransaction);

        SignedTransaction result = transactionService.sign(anotherMockedSignedTransaction, publicKey);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(signedTransaction);
    }

    @Test
    public void signWithSignedTransaction() {
        SignedTransaction anotherMockedSignedTransaction = mock(SignedTransaction.class);
        when(transactionService.sign(anotherMockedSignedTransaction)).thenReturn(signedTransaction);

        SignedTransaction result = transactionService.sign(anotherMockedSignedTransaction);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(signedTransaction);
    }

    @Test
    public void createSignatureWithSignedTransactionAndPublicKey() {
        when(transactionService.createSignature(signedTransaction, publicKey)).thenReturn(digitalSignatureAndMeta);

        DigitalSignatureAndMeta result = transactionService.createSignature(signedTransaction, publicKey);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(digitalSignatureAndMeta);
    }

    @Test
    public void createSignatureWithSignedTransaction() {
        when(transactionService.createSignature(signedTransaction)).thenReturn(digitalSignatureAndMeta);

        DigitalSignatureAndMeta result = transactionService.createSignature(signedTransaction);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(digitalSignatureAndMeta);
    }

    @Test
    public void createSignatureWithFilteredTransactionAndPublicKey() {
        when(transactionService.createSignature(filteredTransaction, publicKey)).thenReturn(digitalSignatureAndMeta);

        DigitalSignatureAndMeta result = transactionService.createSignature(filteredTransaction, publicKey);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(digitalSignatureAndMeta);
    }

    @Test
    public void createSignatureWithFilteredTransaction() {
        when(transactionService.createSignature(filteredTransaction)).thenReturn(digitalSignatureAndMeta);

        DigitalSignatureAndMeta result = transactionService.createSignature(filteredTransaction);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(digitalSignatureAndMeta);
    }

    @Test
    public void addNote() {
        String noteTextTest = "test";
        transactionService.addNote(secureHash, noteTextTest);
        verify(transactionService, times(1)).addNote(secureHash, noteTextTest);
    }

    @Test
    public void getNotes() {
        transactionService.getNotes(secureHash);
        verify(transactionService, times(1)).getNotes(secureHash);
    }
}
