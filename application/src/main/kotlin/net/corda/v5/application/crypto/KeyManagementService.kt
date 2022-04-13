package net.corda.v5.application.crypto

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*

/**
 * The [KeyManagementService] is responsible for storing and using private keys to sign things. An implementation of this may, for example,
 * call out to a hardware security module that enforces various auditing and frequency-of-use requirements.
 */
@DoNotImplement
interface KeyManagementService : CordaServiceInjectable, CordaFlowInjectable {
    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage.
     *
     * @return The [PublicKey] of the generated [KeyPair].
     */
    @Suspendable
    fun freshKey(): PublicKey

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage. Associates the public key to an external Id.
     *
     * @return The [PublicKey] of the generated [KeyPair].
     */
    @Suspendable
    fun freshKey(externalId: UUID): PublicKey

    /**
     * Filters the input [PublicKey]s down to a collection of keys that this node owns (has private keys for).
     *
     * @param candidateKeys The [PublicKey]s to filter.
     *
     * @return A collection of [PublicKey]s that this node owns.
     */
    fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey>

    /**
     * Using the provided signing [PublicKey], internally looks up the matching [PrivateKey] and signs the data.
     *
     * @param bytes The data to sign over using the chosen key.
     *
     * @param publicKey The [PublicKey] partner to an internally held [PrivateKey], either derived from the node's primary identity, or
     * previously generated via the [freshKey] method. If the [PublicKey] is actually a [CompositeKey], the first leaf signing key hosted by
     * the node is used.
     *
     * @return A [DigitalSignature.WithKey] representing the signed data and the [PublicKey] that belongs to the same [KeyPair] as the
     * [PrivateKey] that signed the data.
     *
     * @throws IllegalArgumentException If the input key is not a member of [keys].
     */
    @Suspendable
    fun sign(bytes: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey

    /**
     * Using the provided signing [PublicKey] internally looks up the matching [PrivateKey] and signs the [SignableData].
     *
     * @param signableData A wrapper over transaction id (Merkle root) and signature metadata.
     *
     * @param publicKey The [PublicKey] partner to an internally held [PrivateKey], either derived from the node's primary identity, or
     * previously generated via the [freshKey] method. If the [PublicKey] is actually a [CompositeKey], the first leaf signing key hosted by
     * the node is used.
     *
     * @return A [DigitalSignatureAndMetadata] representing the signed data and the [PublicKey] that belongs to the same [KeyPair] as the
     * [PrivateKey] that signed the data.
     *
     * @throws IllegalArgumentException If the input key is not a member of [keys].
     */
    @Suspendable
    fun sign(signableData: SignableData, publicKey: PublicKey): DigitalSignatureAndMetadata
}
