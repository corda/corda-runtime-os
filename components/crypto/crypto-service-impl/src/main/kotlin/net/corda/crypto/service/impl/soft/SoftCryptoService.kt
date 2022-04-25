package net.corda.crypto.service.impl.soft

import net.corda.crypto.impl.SignatureInstances
import net.corda.crypto.persistence.SoftCryptoKeyCache
import net.corda.crypto.core.aes.WrappingKey
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.SigningWrappedSpec
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SM2_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SPHINCS256_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.DigestService
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
    }

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    private val supportedSchemes: Map<String, SignatureScheme> = mutableMapOf<String, SignatureScheme>().apply {
        addIfSupported(RSA_CODE_NAME)
        addIfSupported(ECDSA_SECP256K1_CODE_NAME)
        addIfSupported(ECDSA_SECP256R1_CODE_NAME)
        addIfSupported(EDDSA_ED25519_CODE_NAME)
        addIfSupported(SPHINCS256_CODE_NAME)
        addIfSupported(SM2_CODE_NAME)
        addIfSupported(GOST3410_GOST3411_CODE_NAME)
    }

    override fun requiresWrappingKey(): Boolean = true

    override fun supportedSchemes(): Array<SignatureScheme> = supportedSchemes.values.toTypedArray()

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) = try {
        logger.debug("createWrappingKey(masterKeyAlias={}, failIfExists={})", masterKeyAlias, failIfExists)
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
                val wrappingKey = WrappingKey.createWrappingKey(schemeMetadata)
                it.saveWrappingKey(masterKeyAlias, wrappingKey, failIfExists)
            }
        }
    } catch (e: CryptoServiceException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceException("Failed create wrapping key with alias $masterKeyAlias", e)
    }

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedKey = try {
        logger.debug(
            "generateKeyPair(masterKeyAlias={}, signatureScheme={})",
            spec.masterKeyAlias,
            spec.signatureScheme
        )
        if(spec.masterKeyAlias.isNullOrBlank()) {
            throw CryptoServiceBadRequestException("The masterKeyAlias is not specified")
        }
        if (!isSupported(spec.signatureScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${spec.signatureScheme.codeName}")
        }
        val wrappingKey = cache.act { it.findWrappingKey(spec.masterKeyAlias!!) }
            ?: throw CryptoServiceBadRequestException("The ${spec.masterKeyAlias} is not created yet.")
        val keyPairGenerator = KeyPairGenerator.getInstance(
            spec.signatureScheme.algorithmName,
            provider(spec.signatureScheme)
        )
        if (spec.signatureScheme.algSpec != null) {
            keyPairGenerator.initialize(spec.signatureScheme.algSpec, schemeMetadata.secureRandom)
        } else if (spec.signatureScheme.keySize != null) {
            keyPairGenerator.initialize(spec.signatureScheme.keySize!!, schemeMetadata.secureRandom)
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
            "Cannot generate wrapped key pair with scheme: ${spec.signatureScheme.codeName}",
            e
        )
    }

    override fun sign(spec: SigningSpec, data: ByteArray, context: Map<String, String>): ByteArray = try {
        if(spec !is SigningWrappedSpec) {
            throw CryptoServiceBadRequestException("The service supports only ${SigningWrappedSpec::class.java}")
        }
        if(spec.masterKeyAlias.isNullOrBlank()) {
            throw CryptoServiceBadRequestException("The masterKeyAlias is not specified")
        }
        logger.debug(
            "sign(wrappedKey.masterKeyAlias={}, wrappedKey.signatureScheme={})",
            spec.masterKeyAlias,
            spec.signatureScheme
        )
        val wrappingKey = cache.act { it.findWrappingKey(spec.masterKeyAlias!!) }
            ?: throw CryptoServiceBadRequestException("The ${spec.masterKeyAlias} is not created yet.")
        val privateKey = wrappingKey.unwrap(spec.keyMaterial)
        sign(spec.signatureScheme, privateKey, data)
    } catch (e: CryptoServiceException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceException("Cannot sign using the key with wrapped private key", e)
    }

    private fun sign(signatureScheme: SignatureScheme, privateKey: PrivateKey, data: ByteArray): ByteArray {
        if (!isSupported(signatureScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${signatureScheme.codeName}")
        }
        if (data.isEmpty()) {
            throw CryptoServiceBadRequestException("Signing of an empty array is not permitted.")
        }
        val signatureSpec = signatureScheme.signatureSpec
        val signatureBytes = if (signatureSpec.precalculateHash && signatureScheme.algorithmName == "RSA") {
            // when the hash is precalculated and the key is RSA the actual sign operation is encryption
            val cipher = Cipher.getInstance(signatureSpec.signatureName, provider(signatureScheme))
            cipher.init(Cipher.ENCRYPT_MODE, privateKey)
            val signingData = signatureSpec.getSigningData(digestService, data)
            cipher.doFinal(signingData)
        } else {
            signatureInstances.withSignature(signatureScheme) {
                if (signatureScheme.signatureSpec.params != null) {
                    it.setParameter(signatureScheme.signatureSpec.params)
                }
                it.initSign(privateKey, schemeMetadata.secureRandom)
                val signingData = signatureSpec.getSigningData(digestService, data)
                it.update(signingData)
                it.sign()
            }
        }
        return signatureBytes
    }

    private fun isSupported(scheme: SignatureScheme): Boolean = supportedSchemes.containsKey(scheme.codeName)

    private fun provider(scheme: SignatureScheme): Provider = schemeMetadata.providers.getValue(scheme.providerName)

    private fun MutableMap<String, SignatureScheme>.addIfSupported(codeName: String) {
        if (schemeMetadata.schemes.any { it.codeName.equals(codeName, true) }) {
            put(codeName, schemeMetadata.findSignatureScheme(codeName))
        }
    }
}
