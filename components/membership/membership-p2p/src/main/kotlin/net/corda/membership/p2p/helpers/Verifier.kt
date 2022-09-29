package net.corda.membership.p2p.helpers

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.SignatureVerificationService

class Verifier(
    private val signature: CryptoSignatureWithKey,
    private val signatureVerificationService: SignatureVerificationService,
    keyEncodingService: KeyEncodingService,
    tenantId: String,
    cryptoOpsClient: CryptoOpsClient,
) : CryptoAction(
    tenantId,
    keyEncodingService.decodePublicKey(signature.publicKey.array()),
    cryptoOpsClient,
) {
    fun verify(date: ByteArray) {
        signatureVerificationService.verify(
            publicKey,
            spec,
            signature.bytes.array(),
            date
        )
    }
}
