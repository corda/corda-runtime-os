package net.corda.ledger.lib.utxo.flow.impl.transaction

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey

/**
 * This interface adds [WireTransaction] to interface [UtxoSignedTransaction] so that there is possible conversion
 * to and from [SignedTransactionContainer].
 * And some methods what the Finality flows use internally.
 */
@CordaSerializable
interface UtxoSignedTransactionInternal : UtxoSignedTransaction {
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
     * It does not verify the available ones.
     *
     * @return Returns a [Set] of [PublicKey] representing the missing signatories from the current [UtxoSignedTransactionInternal].
     */
    fun getMissingSignatories(): Set<PublicKey>

    /**
     * Verify if notary has signed the transaction.
     * It checks both the existence and the validity of that signature.
     *
     * @throws TransactionSignatureException if notary signatures is missing or invalid.
     */
    fun verifyAttachedNotarySignature()

    /**
     * Verify if a signature
     *  - is made by the notary of the transaction
     *  - is valid
     *
     * @throws TransactionSignatureException if the signature is invalid or if not made by the notary
     */
    fun verifyNotarySignature(signature: DigitalSignatureAndMetadata)

    /**
     * Verify if a signature of a signatory is valid.
     * It does not throw if the signature is not one of the signatories regardless of the validity since
     * the public key is not available, the validity cannot be verified.
     *
     * @throws TransactionSignatureException if signature is owned by a signatory, and it is not valid.
     */
    fun verifySignatorySignature(signature: DigitalSignatureAndMetadata)
}
