package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.security.PublicKey

class Verifier(
    private val signatureVerificationService: SignatureVerificationService,
    private val keyEncodingService: KeyEncodingService,
) {
    fun verify(
        acceptableKeys: Collection<PublicKey>,
        signature: CryptoSignatureWithKey,
        signatureSpecAvro: CryptoSignatureSpec,
        data: ByteArray,
    ) {
        val key = keyEncodingService.decodePublicKey(signature.publicKey.array())
        if (!acceptableKeys.contains(key)) {
            throw IllegalArgumentException("The signature public key is not one of the acceptable keys.")
        }
        verify(
            key,
            signature.bytes.array(),
            signatureSpecAvro,
            data,
        )
    }

    fun verify(
        publicKey: PublicKey,
        signature: ByteArray,
        signatureSpecAvro: CryptoSignatureSpec,
        data: ByteArray,
    ) {
        val signatureSpec = signatureSpecAvro.signatureName?.let {
            SignatureSpecImpl(it)
        } ?: throw CordaRuntimeException("Can not find signature spec")
        signatureVerificationService.verify(
            data,
            signature,
            publicKey,
            signatureSpec,
        )
    }
}
