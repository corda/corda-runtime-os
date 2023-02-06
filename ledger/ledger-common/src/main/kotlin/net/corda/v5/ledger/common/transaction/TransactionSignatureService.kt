package net.corda.v5.ledger.common.transaction

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.merkle.MerkleProof
import java.security.PublicKey

/**
 * TransactionSignatureService can be used to sign and verify transaction signatures.
 * It supports both single and batch signatures.
 */

interface TransactionSignatureService {
    /**
     * Signs a transaction id with all the available keys.
     *
     * @param transaction The transaction to be signed.
     * @param publicKeys Public keys that correspond to the private keys which should be attempted to sign with.
     *
     * @returns Resulting signatures.
     *
     * @throws TransactionNoAvailableKeysException If none of the keys are available.
     */
    @Suspendable
    fun sign(transaction: TransactionWithMetadata, publicKeys: Iterable<PublicKey>): List<DigitalSignatureAndMetadata>

    /**
     * Signs a list of transactions with each the available keys.
     * It creates one batch signature for each available keys.
     * Then returns the signatures for each transaction with a [MerkleProof] to prove that they are included in the batch.
     *
     * @param transactions The transactions to be signed.
     * @param publicKeys Public keys that correspond to the private keys which should be attempted to sign with.
     *
     * @returns List of signatures for each supplied transaction.
     *          The outer list will always be of the same size and in the same order as the supplied [transactions].
     *
     * @throws TransactionNoAvailableKeysException If none of the keys are available.
     */
    @Suspendable
    fun signBatch(
        transactions: List<TransactionWithMetadata>,
        publicKeys: Iterable<PublicKey>
    ): List<List<DigitalSignatureAndMetadata>>

    /**
     * Verifies a signature against a transaction.
     * The underlying verification service signals the verification failures with different exceptions.
     * [DigitalSignatureVerificationService]
     *
     * @param transaction The original transaction.
     * @param signatureWithMetadata The signature to be verified.
     *
     */
    fun verifySignature(transaction: TransactionWithMetadata, signatureWithMetadata: DigitalSignatureAndMetadata)
}