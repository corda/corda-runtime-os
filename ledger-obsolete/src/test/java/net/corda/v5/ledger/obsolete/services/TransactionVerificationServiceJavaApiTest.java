package net.corda.v5.ledger.obsolete.services;

import net.corda.v5.ledger.obsolete.transactions.SignedTransaction;
import net.corda.v5.ledger.obsolete.transactions.TransactionWithSignatures;
import org.junit.jupiter.api.Test;

import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TransactionVerificationServiceJavaApiTest {

    private final TransactionVerificationService transactionVerificationService = mock(TransactionVerificationService.class);
    private final TransactionWithSignatures transactionWithSignatures = mock(TransactionWithSignatures.class);
    private final SignedTransaction signedTransaction = mock(SignedTransaction.class);
    private final PublicKey publicKey = mock(PublicKey.class);

    @Test
    public void verifyWithSignedTransactionAndCheckSufficientSignatures() throws SignatureException, InvalidKeyException {
        transactionVerificationService.verify(signedTransaction, true);
        verify(transactionVerificationService, times(1)).verify(signedTransaction, true);
    }

    @Test
    public void verifyWithSignedTransaction() throws SignatureException, InvalidKeyException {
        transactionVerificationService.verify(signedTransaction);
        verify(transactionVerificationService, times(1)).verify(signedTransaction);
    }

    @Test
    public void verifyRequiredSignatures() throws SignatureException, InvalidKeyException {
        transactionVerificationService.verifyRequiredSignatures(transactionWithSignatures);
        verify(transactionVerificationService, times(1)).verifyRequiredSignatures(transactionWithSignatures);
    }

    @Test
    public void verifySignaturesExceptWithPublicKey() throws SignatureException, InvalidKeyException {
        PublicKey anotherMockPublicKey = mock(PublicKey.class);
        transactionVerificationService.verifySignaturesExcept(transactionWithSignatures, publicKey, anotherMockPublicKey);
        verify(transactionVerificationService, times(1)).verifySignaturesExcept(transactionWithSignatures, publicKey, anotherMockPublicKey);
    }

    @Test
    public void verifySignaturesExceptWithCollectionOfPublicKey() throws SignatureException, InvalidKeyException {
        transactionVerificationService.verifySignaturesExcept(transactionWithSignatures, Collections.singletonList(publicKey));
        verify(transactionVerificationService, times(1)).verifySignaturesExcept(transactionWithSignatures, Collections.singletonList(publicKey));
    }
}
