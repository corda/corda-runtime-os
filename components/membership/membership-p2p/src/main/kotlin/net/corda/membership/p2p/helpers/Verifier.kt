package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SignatureSpec

class Verifier(
    private val signatureVerificationService: SignatureVerificationService,
    private val keyEncodingService: KeyEncodingService,
) {
    fun verify(signature: CryptoSignatureWithKey, signatureSpecAvro: CryptoSignatureSpec, data: ByteArray) {
        val publicKey = keyEncodingService.decodePublicKey(signature.publicKey.array())
        val signatureSpec = signatureSpecAvro.signatureName?.let {
            // Maybe use `SignatureSpecService` here to check signature spec should be one of compatible ones
            SignatureSpec(it)
        } ?: throw CordaRuntimeException("Can not find signature spec")
        signatureVerificationService.verify(
            data,
            signature.bytes.array(),
            publicKey,
            signatureSpec
        )
    }
}
