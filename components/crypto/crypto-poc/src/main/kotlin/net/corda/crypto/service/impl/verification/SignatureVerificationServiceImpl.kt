package net.corda.crypto.service.impl.verification

import net.corda.crypto.service.SignatureVerificationService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.AbstractCipherSuite
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import java.security.PublicKey


class SignatureVerificationServiceImpl(
    private val suite: AbstractCipherSuite
) : SignatureVerificationService {
    companion object {
        private val logger = contextLogger()
    }

    override fun isValid(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray
    ): Boolean {
        logger.debug {
            "isValid(publicKey=${publicKey.publicKeyId()},signatureSpec=${signatureSpec.signatureName})"
        }
        val scheme = suite.findKeyScheme(publicKey)
        val handler = suite.findVerifySignatureHandler(scheme.codeName)
        return handler.isValid(scheme, publicKey, signatureSpec, signatureData, clearData)
    }
}