package net.corda.v5.application.crypto

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*

/**
 * Responsible for storing and using private keys to sign things. An implementation of this may, for example, call out
 * to a hardware security module that enforces various auditing and frequency-of-use requirements.
 *
 * Corda provides an instance of [DigitalSignatureVerificationService] to flows via property injection.
 */
@DoNotImplement
interface SigningService {

    /**
     * Using the provided signing [PublicKey], internally looks up the matching [PrivateKey] and signs the data.
     *
     * @param bytes The data to sign over using the chosen key.
     * @param publicKey The [PublicKey] partner to an internally held [PrivateKey], either derived from the node's
     * primary identity, or previously generated via the freshKey method. If the [PublicKey] is actually
     * a [CompositeKey], the first leaf signing key hosted by the node is used.
     * @param signatureSpec The [SignatureSpec] to use when producing this signature.
     *
     * @return A [DigitalSignature.WithKey] representing the signed data and the [PublicKey] that belongs to the
     * same [KeyPair] as the [PrivateKey] that signed the data.
     *
     * @throws [CordaRuntimeException] If the input key is not a member of [keys].
     */
    @Suspendable
    fun sign(bytes: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec): DigitalSignature.WithKey

    /**
     * Gets a set of signing keys to look into and returns a mapping of the requested signing keys to signing keys
     * found to be owned by the caller. In case of [CompositeKey] it maps the composite key with the firstly found
     * composite key leaf.
     *
     * @param keys The signing keys to look into.
     * @return A mapping of requested signing keys to found signing keys to be owned by the caller or null if not found to be owned.
     */
    @Suspendable
    fun findMySigningKeys(keys: Set<PublicKey>): Map<PublicKey, PublicKey?>
}
