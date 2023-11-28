package net.corda.crypto.core

import net.corda.base.internal.OpaqueBytes
import net.corda.v5.crypto.DigitalSignature
import java.security.PublicKey

class DigitalSignatureWithKey(
    val by: PublicKey,
    bytes: ByteArray
) : DigitalSignature, OpaqueBytes(bytes)
