package net.corda.crypto.core.service

import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

interface SignatureVerificationService {
    fun isValid(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray,
        metadata: ByteArray
    ): Boolean
}