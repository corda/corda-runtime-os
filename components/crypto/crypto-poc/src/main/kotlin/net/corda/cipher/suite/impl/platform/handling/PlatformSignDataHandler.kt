package net.corda.cipher.suite.impl.platform.handling

import net.corda.cipher.suite.impl.platform.SignatureInstances
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.providers.signing.SignDataHandler
import net.corda.v5.cipher.suite.providers.signing.SigningSpec
import net.corda.v5.cipher.suite.providers.signing.SigningWrappedSpec
import net.corda.v5.cipher.suite.scheme.KeySchemeCapability
import java.security.PrivateKey

class PlatformSignDataHandler(
    private val metadata: PlatformCipherSuiteMetadata,
    private val keyMap: SoftKeyMap,
) : SignDataHandler {
    companion object {
        private val logger = contextLogger()
    }

    private val signatureInstances = SignatureInstances(metadata)

    override val rank: Int = 0

    override fun sign(spec: SigningSpec, data: ByteArray, context: Map<String, String>): ByteArray {
        require(spec is SigningWrappedSpec) {
            "The service supports only ${SigningWrappedSpec::class.java}"
        }
        require(data.isNotEmpty()) {
            "Signing of an empty array is not permitted."
        }
        require(metadata.supportedSigningSchemes.containsKey(spec.keyScheme)) {
            "Unsupported key scheme: ${spec.keyScheme.codeName}"
        }
        require(spec.keyScheme.canDo(KeySchemeCapability.SIGN)) {
            "Key scheme: ${spec.keyScheme.codeName} cannot be used for signing."
        }
        logger.debug { "sign(spec=$spec)" }
        return sign(spec, keyMap.getPrivateKey(spec.publicKey, spec.keyMaterialSpec), data)
    }

    private fun sign(spec: SigningSpec, privateKey: PrivateKey, data: ByteArray): ByteArray =
        signatureInstances.withSignature(spec.keyScheme, spec.signatureSpec) { signature ->
            spec.signatureSpec.getParamsSafely()?.let { params -> signature.setParameter(params) }
            signature.initSign(privateKey, metadata.secureRandom)
            signature.update(data)
            signature.sign()
        }
}