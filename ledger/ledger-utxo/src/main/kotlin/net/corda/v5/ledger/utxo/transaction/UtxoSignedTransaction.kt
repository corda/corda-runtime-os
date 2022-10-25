package net.corda.v5.ledger.utxo.transaction

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import java.security.PublicKey

/**
 * Defines a signed UTXO transaction.
 *
 * @property id The ID of the transaction.
 * @property signatures The signatures that have been applied to the transaction.
 */
@DoNotImplement
@CordaSerializable
interface UtxoSignedTransaction {

    val id: SecureHash
    val signatures: List<DigitalSignatureAndMetadata>

    /**
     * Adds the specified signatures to the current [UtxoSignedTransaction].
     *
     * @param signatures The signatures and metadata to add to the current [UtxoSignedTransaction].
     * @return Returns a new [UtxoSignedTransaction] containing the applied signatures.
     */
    @Suspendable
    fun addSignatures(signatures: Iterable<DigitalSignatureAndMetadata>): UtxoSignedTransaction

    /**
     * Adds the specified signature to the current [UtxoSignedTransaction].
     *
     * @param signatures The signatures and metadata to add to the current [UtxoSignedTransaction].
     * @return Returns a new [UtxoSignedTransaction] containing the applied signature.
     */
    @Suspendable
    fun addSignatures(vararg signatures: DigitalSignatureAndMetadata): UtxoSignedTransaction

    /**
     * Gets the missing signatories from the current [UtxoSignedTransaction].
     *
     * @return Returns a [Set] of [PublicKey] representing the missing signatories from the current [UtxoSignedTransaction].
     */
    @Suspendable
    fun getMissingSignatories(): Set<PublicKey>

    /**
     * Converts the current [UtxoSignedTransaction] into a [UtxoLedgerTransaction].
     *
     * @return Returns a [UtxoLedgerTransaction] from the current signed transaction.
     */
    fun toLedgerTransaction(): UtxoLedgerTransaction
}
