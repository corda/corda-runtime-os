package net.corda.cipher.suite.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.crypto.cipher.suite.getParamsSafely
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.impl.SignatureInstances
import net.corda.crypto.impl.getSigningData
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.utilities.debug
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory
import java.security.PublicKey
import javax.crypto.Cipher

@Component(
    service = [ SignatureVerificationService::class, UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class ],
    scope = PROTOTYPE
)
class SignatureVerificationServiceImpl @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = DigestService::class)
    private val hashingService: DigestService
) : SignatureVerificationService,
    UsedByFlow, UsedByPersistence, UsedByVerification, SingletonSerializeAsToken {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    override fun verify(
        originalData: ByteArray,
        signatureData: ByteArray,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec
    ) {
        logger.debug {
            "verify(publicKey=${publicKey.publicKeyId()},signatureSpec=${signatureSpec.signatureName})"
        }
        val result = try {
            !isValid(originalData, signatureData, publicKey, signatureSpec, schemeMetadata.findKeyScheme(publicKey))
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoSignatureException("Signature Verification failed!", e)
        }
        if (result) {
            throw CryptoSignatureException("Signature Verification failed!")
        }
    }

    override fun verify(
        originalData: ByteArray,
        signatureData: ByteArray,
        publicKey: PublicKey,
        digest: DigestAlgorithmName
    ) {
        logger.debug {
            "verify(publicKey=${publicKey.publicKeyId()},digest=${digest.name})"
        }
        val signatureSpec = schemeMetadata.inferSignatureSpec(publicKey, digest)
        require(signatureSpec != null) {
            "Failed to infer the signature spec for key=${publicKey.publicKeyId()} " +
                    " (${schemeMetadata.findKeyScheme(publicKey).codeName}:${digest.name})"
        }
        val result = try {
            !isValid(originalData, signatureData, publicKey, signatureSpec, schemeMetadata.findKeyScheme(publicKey))
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoSignatureException("Signature Verification failed!", e)
        }
        if (result) {
            throw CryptoSignatureException("Signature Verification failed!")
        }
    }

    override fun isValid(
        originalData: ByteArray,
        signatureData: ByteArray,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec
    ): Boolean {
        logger.debug {
            "isValid(publicKey=${publicKey.publicKeyId()},signatureSpec=${signatureSpec.signatureName})"
        }
        return isValid(originalData, signatureData, publicKey, signatureSpec, schemeMetadata.findKeyScheme(publicKey))
    }

    override fun isValid(
        originalData: ByteArray,
        signatureData: ByteArray,
        publicKey: PublicKey,
        digest: DigestAlgorithmName
    ): Boolean {
        logger.debug {
            "isValid(publicKey=${publicKey.publicKeyId()},digest=${digest.name})"
        }
        val signatureSpec = schemeMetadata.inferSignatureSpec(publicKey, digest)
        require(signatureSpec != null) {
            "Failed to infer the signature spec for key=${publicKey.publicKeyId()} " +
                    " (${schemeMetadata.findKeyScheme(publicKey).codeName}:${digest.name})"
        }
        return isValid(originalData, signatureData, publicKey, signatureSpec, schemeMetadata.findKeyScheme(publicKey))
    }

    private fun isValid(
        originalData: ByteArray,
        signatureData: ByteArray,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        scheme: KeyScheme
    ): Boolean {
        require(schemeMetadata.schemes.contains(scheme)) {
            "Unsupported key/algorithm for codeName: ${scheme.codeName}"
        }
        require(signatureData.isNotEmpty()) {
            "Signature data is empty!"
        }
        require(originalData.isNotEmpty()) {
            "Clear data is empty, nothing to verify!"
        }
        val signingData = signatureSpec.getSigningData(hashingService, originalData)
        return if (signatureSpec is CustomSignatureSpec && scheme.algorithmName == "RSA") {
            val cipher = Cipher.getInstance(
                signatureSpec.signatureName,
                schemeMetadata.providers.getValue(scheme.providerName)
            )
            cipher.init(Cipher.DECRYPT_MODE, publicKey)
            cipher.doFinal(signatureData).contentEquals(signingData)
        } else {
            signatureInstances.withSignature(scheme, signatureSpec) { signature ->
                signatureSpec.getParamsSafely()?.let { params -> signature.setParameter(params) }
                signature.initVerify(publicKey)
                signature.update(signingData)
                signature.verify(signatureData)
            }
        }
    }
}
