package net.corda.crypto.impl.service

import net.corda.crypto.core.service.SignatureVerificationService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.CipherSuiteBase
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

@Component(service = [SignatureVerificationService::class])
class SignatureVerificationServiceImpl @Activate constructor(
    @Reference(service = CipherSuiteBase::class)
    private val suite: CipherSuiteBase
) : SignatureVerificationService {
    companion object {
        private val logger = contextLogger()
    }

    override fun isValid(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray,
        metadata: ByteArray
    ): Boolean {
        logger.debug {
            "isValid(publicKey=${publicKey.publicKeyId()},signatureSpec=${signatureSpec.signatureName})"
        }
        val scheme = suite.findKeyScheme(publicKey)
            ?: throw IllegalArgumentException("The scheme ${publicKey.publicKeyId()} is not supported.")
        val handler = suite.findVerifySignatureHandler(scheme.codeName)
            ?: throw IllegalArgumentException("There is no verification handler for the scheme $scheme.")
        return try {
            handler.isValid(scheme, publicKey, signatureSpec, signatureData, clearData, metadata)
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Throwable) {
            throw RuntimeException(e.message, e)
        }
    }
}