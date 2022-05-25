package net.corda.v5.crypto

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.OpaqueBytes
import java.security.PublicKey

/** A wrapper around a digital signature. */
@CordaSerializable
open class DigitalSignature(bytes: ByteArray) : OpaqueBytes(bytes) {
    /**
     * A digital signature that identifies who the public key is owned by.
     *
     * @param by a public key which corresponding private key was used to sign the data (as the [CompositeKey]
     * for a notary may contain several key which is not actually owned by the current node).
     * @param bytes signature.
     * @param context the context which was passed to the signing operation.
     */
    open class WithKey(
        val by: PublicKey,
        bytes: ByteArray,
        val context: Map<String, String>
    ) : DigitalSignature(bytes)
}
