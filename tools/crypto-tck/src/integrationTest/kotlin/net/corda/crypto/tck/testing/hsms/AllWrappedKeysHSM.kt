package net.corda.crypto.tck.testing.hsms

import net.corda.crypto.core.aes.WrappingKey
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.SigningWrappedSpec
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
import java.security.KeyPairGenerator
import java.security.Provider

class AllWrappedKeysHSM(
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
        logger.info("Created ${AllWrappedKeysHSM::class.simpleName} for $userName")
    }

    override fun requiresWrappingKey(): Boolean = true

    override fun supportedSchemes(): List<KeyScheme> = supportedSchemes

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) = try {
        logger.info("createWrappingKey(masterKeyAlias={}, failIfExists={})", masterKeyAlias, failIfExists)
        if (masterKeys[masterKeyAlias] != null) {
            if (failIfExists) {
                throw CryptoServiceBadRequestException(
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
    } catch (e: CryptoServiceException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceException("Failed create wrapping key with alias $masterKeyAlias", e)
    }

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedKey = try {
        logger.info(
            "generateKeyPair(alias={},masterKeyAlias={},scheme={})",
            spec.alias,
            spec.masterKeyAlias,
            spec.keyScheme.codeName
        )
        if (spec.masterKeyAlias.isNullOrBlank()) {
            throw CryptoServiceBadRequestException("The masterKeyAlias is not specified")
        }
        if (!isSupported(spec.keyScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${spec.keyScheme.codeName}")
        }
        val wrappingKey = masterKeys[spec.masterKeyAlias!!]
            ?: throw CryptoServiceBadRequestException("The ${spec.masterKeyAlias} is not created yet.")
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
        GeneratedWrappedKey(
            publicKey = keyPair.public,
            keyMaterial = wrappingKey.wrap(keyPair.private),
            encodingVersion = 1
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
        if (spec !is SigningWrappedSpec) {
            throw CryptoServiceBadRequestException("The service supports only ${SigningWrappedSpec::class.java}")
        }
        if (spec.masterKeyAlias.isNullOrBlank()) {
            throw CryptoServiceBadRequestException("The masterKeyAlias is not specified")
        }
        logger.debug(
            "sign(wrappedKey.masterKeyAlias={}, wrappedKey.keyScheme={})",
            spec.masterKeyAlias,
            spec.keyScheme.codeName
        )
        val wrappingKey = masterKeys[spec.masterKeyAlias!!]
            ?: throw CryptoServiceBadRequestException("The ${spec.masterKeyAlias} is not created yet.")
        val privateKey = wrappingKey.unwrap(spec.keyMaterial)
        sign(spec, privateKey, data)
    } catch (e: CryptoServiceException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceException("Cannot sign using the key with wrapped private key", e)
    }

    private fun isSupported(scheme: KeyScheme): Boolean = supportedSchemes.any { it.codeName == scheme.codeName }

    private fun provider(scheme: KeyScheme): Provider = schemeMetadata.providers.getValue(scheme.providerName)
}