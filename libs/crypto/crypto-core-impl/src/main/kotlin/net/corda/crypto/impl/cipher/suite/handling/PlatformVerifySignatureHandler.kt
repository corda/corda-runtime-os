package net.corda.crypto.impl.cipher.suite.handling

import net.corda.crypto.core.service.PlatformCipherSuiteMetadata
import net.corda.crypto.impl.cipher.suite.SignatureInstances
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.handlers.verification.VerifySignatureHandler
import net.corda.v5.cipher.suite.KeyScheme
import net.corda.v5.cipher.suite.getParamsSafely
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import java.security.PublicKey

class PlatformVerifySignatureHandler(
    private val suiteMetadata: PlatformCipherSuiteMetadata
) : VerifySignatureHandler {
    companion object {
        private val logger = contextLogger()
    }

    private val signatureInstances = SignatureInstances(suiteMetadata)

    override val rank: Int = 0

    override fun isValid(
        scheme: KeyScheme,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray,
        metadata: ByteArray
    ): Boolean {
        logger.debug {
            "isValid(publicKey=${publicKey.publicKeyId()},signatureSpec=${signatureSpec.signatureName})"
        }
        require(suiteMetadata.supportedSigningSchemes.contains(scheme)) {
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
            if(metadata.isNotEmpty()) {
                signature.update(metadata)
            }
            signature.update(clearData)
            signature.verify(signatureData)
        }
    }
}