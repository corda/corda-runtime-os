package net.corda.cipher.suite.impl.platform.handling

import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.providers.generation.GenerateKeyHandler
import net.corda.v5.cipher.suite.providers.generation.GeneratedKey
import net.corda.v5.cipher.suite.providers.generation.GeneratedWrappedKey
import net.corda.v5.cipher.suite.providers.generation.KeyGenerationSpec
import java.security.KeyPairGenerator

class PlatformGenerateKeyHandler(
    val metadata: PlatformCipherSchemeMetadata,
    private val keyMap: SoftKeyMap,
) : GenerateKeyHandler {
    companion object {
        private val logger = contextLogger()
    }

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedKey {
        require(metadata.supportedSigningSchemes.containsKey(spec.keyScheme)) {
            "Unsupported key scheme: ${spec.keyScheme.codeName}"
        }
        logger.info(
            "generateKeyPair(alias={},masterKeyAlias={},scheme={})",
            spec.alias,
            spec.masterKeyAlias,
            spec.keyScheme.codeName
        )
        val keyPairGenerator = KeyPairGenerator.getInstance(
            spec.keyScheme.algorithmName,
            metadata.providerFor(spec.keyScheme)
        )
        if (spec.keyScheme.algSpec != null) {
            keyPairGenerator.initialize(spec.keyScheme.algSpec, metadata.secureRandom)
        } else if (spec.keyScheme.keySize != null) {
            keyPairGenerator.initialize(spec.keyScheme.keySize!!, metadata.secureRandom)
        }
        val keyPair = keyPairGenerator.generateKeyPair()
        val privateKeyMaterial = keyMap.wrapPrivateKey(keyPair, spec.masterKeyAlias)
        return GeneratedWrappedKey(
            publicKey = keyPair.public,
            keyMaterial = privateKeyMaterial.keyMaterial,
            encodingVersion = privateKeyMaterial.encodingVersion
        )
    }
}