package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.transaction.TransactionVerificationException
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import java.security.PublicKey

/**
 * This interface adds [WireTransaction] to interface [ConsensualSignedTransaction] so that there is possible conversion
 * to and from [SignedTransactionContainer].
 * And some methods what the Finality flows use internally.
 */
interface ConsensualSignedTransactionInternal: ConsensualSignedTransaction {
    val wireTransaction: WireTransaction

    /**
     * Sign the current [ConsensualSignedTransactionInternal] with the specified key.
     *
     * @param publicKey The private counterpart of the specified public key will be used for signing the
     *      [ConsensualSignedTransaction].
     * @return Returns the new [ConsensualSignedTransactionInternal] containing the applied signature and the signature itself.
     */
    @Suspendable
    fun sign(publicKey: PublicKey): Pair<ConsensualSignedTransactionInternal, DigitalSignatureAndMetadata>

    /**
     * Adds a signature to the current [ConsensualSignedTransactionInternal].
     *
     * @param signature The signature to be added to the [ConsensualSignedTransactionInternal].
     *
     * @return Returns a new [ConsensualSignedTransactionInternal] containing the new signature.
     */
    fun addSignature(signature: DigitalSignatureAndMetadata): ConsensualSignedTransactionInternal

    /**
     * Crosschecks the missing signatures with the available keys and signs the transaction with their intersection
     * if there are any. (Disabled until crypto support becomes available.)
     *
     * @return Returns the new [ConsensualSignedTransactionInternal] containing the applied signature and a
     *          list of added signatures.
     */
    @Suspendable
    fun addMissingSignatures(): Pair<ConsensualSignedTransactionInternal, List<DigitalSignatureAndMetadata>>{
        TODO("Not implemented yet")
    }

    /**
     * Gets the missing signatories from the current [ConsensualSignedTransactionInternal].
     *
     * @return Returns a [Set] of [PublicKey] representing the missing signatories from the current [ConsensualSignedTransactionInternal].
     */
    @Suspendable
    fun getMissingSignatories(): Set<PublicKey>

    /**
     * Verify all available signatures and whether there are any missing ones.
     *
     * @throws TransactionVerificationException if any signatures are invalid or missing.
     */
    @Suspendable
    fun verifySignatures()
}
