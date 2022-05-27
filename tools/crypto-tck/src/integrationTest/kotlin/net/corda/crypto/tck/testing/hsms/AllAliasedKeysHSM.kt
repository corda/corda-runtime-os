package net.corda.crypto.tck.testing.hsms

import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CRYPTO_TENANT_ID
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningAliasSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.SigningWrappedSpec
import net.corda.v5.cipher.suite.computeHSMAlias
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Provider
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AllAliasedKeysHSM(
    userName: String,
    schemeMetadata: CipherSchemeMetadata,
    digestService: DigestService,
    supportedSchemeCodes: List<String> = listOf(
        RSA_CODE_NAME,
        ECDSA_SECP256K1_CODE_NAME,
        ECDSA_SECP256R1_CODE_NAME,
        EDDSA_ED25519_CODE_NAME,
        SPHINCS256_CODE_NAME,
        SM2_CODE_NAME,
        GOST3410_GOST3411_CODE_NAME
    )
) : AbstractHSM(supportedSchemeCodes, schemeMetadata, digestService), CryptoService {
    companion object {
        private val logger = contextLogger()
    }

    init {
        logger.info("Created ${AllAliasedKeysHSM::class.simpleName} for $userName")
    }

    private val keyPairs = ConcurrentHashMap<String, KeyPair>()

    override fun requiresWrappingKey(): Boolean = false

    override fun supportedSchemes(): List<KeyScheme> = supportedSchemes

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) {
        throw CryptoServiceException("Operation is not supported")
    }

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedKey = try {
        logger.info(
            "generateKeyPair(alias={},masterKeyAlias={},scheme={})",
            spec.alias,
            spec.masterKeyAlias,
            spec.keyScheme.codeName
        )
        if (!isSupported(spec.keyScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${spec.keyScheme.codeName}")
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
            secret = spec.secret ?: UUID.randomUUID().toString().toByteArray()
        )
        keyPairs[hsmAlias] = keyPair
        GeneratedPublicKey(
            publicKey = keyPair.public,
            hsmAlias = hsmAlias
        )
    } catch (e: CryptoServiceException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceException(
            "Cannot generate wrapped key pair with scheme: ${spec.keyScheme.codeName}",
            e
        )
    }

    override fun sign(spec: SigningSpec, data: ByteArray, context: Map<String, String>): ByteArray = try {
        if (spec !is SigningAliasSpec) {
            throw CryptoServiceBadRequestException("The service supports only ${SigningWrappedSpec::class.java}")
        }
        if (spec.hsmAlias.isBlank()) {
            throw CryptoServiceBadRequestException("The hsmAlias is not specified")
        }
        logger.debug(
            "sign(hsmAlias={}, keyScheme={})",
            spec.hsmAlias,
            spec.keyScheme.codeName
        )
        val privateKey = keyPairs.getValue(spec.hsmAlias).private
        sign(spec, privateKey, data)
    } catch (e: CryptoServiceException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceException("Cannot sign using the key with wrapped private key", e)
    }

    private fun isSupported(scheme: KeyScheme): Boolean = supportedSchemes.any { it.codeName == scheme.codeName }

    private fun provider(scheme: KeyScheme): Provider = schemeMetadata.providers.getValue(scheme.providerName)
}