package net.corda.v5.crypto

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.OpaqueBytes
import java.security.PublicKey

/** A wrapper around a digital signature. */
@CordaSerializable
open class DigitalSignature(bytes: ByteArray) : OpaqueBytes(bytes) {
    /** A digital signature that identifies who the public key is owned by. */
    open class WithKey(val by: PublicKey, bytes: ByteArray) : DigitalSignature(bytes)
}
