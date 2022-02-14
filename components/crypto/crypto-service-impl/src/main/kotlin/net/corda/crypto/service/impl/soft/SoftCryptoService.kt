package net.corda.crypto.service.impl.soft

import net.corda.crypto.impl.SignatureInstances
import net.corda.crypto.service.impl.persistence.SoftCryptoKeyCache
import net.corda.crypto.persistence.WrappingKey
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.WrappedKeyPair
import net.corda.v5.cipher.suite.WrappedPrivateKey
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
import java.security.PublicKey
import javax.crypto.Cipher

@Suppress("TooManyFunctions")
open class SoftCryptoService(
    private val cache: SoftCryptoKeyCache,
    private val schemeMetadata: CipherSchemeMetadata,
    private val hashingService: DigestService
) : CryptoService {
    companion object {
        private val logger = contextLogger()
    }

    private val signatureInstances = SignatureInstances(schemeMetadata.providers)

    private val supportedSchemes: Array<SignatureScheme>

    private val supportedSchemeCodes: Set<String>

    init {
        val schemes = mutableListOf<SignatureScheme>()
        schemes.addIfSupported(RSA_CODE_NAME)
        schemes.addIfSupported(ECDSA_SECP256K1_CODE_NAME)
        schemes.addIfSupported(ECDSA_SECP256R1_CODE_NAME)
        schemes.addIfSupported(EDDSA_ED25519_CODE_NAME)
        schemes.addIfSupported(SPHINCS256_CODE_NAME)
        schemes.addIfSupported(SM2_CODE_NAME)
        schemes.addIfSupported(GOST3410_GOST3411_CODE_NAME)
        supportedSchemes = schemes.toTypedArray()
        supportedSchemeCodes = supportedSchemes.map { it.codeName }.toSet()
    }

    override fun requiresWrappingKey(): Boolean = true

    override fun supportedSchemes(): Array<SignatureScheme> = supportedSchemes

    override fun supportedWrappingSchemes(): Array<SignatureScheme> = supportedSchemes

    override fun containsKey(alias: String): Boolean {
        logger.debug("containsKey(alias={})", alias)
        return try {
            cache.find(alias) != null
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("Cannot check key existence for alias $alias", e)
        }
    }

    override fun findPublicKey(alias: String): PublicKey? {
        logger.debug("findPublicKey(alias={})", alias)
        return try {
            cache.find(alias)?.publicKey
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("Cannot find public key for alias $alias", e)
        }
    }

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean) {
        logger.debug("createWrappingKey(masterKeyAlias={}, failIfExists={})", masterKeyAlias, failIfExists)
        try {
            if (cache.find(masterKeyAlias) != null) {
                when (failIfExists) {
                    true -> throw CryptoServiceBadRequestException(
                        "There is an existing key with the alias: $masterKeyAlias"
                    )
                    false -> {
                        logger.info(
                            "Wrapping key for alias '$masterKeyAlias' already exists, " +
                                    "continue as normal as failIfExists=$failIfExists"
                        )
                        return
                    }
                }
            }
            val wrappingKey = WrappingKey.createWrappingKey(schemeMetadata)
            cache.save(masterKeyAlias, wrappingKey)
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("Failed create wrapping key with alias $masterKeyAlias", e)
        }
    }

    override fun generateKeyPair(
        alias: String,
        signatureScheme: SignatureScheme,
        context: Map<String, String>
    ): PublicKey {
        logger.debug("generateKeyPair(alias={}, signatureScheme={})", alias, signatureScheme)
        if (!isSupported(signatureScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${signatureScheme.codeName}")
        }
        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                signatureScheme.algorithmName,
                provider(signatureScheme)
            )
            if (signatureScheme.algSpec != null) {
                keyPairGenerator.initialize(signatureScheme.algSpec, schemeMetadata.secureRandom)
            } else if (signatureScheme.keySize != null) {
                keyPairGenerator.initialize(signatureScheme.keySize!!, schemeMetadata.secureRandom)
            }
            val keyPair = keyPairGenerator.generateKeyPair()
            cache.save(alias, keyPair, signatureScheme)
            keyPair.public
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException(
                "Cannot generate key for alias $alias and signature scheme ${signatureScheme.codeName}",
                e
            )
        }
    }

    override fun generateWrappedKeyPair(
        masterKeyAlias: String,
        wrappedSignatureScheme: SignatureScheme,
        context: Map<String, String>
    ): WrappedKeyPair {
        logger.debug(
            "generateWrappedKeyPair(masterKeyAlias={}, wrappedSignatureScheme={})",
            masterKeyAlias,
            wrappedSignatureScheme
        )
        if (!isSupported(wrappedSignatureScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${wrappedSignatureScheme.codeName}")
        }
        return try {
            val wrappingKey = cache.find(masterKeyAlias)?.wrappingKey
                ?: throw CryptoServiceBadRequestException("The $masterKeyAlias is not created yet.")
            val keyPairGenerator = KeyPairGenerator.getInstance(
                wrappedSignatureScheme.algorithmName,
                provider(wrappedSignatureScheme)
            )
            if (wrappedSignatureScheme.algSpec != null) {
                keyPairGenerator.initialize(wrappedSignatureScheme.algSpec, schemeMetadata.secureRandom)
            } else if (wrappedSignatureScheme.keySize != null) {
                keyPairGenerator.initialize(wrappedSignatureScheme.keySize!!, schemeMetadata.secureRandom)
            }
            val keyPair = keyPairGenerator.generateKeyPair()
            val privateMaterial = wrappingKey.wrap(keyPair.private)
            WrappedKeyPair(
                publicKey = keyPair.public,
                keyMaterial = privateMaterial,
                encodingVersion = 1
            )
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException(
                "Cannot generate wrapped key pair with scheme: ${wrappedSignatureScheme.codeName}",
                e
            )
        }
    }

    override fun sign(
        alias: String,
        signatureScheme: SignatureScheme,
        data: ByteArray,
        context: Map<String, String>
    ): ByteArray {
        logger.debug("sign(alias={}, signatureScheme={})", alias, signatureScheme)
        return try {
            val privateKey = cache.find(alias)?.privateKey
                ?: throw CryptoServiceBadRequestException(
                    "Unable to sign: There is no private key under the alias: $alias"
                )
            sign(signatureScheme, privateKey, data)
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("Cannot sign using the key with alias $alias", e)
        }
    }

    override fun sign(
        wrappedKey: WrappedPrivateKey,
        data: ByteArray,
        context: Map<String, String>
    ): ByteArray {
        logger.debug(
            "sign(wrappedKey.masterKeyAlias={}, wrappedKey.signatureScheme={})",
            wrappedKey.masterKeyAlias,
            wrappedKey.signatureScheme
        )
        return try {
            val wrappingKey = cache.find(wrappedKey.masterKeyAlias)?.wrappingKey
                ?: throw CryptoServiceBadRequestException("The ${wrappedKey.masterKeyAlias} is not created yet.")
            val privateKey = wrappingKey.unwrap(wrappedKey.keyMaterial)
            sign(wrappedKey.signatureScheme, privateKey, data)
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("Cannot sign using the key with wrapped private key", e)
        }
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
            val cipher = Cipher.getInstance(signatureSpec.signatureName, provider(signatureScheme))
            cipher.init(Cipher.ENCRYPT_MODE, privateKey)
            val signingData = signatureSpec.getSigningData(hashingService, data)
            cipher.doFinal(signingData)
        } else {
            signatureInstances.withSignature(signatureScheme) {
                if(signatureScheme.signatureSpec.params != null) {
                    it.setParameter(signatureScheme.signatureSpec.params)
                }
                it.initSign(privateKey, schemeMetadata.secureRandom)
                val signingData = signatureSpec.getSigningData(hashingService, data)
                it.update(signingData)
                it.sign()
            }
        }
        return signatureBytes
    }

    private fun isSupported(scheme: SignatureScheme): Boolean = scheme.codeName in supportedSchemeCodes

    private fun provider(scheme: SignatureScheme): Provider = schemeMetadata.providers.getValue(scheme.providerName)

    private fun MutableList<SignatureScheme>.addIfSupported(codeName: String) {
        if (schemeMetadata.schemes.any { it.codeName.equals(codeName, true) }) {
            add(schemeMetadata.findSignatureScheme(codeName))
        }
    }
}
