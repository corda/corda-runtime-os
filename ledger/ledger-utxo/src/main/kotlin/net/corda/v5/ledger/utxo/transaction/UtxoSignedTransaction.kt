package net.corda.v5.ledger.utxo.transaction

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
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
    val signatures: Set<DigitalSignatureAndMetadata>

    /**
     * Adds the specified signature to the current [UtxoSignedTransaction].
     *
     * @param signature The signature and metadata to add to the current [UtxoSignedTransaction].
     * @return Returns a new [UtxoSignedTransaction] containing the applied signature.
     */
    fun addSignature(signature: DigitalSignatureAndMetadata): UtxoSignedTransaction

    /**
     * Adds the specified signatures to the current [UtxoSignedTransaction].
     *
     * @param signatures The signatures and metadata to add to the current [UtxoSignedTransaction].
     * @return Returns a new [UtxoSignedTransaction] containing the applied signatures.
     */
    fun addSignatures(signatures: Iterable<DigitalSignatureAndMetadata>): UtxoSignedTransaction

    /**
     * Gets the missing signatories from the current [UtxoSignedTransaction].
     *
     * @param serializer The [SerializationService] required to obtain missing signed transaction signatures.
     * @return Returns a [Set] of [PublicKey] representing the missing signatories from the current [UtxoSignedTransaction].
     */
    fun getMissingSignatories(serializer: SerializationService): Set<PublicKey>

    /**
     * Converts the current [UtxoSignedTransaction] into a [UtxoLedgerTransaction].
     *
     * @param serializer The [SerializationService] required to convert the current transaction.
     * @return Returns a [UtxoLedgerTransaction] from the current signed transaction.
     */
    fun toLedgerTransaction(serializer: SerializationService): UtxoLedgerTransaction
}
