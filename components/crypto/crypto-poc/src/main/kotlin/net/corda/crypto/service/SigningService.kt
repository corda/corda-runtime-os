package net.corda.crypto.service

import net.corda.v5.cipher.suite.KeyScheme
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

interface SigningService {
    fun generateKeyPair(
        tenantId: String,
        alias: String?,
        externalId: String?,
        scheme: KeyScheme,
        context: Map<String, String>
    ): PublicKey
    fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        metadata: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey
}