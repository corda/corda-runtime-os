package net.corda.v5.ledger.common.transaction;

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata;
import net.corda.v5.application.crypto.DigitalSignatureVerificationService;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.crypto.merkle.MerkleProof;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;

/**
 * TransactionSignatureService can be used to sign and verify transaction signatures.
 * It supports both single and batch signatures.
 */
public interface TransactionSignatureService {

    /**
     * Signs a transaction id with all the available keys.
     *
     * @param transaction The transaction to be signed.
     * @param publicKeys Public keys that correspond to the private keys which should be attempted to sign with.
     *
     * @return Resulting signatures.
     *
     * @throws TransactionNoAvailableKeysException If none of the keys are available.
     */
    @Suspendable
    @NotNull
    List<DigitalSignatureAndMetadata> sign(
            @NotNull final TransactionWithMetadata transaction,
            @NotNull final Iterable<PublicKey> publicKeys
    );

    /**
     * Signs a list of transactions with each the available keys.
     * It creates one batch signature for each available keys.
     * Then returns the signatures for each transaction with a {@link MerkleProof} to prove that they are included in the batch.
     *
     * @param transactions The transactions to be signed.
     * @param publicKeys Public keys that correspond to the private keys which should be attempted to sign with.
     *
     * @return {@link List} of signatures for each supplied transaction.
     *          The outer list will always be of the same size and in the same order as the supplied [transactions].
     *
     * @throws TransactionNoAvailableKeysException If none of the keys are available.
     */
    @Suspendable
    @NotNull
    List<List<DigitalSignatureAndMetadata>> signBatch(
            @NotNull final List<TransactionWithMetadata> transactions,
            @NotNull final Iterable<PublicKey> publicKeys
    );

    /**
     * Verifies a signature against a transaction.
     * The underlying verification service signals the verification failures with different exceptions.
     * {@link DigitalSignatureVerificationService}
     *
     * @param transaction The original transaction.
     * @param signatureWithMetadata The signature to be verified.
     * @throws RuntimeException if the signature could not be verified.
     */
    void verifySignature(
            @NotNull final TransactionWithMetadata transaction,
            @NotNull final DigitalSignatureAndMetadata signatureWithMetadata
    );
}
