package net.corda.crypto.tck.testing.hsms

import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.CRYPTO_TENANT_ID
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceExtensions
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.PlatformDigestService
import net.corda.v5.cipher.suite.SharedSecretSpec
import net.corda.v5.cipher.suite.SigningAliasSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.SigningWrappedSpec
import net.corda.v5.cipher.suite.computeHSMAlias
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AllAliasedKeysHSM(
    config: AllAliasedKeysHSMConfiguration,
    schemeMetadata: CipherSchemeMetadata,
    digestService: PlatformDigestService
) : AbstractHSM(config.userName, schemeMetadata, digestService), CryptoService {
    private val keyPairs = ConcurrentHashMap<String, KeyPair>()

    override val extensions: List<CryptoServiceExtensions> = listOf(
        CryptoServiceExtensions.DELETE_KEYS
    )

    override val supportedSchemes: Map<KeyScheme, List<SignatureSpec>> = supportedSchemesMap

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) =
        throw UnsupportedOperationException("Creating wrapping keys is not supported")

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedKey {
        logger.info(
            "generateKeyPair(alias={},masterKeyAlias={},scheme={})",
            spec.alias,
            spec.masterKeyAlias,
            spec.keyScheme.codeName
        )
        require (isSupported(spec.keyScheme)) {
            "Unsupported signature scheme: ${spec.keyScheme.codeName}"
        }
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
        val hsmAlias = computeHSMAlias(
            tenantId = context.getValue(CRYPTO_TENANT_ID),
            alias = spec.alias ?: UUID.randomUUID().toString(),
            secret = UUID.randomUUID().toString().toByteArray()
        )
        keyPairs[hsmAlias] = keyPair
        return GeneratedPublicKey(
            publicKey = keyPair.public,
            hsmAlias = hsmAlias
        )
    }

    override fun sign(spec: SigningSpec, data: ByteArray, context: Map<String, String>): ByteArray {
        require (spec is SigningAliasSpec) {
            "The service supports only ${SigningWrappedSpec::class.java}"
        }
        require (spec.hsmAlias.isNotBlank()) {
            "The hsmAlias is not specified"
        }
        logger.debug {
            "sign(hsmAlias=${spec.hsmAlias}, keyScheme=${spec.keyScheme.codeName})"
        }
        val privateKey = keyPairs[spec.hsmAlias]?.private
            ?: throw IllegalArgumentException("The key ${spec.hsmAlias} is not found.")
        return sign(spec, privateKey, data)
    }

    override fun delete(alias: String, context: Map<String, String>): Boolean {
        throw Error("Just to test that the tests will not break.")
    }

    override fun deriveSharedSecret(spec: SharedSecretSpec, context: Map<String, String>): ByteArray =
        throw UnsupportedOperationException("Deriving shared secret is not supported")
}