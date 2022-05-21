package net.corda.crypto.service.impl.hsm.soft

import net.corda.crypto.impl.SignatureInstances
import net.corda.crypto.persistence.soft.SoftCryptoKeyCache
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
import java.security.PrivateKey
import java.security.Provider
import javax.crypto.Cipher

open class SoftCryptoService(
    private val cache: SoftCryptoKeyCache,
    private val schemeMetadata: CipherSchemeMetadata,
    private val digestService: DigestService
) : CryptoService {
    companion object {
        private val logger = contextLogger()

        fun produceSupportedSchemes(schemeMetadata: CipherSchemeMetadata): List<KeyScheme> =
            mutableListOf<KeyScheme>().apply {
                addIfSupported(schemeMetadata, RSA_CODE_NAME)
                addIfSupported(schemeMetadata, ECDSA_SECP256K1_CODE_NAME)
                addIfSupported(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
                addIfSupported(schemeMetadata, EDDSA_ED25519_CODE_NAME)
                addIfSupported(schemeMetadata, SPHINCS256_CODE_NAME)
                addIfSupported(schemeMetadata, SM2_CODE_NAME)
                addIfSupported(schemeMetadata, GOST3410_GOST3411_CODE_NAME)
            }

        private fun MutableList<KeyScheme>.addIfSupported(
            schemeMetadata: CipherSchemeMetadata,
            codeName: String
        ) {
            if (schemeMetadata.schemes.any { it.codeName.equals(codeName, true) }) {
                add(schemeMetadata.findKeyScheme(codeName))
            }
        }
    }

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    private val supportedSchemes = produceSupportedSchemes(schemeMetadata).associateBy {
        it.codeName
    }

    override fun requiresWrappingKey(): Boolean = true

    override fun supportedSchemes(): List<KeyScheme> = supportedSchemes.values.toList()

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) = try {
        logger.info("createWrappingKey(masterKeyAlias={}, failIfExists={})", masterKeyAlias, failIfExists)
        cache.act {
            if (it.findWrappingKey(masterKeyAlias) != null) {
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
                it.saveWrappingKey(masterKeyAlias, wrappingKey, failIfExists)
            }
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
        val wrappingKey = cache.act { it.findWrappingKey(spec.masterKeyAlias!!) }
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
            "sign(wrappedKey.masterKeyAlias={}, wrappedKey.signatureScheme={})",
            spec.masterKeyAlias,
            spec.keyScheme.codeName
        )
        val wrappingKey = cache.act { it.findWrappingKey(spec.masterKeyAlias!!) }
            ?: throw CryptoServiceBadRequestException("The ${spec.masterKeyAlias} is not created yet.")
        val privateKey = wrappingKey.unwrap(spec.keyMaterial)
        sign(spec, privateKey, data)
    } catch (e: CryptoServiceException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceException("Cannot sign using the key with wrapped private key", e)
    }

    private fun sign(spec: SigningSpec, privateKey: PrivateKey, data: ByteArray): ByteArray {
        if (!isSupported(spec.keyScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${spec.keyScheme.codeName}")
        }
        if (data.isEmpty()) {
            throw CryptoServiceBadRequestException("Signing of an empty array is not permitted.")
        }
        val signatureBytes = if (spec.signatureSpec.precalculateHash && spec.keyScheme.algorithmName == "RSA") {
            // when the hash is precalculated and the key is RSA the actual sign operation is encryption
            val cipher = Cipher.getInstance(spec.signatureSpec.signatureName, provider(spec.keyScheme))
            cipher.init(Cipher.ENCRYPT_MODE, privateKey)
            val signingData = spec.signatureSpec.getSigningData(digestService, data)
            cipher.doFinal(signingData)
        } else {
            signatureInstances.withSignature(spec.keyScheme, spec.signatureSpec) {
                if (spec.signatureSpec.params != null) {
                    it.setParameter(spec.signatureSpec.params)
                }
                it.initSign(privateKey, schemeMetadata.secureRandom)
                val signingData = spec.signatureSpec.getSigningData(digestService, data)
                it.update(signingData)
                it.sign()
            }
        }
        return signatureBytes
    }

    private fun isSupported(scheme: KeyScheme): Boolean = supportedSchemes.containsKey(scheme.codeName)

    private fun provider(scheme: KeyScheme): Provider = schemeMetadata.providers.getValue(scheme.providerName)
}
