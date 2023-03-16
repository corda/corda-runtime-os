package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey

/**
 * This interface adds [WireTransaction] to interface [UtxoSignedTransaction] so that there is possible conversion
 * to and from [SignedTransactionContainer].
 * And some methods what the Finality flows use internally.
 */
@CordaSerializable
interface UtxoSignedTransactionInternal: UtxoSignedTransaction {
    val wireTransaction: WireTransaction

    /**
     * Adds a signature to the current [UtxoSignedTransactionInternal].
     *
     * @param signature The signature to be added to the [UtxoSignedTransactionInternal].
     *
     * @return Returns a new [UtxoSignedTransactionInternal] containing the new signature.
     */
    fun addSignature(signature: DigitalSignatureAndMetadata): UtxoSignedTransactionInternal

    /**
     * Crosschecks the missing signatures with the available keys and signs the transaction with their intersection
     * if there are any. (Disabled until crypto support becomes available.)
     *
     * @return Returns the new [UtxoSignedTransactionInternal] containing the applied signature and a
     *          list of the added signatures.
     */
    @Suspendable
    fun addMissingSignatures(): Pair<UtxoSignedTransactionInternal, List<DigitalSignatureAndMetadata>>

    /**
     * Gets the missing signatories from the current [UtxoSignedTransactionInternal].
     *
     * @return Returns a [Set] of [PublicKey] representing the missing signatories from the current [UtxoSignedTransactionInternal].
     */
    @Suspendable
    fun getMissingSignatories(): Set<PublicKey>

    /**
     * Verify the signatories' signatures and check if there are any missing one.
     * It ignores the non-signatory signatures! (including the notary's)
     *
     * @throws TransactionSignatureException if any signatures are invalid or missing.
     */
    @Suspendable
    fun verifySignatorySignatures()

    /**
     * Verify if notary has signed the transaction.
     * The signature itself is also verified!
     *
     * @throws TransactionSignatureException if notary signatures is missing.
     */
    @Suspendable
    fun verifyAttachedNotarySignature()

    /**
     * Verify if a signature
     *  - is the transaction's notary's
     *  - is valid
     *
     * @throws CordaRuntimeException if not owned by the notary // todo: change this to TransactionSignatureException
     * @throws TransactionSignatureException if invalid
     */
    @Suspendable
    fun verifyNotarySignature(signature: DigitalSignatureAndMetadata)

    /**
     * Verify if a signature is one of the signatories is valid.
     * It does not throw if the signature is not one of the signatories!
     *
     * @throws TransactionSignatureException if signature is owned by a signature, and it is not valid.
     */
    @Suspendable
    fun verifySignatorySignature(signature: DigitalSignatureAndMetadata)
}
