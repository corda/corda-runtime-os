package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

class Verifier(
    private val signatureVerificationService: SignatureVerificationService,
    private val keyEncodingService: KeyEncodingService,
) {
    fun verify(
        signature: CryptoSignatureWithKey,
        signatureSpecAvro: CryptoSignatureSpec,
        data: ByteArray
    ) = verify(
        keyEncodingService.decodePublicKey(signature.publicKey.array()),
        signature,
        signatureSpecAvro,
        data
    )


    fun verify(
        publicKey: PublicKey,
        signature: CryptoSignatureWithKey,
        signatureSpecAvro: CryptoSignatureSpec,
        data: ByteArray
    ) {
        val signatureSpec = signatureSpecAvro.signatureName?.let {
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
