package net.corda.impl.dev.cipher.suite

import net.corda.impl.cipher.suite.DefaultCryptoService
import net.corda.impl.cipher.suite.DefaultKeyCache
import net.corda.impl.crypto.SigningKeyCache
import net.corda.internal.crypto.SigningService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.WrappedKeyPair
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.cipher.suite.schemes.SignatureSpec
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.math.BigInteger
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PublicKey

/**
 * Dev friendly implementation of the [CryptoService].
 * The aliased keys are deterministic and always derived from the first 64 bits of the SHA-256 hash of the alias,
 * if the aliased key doesn't exist its generated and stored in the caches for the [CryptoService]
 * and the [SigningService].
 * The wrapped keys are not deterministic generated using th
 */
class DevCryptoService(
    private val keyCache: DefaultKeyCache,
    private val signingCache: SigningKeyCache,
    schemeMetadata: CipherSchemeMetadata,
    hashingService: DigestService
) : CryptoService {
    companion object {
        private val logger = contextLogger()

        private fun String.toBigIntegerEntropy(): BigInteger {
            val bytes = MessageDigest
                .getInstance("SHA-256")
                .digest(this.toByteArray())
                .sliceArray(0 until 64)
            return BigInteger(bytes)
        }
    }

    private val supportedSchemes: Array<SignatureScheme>

    private val defaultCryptoService: CryptoService = DefaultCryptoService(
        cache = keyCache,
        schemeMetadata = schemeMetadata,
        hashingService = hashingService
    )

    init {
        if (!defaultCryptoService.supportedSchemes().any { it.codeName.equals(EDDSA_ED25519_CODE_NAME, true) }) {
            throw CryptoServiceException("The default crypto service doesn't support $EDDSA_ED25519_CODE_NAME scheme.")
        }
        val schemes = mutableListOf<SignatureScheme>()
        if (schemeMetadata.schemes.any { it.codeName.equals(EDDSA_ED25519_CODE_NAME, true) }) {
            schemes.add(schemeMetadata.findSignatureScheme(EDDSA_ED25519_CODE_NAME))
        }
        supportedSchemes = schemes.toTypedArray()
    }

    override fun requiresWrappingKey(): Boolean = defaultCryptoService.requiresWrappingKey()

    override fun supportedSchemes(): Array<SignatureScheme> = supportedSchemes

    override fun supportedWrappingSchemes(): Array<SignatureScheme> = supportedSchemes

    @Suppress("TooGenericExceptionCaught")
    override fun containsKey(alias: String): Boolean {
        logger.debug("containsKey(alias={})", alias)
        return try {
            findPublicKey(alias) != null
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Exception) {
            throw CryptoServiceException("Cannot check key existence key for alias $alias", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun findPublicKey(alias: String): PublicKey? {
        logger.debug("findPublicKey(alias={})", alias)
        return try {
            keyCache.find(alias)?.publicKey ?: generateKeyPair(alias, supportedSchemes[0])
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Exception) {
            throw CryptoServiceException("Cannot find public key for alias $alias", e)
        }
    }

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean) =
        defaultCryptoService.createWrappingKey(masterKeyAlias, failIfExists)

    @Suppress("TooGenericExceptionCaught")
    override fun generateKeyPair(alias: String, signatureScheme: SignatureScheme): PublicKey {
        logger.debug("generateKeyPair(alias={}, scheme={})", alias, signatureScheme)
        if (!isSupported(signatureScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${signatureScheme.codeName}")
        }
        return try {
            val keyPair = deriveKeyPairFromEntropy(signatureScheme, alias.toBigIntegerEntropy())
            keyCache.save(alias, keyPair, signatureScheme)
            signingCache.save(keyPair.public, signatureScheme, alias)
            keyPair.public
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Exception) {
            throw CryptoServiceException("Cannot generate key for alias $alias and signature scheme ${signatureScheme.codeName}", e)
        }
    }

    override fun generateWrappedKeyPair(masterKeyAlias: String, wrappedSignatureScheme: SignatureScheme): WrappedKeyPair {
        logger.debug("generateWrappedKeyPair(masterKeyAlias={}, wrappedSignatureScheme={})", masterKeyAlias, wrappedSignatureScheme)
        if (!isSupported(wrappedSignatureScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${wrappedSignatureScheme.codeName}")
        }
        return defaultCryptoService.generateWrappedKeyPair(masterKeyAlias, wrappedSignatureScheme)
    }

    override fun sign(alias: String, signatureScheme: SignatureScheme, data: ByteArray): ByteArray =
        sign(alias, signatureScheme, signatureScheme.signatureSpec, data)

    @Suppress("TooGenericExceptionCaught")
    override fun sign(alias: String, signatureScheme: SignatureScheme, signatureSpec: SignatureSpec, data: ByteArray): ByteArray {
        logger.debug("sign(alias={}, signatureScheme={}, signatureSpec={})", alias, signatureScheme, signatureSpec)
        if (!isSupported(signatureScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${signatureScheme.codeName}")
        }
        if (findPublicKey(alias) == null) {
            throw CryptoServiceBadRequestException("Unable to sign: There is no private key under the alias: $alias")
        }
        return defaultCryptoService.sign(alias, signatureScheme, signatureSpec, data)
    }

    override fun sign(wrappedKey: WrappedPrivateKey, signatureSpec: SignatureSpec, data: ByteArray): ByteArray {
        logger.debug(
            "sign(wrappedKey.masterKeyAlias={}, wrappedKey.signatureScheme={}, signatureSpec={})",
            wrappedKey.masterKeyAlias,
            wrappedKey.signatureScheme,
            signatureSpec
        )
        if (!isSupported(wrappedKey.signatureScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${wrappedKey.signatureScheme.codeName}")
        }
        return defaultCryptoService.sign(wrappedKey, signatureSpec, data)
    }

    private fun deriveKeyPairFromEntropy(signatureScheme: SignatureScheme, entropy: BigInteger): KeyPair {
        if (!isSupported(signatureScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${signatureScheme.codeName}")
        }
        val params = signatureScheme.algSpec as EdDSANamedCurveSpec
        val bytes = entropy.toByteArray().copyOf(params.curve.field.getb() / 8) // Need to pad the entropy to the valid seed length.
        val priv = EdDSAPrivateKeySpec(bytes, params)
        val pub = EdDSAPublicKeySpec(priv.a, params)
        return KeyPair(EdDSAPublicKey(pub), EdDSAPrivateKey(priv))
    }

    private fun isSupported(scheme: SignatureScheme): Boolean = scheme in supportedSchemes
}