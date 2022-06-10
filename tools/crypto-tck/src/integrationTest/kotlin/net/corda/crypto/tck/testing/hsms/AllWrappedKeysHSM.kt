package net.corda.crypto.tck.testing.hsms

import net.corda.crypto.core.aes.WrappingKey
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceExtensions
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.SigningWrappedSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureSpec
import java.security.KeyPairGenerator

class AllWrappedKeysHSM(
    config: AllWrappedKeysHSMConfiguration,
    schemeMetadata: CipherSchemeMetadata,
    digestService: DigestService
) : AbstractHSM(config.userName, schemeMetadata, digestService), CryptoService {

    override val extensions: List<CryptoServiceExtensions> = listOf(
        CryptoServiceExtensions.REQUIRE_WRAPPING_KEY,
        CryptoServiceExtensions.DELETE_KEYS
    )

    override val supportedSchemes: Map<KeyScheme, List<SignatureSpec>> = supportedSchemesMap

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) {
        logger.info("createWrappingKey(masterKeyAlias={}, failIfExists={})", masterKeyAlias, failIfExists)
        if (masterKeys[masterKeyAlias] != null) {
            if (failIfExists) {
                throw IllegalStateException(
                    "There is an existing key with the alias: $masterKeyAlias"
                )
            } else {
                logger.info(
                    "Wrapping with alias '$masterKeyAlias' already exists, " +
                            "continue as normal as failIfExists is false"
                )
            }
        } else {
            val wrappingKey = WrappingKey.generateWrappingKey(schemeMetadata)
            masterKeys[masterKeyAlias] = wrappingKey
        }
    }

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedKey {
        logger.info(
            "generateKeyPair(alias={},masterKeyAlias={},scheme={})",
            spec.alias,
            spec.masterKeyAlias,
            spec.keyScheme.codeName
        )
        require (!spec.masterKeyAlias.isNullOrBlank()) {
            "The masterKeyAlias is not specified"
        }
        require (isSupported(spec.keyScheme)) {
            "Unsupported signature scheme: ${spec.keyScheme.codeName}"
        }
        val wrappingKey = masterKeys[spec.masterKeyAlias!!]
            ?: throw IllegalStateException("The ${spec.masterKeyAlias} is not created yet.")
        val keyPairGenerator = KeyPairGenerator.getInstance(
            spec.keyScheme.algorithmName,
            provider(spec.keyScheme)
        )
        if (spec.keyScheme.algSpec != null) {
            keyPairGenerator.initialize(spec.keyScheme.algSpec, schemeMetadata.secureRandom)
        } else if (spec.keyScheme.keySize != null) {
            keyPairGenerator.initialize(spec.keyScheme.keySize!!, schemeMetadata.secureRandom)
        }
        val keyPair = keyPairGenerator.generateKeyPair()
        return GeneratedWrappedKey(
            publicKey = keyPair.public,
            keyMaterial = wrappingKey.wrap(keyPair.private),
            encodingVersion = 1
        )
    }

    override fun sign(spec: SigningSpec, data: ByteArray, context: Map<String, String>): ByteArray {
        require (spec is SigningWrappedSpec) {
            "The service supports only ${SigningWrappedSpec::class.java}"
        }
        require (!spec.masterKeyAlias.isNullOrBlank()) {
            "The masterKeyAlias is not specified"
        }
        logger.debug {
            "sign(masterKeyAlias=${spec.masterKeyAlias}, keyScheme=${spec.keyScheme.codeName})"
        }
        val wrappingKey = masterKeys[spec.masterKeyAlias!!]
            ?: throw IllegalStateException("The ${spec.masterKeyAlias} is not created yet.")
        val privateKey = wrappingKey.unwrap(spec.keyMaterial)
        return sign(spec, privateKey, data)
    }

    override fun delete(alias: String, context: Map<String, String>) {
        throw Error("Just to test that the tests will not break.")
    }
}