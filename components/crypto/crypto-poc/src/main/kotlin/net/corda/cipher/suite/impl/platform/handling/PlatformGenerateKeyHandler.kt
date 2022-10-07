package net.corda.cipher.suite.impl.platform.handling

import net.corda.cipher.suite.impl.platform.PlatformCipherSuiteMetadata
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.providers.generation.GenerateKeyHandler
import net.corda.v5.cipher.suite.providers.generation.GeneratedKey
import net.corda.v5.cipher.suite.providers.generation.GeneratedWrappedKey
import net.corda.v5.cipher.suite.providers.generation.KeyGenerationSpec
import java.security.KeyPairGenerator

class PlatformGenerateKeyHandler(
    private val suiteMetadata: PlatformCipherSuiteMetadata,
    private val keyMap: SoftKeyMap,
) : GenerateKeyHandler {
    companion object {
        private val logger = contextLogger()
    }

    override val rank: Int = 0

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedKey {
        require(suiteMetadata.supportedSigningSchemes.containsKey(spec.keyScheme)) {
            "Unsupported key scheme: ${spec.keyScheme.codeName}"
        }
        logger.info(
            "generateKeyPair(alias={},scheme={})",
            spec.alias,
            spec.keyScheme.codeName
        )
        val keyPairGenerator = KeyPairGenerator.getInstance(
            spec.keyScheme.algorithmName,
            suiteMetadata.providerFor(spec.keyScheme)
        )
        if (spec.keyScheme.algSpec != null) {
            keyPairGenerator.initialize(spec.keyScheme.algSpec, suiteMetadata.secureRandom)
        } else if (spec.keyScheme.keySize != null) {
            keyPairGenerator.initialize(spec.keyScheme.keySize, suiteMetadata.secureRandom)
        }
        val keyPair = keyPairGenerator.generateKeyPair()
        val privateKeyMaterial = keyMap.wrapPrivateKey(keyPair)
        return GeneratedWrappedKey(
            publicKey = keyPair.public,
            keyMaterial = privateKeyMaterial.keyMaterial,
            encodingVersion = privateKeyMaterial.encodingVersion,
            masterKeyAlias = privateKeyMaterial.masterKeyAlias
        )
    }
}