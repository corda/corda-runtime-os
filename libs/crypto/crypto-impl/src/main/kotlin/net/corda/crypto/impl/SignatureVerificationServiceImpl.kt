package net.corda.crypto.impl

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import java.security.PublicKey
import java.security.SignatureException
import javax.crypto.Cipher

open class SignatureVerificationServiceImpl(
    private val schemeMetadata: CipherSchemeMetadata,
    private val hashingService: DigestService
) : SignatureVerificationService {

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    override fun verify(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray
    ) =
        doVerify(schemeMetadata.findSignatureScheme(publicKey), signatureSpec, publicKey, signatureData, clearData)

    override fun verify(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray) =
        doVerify(schemeMetadata.findSignatureScheme(publicKey), null, publicKey, signatureData, clearData)

    override fun isValid(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray
    ): Boolean =
        isValid(publicKey, schemeMetadata.findSignatureScheme(publicKey), signatureSpec, signatureData, clearData)

    override fun isValid(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean =
        isValid(publicKey, schemeMetadata.findSignatureScheme(publicKey), null, signatureData, clearData)

    @Suppress("ThrowsCount")
    private fun doVerify(
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec?,
        publicKey: PublicKey,
        signatureData: ByteArray,
        clearData: ByteArray
    ) {
        require(isSupported(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.codeName}"
        }
        val verificationResult = isValid(publicKey, signatureScheme, signatureSpec, signatureData, clearData)
        if (!verificationResult) {
            throw SignatureException("Signature Verification failed!")
        }
    }

    private fun isValid(
        publicKey: PublicKey,
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec?,
        signatureBytes: ByteArray,
        clearData: ByteArray
    ): Boolean {
        require(isSupported(signatureScheme)) {
            "Unsupported key/algorithm for codeName: ${signatureScheme.codeName}"
        }
        require(signatureBytes.isNotEmpty()) {
            "Signature data is empty!"
        }
        require(clearData.isNotEmpty()) {
            "Clear data is empty, nothing to verify!"
        }
        val effectiveSignatureScheme = if(signatureSpec != null) {
            signatureScheme.copy(signatureSpec = signatureSpec)
        } else {
            signatureScheme
        }
        val signingData = effectiveSignatureScheme.signatureSpec.getSigningData(hashingService, clearData)
        return if (effectiveSignatureScheme.signatureSpec.precalculateHash && signatureScheme.algorithmName == "RSA") {
            val cipher = Cipher.getInstance(
                effectiveSignatureScheme.signatureSpec.signatureName,
                    schemeMetadata.providers.getValue(signatureScheme.providerName)
            )
            cipher.init(Cipher.DECRYPT_MODE, publicKey)
            cipher.doFinal(signatureBytes).contentEquals(signingData)
        } else {
            signatureInstances.withSignature(effectiveSignatureScheme) { signature ->
                signature.initVerify(publicKey)
                signature.update(signingData)
                signature.verify(signatureBytes)
            }
        }
    }

    private fun isSupported(scheme: SignatureScheme): Boolean {
        return schemeMetadata.schemes.contains(scheme)
    }
}