package net.corda.membership.p2p.helpers

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.SignatureVerificationService
import net.corda.virtualnode.ShortHash

class VerifierFactory(
    private val signatureVerificationService: SignatureVerificationService,
    private val keyEncodingService: KeyEncodingService,
    private val cryptoOpsClient: CryptoOpsClient,
) {
    fun createVerifier(
        signature: CryptoSignatureWithKey,
        memberId: ShortHash,
    ): Verifier = Verifier(
        signature,
        signatureVerificationService,
        keyEncodingService,
        memberId.value,
        cryptoOpsClient
    )
}
