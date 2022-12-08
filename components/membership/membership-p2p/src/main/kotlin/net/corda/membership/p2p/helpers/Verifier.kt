package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SignatureSpec

class Verifier(
    private val signatureVerificationService: SignatureVerificationService,
    private val keyEncodingService: KeyEncodingService,
) {
    companion object {
        const val SIGNATURE_SPEC = "corda.membership.signature.spec"
    }
    fun verify(signature: CryptoSignatureWithKey, data: ByteArray) {
        val publicKey = keyEncodingService.decodePublicKey(signature.publicKey.array())
        val spec = signature.context
            .items
            .firstOrNull {
                it.key == SIGNATURE_SPEC
            }?.value
            ?: throw CordaRuntimeException("Can not find signature spec")
        signatureVerificationService.verify(
            publicKey,
            SignatureSpec(spec),
            signature.bytes.array(),
            data
        )
    }
}
