package net.corda.crypto.impl.components

import net.corda.crypto.impl.SignatureInstances
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.publicKeyId
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey
import java.security.SignatureException
import javax.crypto.Cipher

@Component(service = [SignatureVerificationService::class])
class SignatureVerificationServiceImpl @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = DigestService::class)
    private val hashingService: DigestService
) : SignatureVerificationService {
    companion object {
        private val logger = contextLogger()
    }

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    override fun verify(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray
    ) {
        logger.debug("verify(publicKey={},signatureSpec={})", publicKey.publicKeyId(), signatureSpec.signatureName)
        if (!isValid(publicKey, schemeMetadata.findKeyScheme(publicKey), signatureSpec, signatureData, clearData)) {
            throw SignatureException("Signature Verification failed!")
        }
    }

    override fun verify(
        publicKey: PublicKey,
        digest: DigestAlgorithmName,
        signatureData: ByteArray,
        clearData: ByteArray
    ) {
        logger.debug("verify(publicKey={},digest={})", publicKey.publicKeyId(), digest.name)
        val signatureSpec = schemeMetadata.inferSignatureSpec(publicKey, digest)
        require(signatureSpec != null) {
            "Failed to infer the signature spec for key=${publicKey.publicKeyId()} " +
                    " (${schemeMetadata.findKeyScheme(publicKey).codeName}:${digest.name})"
        }
        if (!isValid(publicKey, schemeMetadata.findKeyScheme(publicKey), signatureSpec, signatureData, clearData)) {
            throw SignatureException("Signature Verification failed!")
        }
    }

    override fun isValid(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray
    ): Boolean {
        logger.debug("isValid(publicKey={},signatureSpec={})", publicKey.publicKeyId(), signatureSpec.signatureName)
        return isValid(publicKey, schemeMetadata.findKeyScheme(publicKey), signatureSpec, signatureData, clearData)
    }

    override fun isValid(
        publicKey: PublicKey,
        digest: DigestAlgorithmName,
        signatureData: ByteArray,
        clearData: ByteArray
    ): Boolean {
        logger.debug("isValid(publicKey={},digest={})", publicKey.publicKeyId(), digest.name)
        val signatureSpec = schemeMetadata.inferSignatureSpec(publicKey, digest)
        require(signatureSpec != null) {
            "Failed to infer the signature spec for key=${publicKey.publicKeyId()} " +
                    " (${schemeMetadata.findKeyScheme(publicKey).codeName}:${digest.name})"
        }
        return isValid(publicKey, schemeMetadata.findKeyScheme(publicKey), signatureSpec, signatureData, clearData)
    }

    private fun isValid(
        publicKey: PublicKey,
        scheme: KeyScheme,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray
    ): Boolean {
        require(schemeMetadata.schemes.contains(scheme)) {
            "Unsupported key/algorithm for codeName: ${scheme.codeName}"
        }
        require(signatureData.isNotEmpty()) {
            "Signature data is empty!"
        }
        require(clearData.isNotEmpty()) {
            "Clear data is empty, nothing to verify!"
        }
        val signingData = signatureSpec.getSigningData(hashingService, clearData)
        return if (signatureSpec.precalculateHash && scheme.algorithmName == "RSA") {
            val cipher = Cipher.getInstance(
                signatureSpec.signatureName,
                schemeMetadata.providers.getValue(scheme.providerName)
            )
            cipher.init(Cipher.DECRYPT_MODE, publicKey)
            cipher.doFinal(signatureData).contentEquals(signingData)
        } else {
            signatureInstances.withSignature(scheme, signatureSpec) {
                if(signatureSpec.params != null) {
                    it.setParameter(signatureSpec.params)
                }
                it.initVerify(publicKey)
                it.update(signingData)
                it.verify(signatureData)
            }
        }
    }
}