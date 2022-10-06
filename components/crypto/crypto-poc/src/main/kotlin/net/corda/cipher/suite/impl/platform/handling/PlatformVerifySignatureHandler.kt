package net.corda.cipher.suite.impl.platform.handling

import net.corda.cipher.suite.impl.platform.SignatureInstances
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.providers.verification.VerifySignatureHandler
import net.corda.v5.cipher.suite.scheme.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import java.security.PublicKey

class PlatformVerifySignatureHandler(
    private val metadata: PlatformCipherSuiteMetadata
) : VerifySignatureHandler {
    companion object {
        private val logger = contextLogger()
    }

    private val signatureInstances = SignatureInstances(metadata)

    override val rank: Int = 0

    override fun isValid(
        scheme: KeyScheme,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray
    ): Boolean {
        logger.debug {
            "isValid(publicKey=${publicKey.publicKeyId()},signatureSpec=${signatureSpec.signatureName})"
        }
        return isValid(publicKey, metadata.findKeyScheme(publicKey), signatureSpec, signatureData, clearData)
    }

    private fun isValid(
        publicKey: PublicKey,
        scheme: KeyScheme,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray
    ): Boolean {
        require(metadata.supportedSigningSchemes.contains(scheme)) {
            "Unsupported key/algorithm for codeName: ${scheme.codeName}"
        }
        require(signatureData.isNotEmpty()) {
            "Signature data is empty!"
        }
        require(clearData.isNotEmpty()) {
            "Clear data is empty, nothing to verify!"
        }
        return signatureInstances.withSignature(scheme, signatureSpec) { signature ->
            signatureSpec.getParamsSafely()?.let { params -> signature.setParameter(params) }
            signature.initVerify(publicKey)
            signature.update(clearData)
            signature.verify(signatureData)
        }
    }
}