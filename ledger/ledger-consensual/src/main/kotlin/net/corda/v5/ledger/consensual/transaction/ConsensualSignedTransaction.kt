package net.corda.v5.ledger.consensual.transaction

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.SecureHash
import java.security.PublicKey

/**
 * Defines a signed Consensual transaction.
 *
 *  Comparing with [ConsensualLedgerTransaction]:
 *  - It does not have access to the deserialized details.
 *  - It has direct access to the signatures.
 *  - It does not require a serializer.
 *
 * [ConsensualSignedTransaction] wraps the wire representation of the transaction. It contains one or more signatures,
 * each one for a public key (including composite keys) that is mentioned inside a transaction state.
 * [ConsensualSignedTransaction] is frequently passed around the network and stored. The identity of a transaction is
 * the hash of Merkle root of the wrapped wire representation, therefore if you are storing data keyed by wire
 * representations hash be aware that multiple different [ConsensualSignedTransaction]s may map to the same key (and
 * they could be different in important ways, like validity!). The signatures on a ConsensualSignedTransaction
 * might be invalid or missing: the type does not imply validity.
 * A transaction ID should be the hash of the wrapped wire representation's Merkle tree root.
 * Thus adding or removing a signature does not change it.
 */
@DoNotImplement
@CordaSerializable
interface ConsensualSignedTransaction {
    /**
     * @property id The ID of the transaction.
     */
    val id: SecureHash

    /**
     * @property signatures The signatures that have been applied to the transaction.
     */
    val signatures: List<DigitalSignatureAndMetadata>

    /**
     * Converts the current [ConsensualSignedTransaction] into a [ConsensualLedgerTransaction].
     *
     * @return Returns a [ConsensualLedgerTransaction] from the current signed transaction.
     */
    fun toLedgerTransaction(): ConsensualLedgerTransaction

    /**
     * Sign the current [ConsensualSignedTransaction] with the specified key.
     *
     * @param publicKey The private counterpart of the specified public key will be used for signing the
     *      [ConsensualSignedTransaction].
     * @return Returns a new [ConsensualSignedTransaction] containing the applied signature.
     */
    fun addSignature(publicKey: PublicKey): ConsensualSignedTransaction

    /**
     * Gets the signing keys for any missing transaction signatures.
     *
     * @return Returns a [Set] of [PublicKey] representing the signing keys for any missing transaction signatures.
     */
    fun getMissingSigningKeys(): Set<PublicKey>
}


