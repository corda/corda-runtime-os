package net.corda.crypto.service.impl.dev

import net.corda.crypto.service.impl.soft.SoftCryptoService
import net.corda.crypto.service.impl.persistence.SigningKeyCache
import net.corda.crypto.service.impl.persistence.SoftCryptoKeyCache
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.WrappedKeyPair
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.sha256Bytes
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.bouncycastle.math.ec.ECConstants
import org.bouncycastle.math.ec.FixedPointCombMultiplier
import org.bouncycastle.util.encoders.Base32
import java.math.BigInteger
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PublicKey

/**
 * Dev friendly implementation of the [CryptoService].
 *
 * The aliased keys are deterministic and always derived from the SHA-512 hash of the alias.
 *
 * The keys assumed always exists and will be automatically generated.
 *
 * The service supports only ECDSA_SECP256R1 to avoid ambiguity to auto generate key when [containsKey] or
 * [findPublicKey] are called.
 *
 * The auto generated keys are automatically synchronised with the key cache used by [SigningKeyCache]
 *
 * The wrapped keys are not deterministic and are generated using the [SoftCryptoService] instance.
 */
@Suppress("LongParameterList")
class DevCryptoService(
    val tenantId: String,
    val category: String,
    val keyCache: SoftCryptoKeyCache,
    val signingCache: SigningKeyCache,
    schemeMetadata: CipherSchemeMetadata,
    hashingService: DigestService
) : CryptoService {
    companion object {
        const val SUPPORTED_SCHEME_CODE_NAME = ECDSA_SECP256R1_CODE_NAME

        private val logger = contextLogger()

        private fun String.toBigIntegerEntropy(): BigInteger {
            val bytes = MessageDigest
                .getInstance("SHA-512")
                .digest(this.toByteArray())
                .sliceArray(0 until 64)
            return BigInteger(bytes)
        }
    }

    private val supportedSchemes: Array<SignatureScheme>

    private val supportedSchemeCodes: Set<String>

    private val softCryptoService: CryptoService = SoftCryptoService(
        cache = keyCache,
        schemeMetadata = schemeMetadata,
        hashingService = hashingService
    )

    init {
        val schemes = mutableListOf<SignatureScheme>()
        if (schemeMetadata.schemes.any { it.codeName.equals(SUPPORTED_SCHEME_CODE_NAME, true) }) {
            schemes.add(schemeMetadata.findSignatureScheme(SUPPORTED_SCHEME_CODE_NAME))
        }
        require(schemes.isNotEmpty()) {
            throw CryptoServiceException("" +
                    "The default crypto service doesn't support $SUPPORTED_SCHEME_CODE_NAME scheme"
            )
        }
        supportedSchemes = schemes.toTypedArray()
        supportedSchemeCodes = supportedSchemes.map { it.codeName }.toSet()
    }

    override fun requiresWrappingKey(): Boolean = softCryptoService.requiresWrappingKey()

    override fun supportedSchemes(): Array<SignatureScheme> = supportedSchemes

    override fun supportedWrappingSchemes(): Array<SignatureScheme> = supportedSchemes

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

    override fun findPublicKey(alias: String): PublicKey? {
        logger.debug("findPublicKey(alias={})", alias)
        return try {
            keyCache.find(alias)?.publicKey ?: generateKeyPair(alias, supportedSchemes[0], true)
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Exception) {
            throw CryptoServiceException("Cannot find public key for alias $alias", e)
        }
    }

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean) =
        softCryptoService.createWrappingKey(masterKeyAlias, failIfExists)

    override fun generateKeyPair(
        alias: String,
        signatureScheme: SignatureScheme,
        context: Map<String, String>
    ): PublicKey =
        generateKeyPair(alias, signatureScheme, false)

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
        return softCryptoService.generateWrappedKeyPair(masterKeyAlias, wrappedSignatureScheme, context)
    }

    override fun sign(
        alias: String,
        signatureScheme: SignatureScheme,
        data: ByteArray,
        context: Map<String, String>
    ): ByteArray {
        logger.debug("sign(alias={}, signatureScheme={})", alias, signatureScheme)
        if (!isSupported(signatureScheme)) {
            throw CryptoServiceBadRequestException("Unsupported signature scheme: ${signatureScheme.codeName}")
        }
        if (findPublicKey(alias) == null) {
            throw CryptoServiceBadRequestException("Unable to sign: There is no private key under the alias: $alias")
        }
        return softCryptoService.sign(alias, signatureScheme, data, context)
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
        if (!isSupported(wrappedKey.signatureScheme)) {
            throw CryptoServiceBadRequestException(
                "Unsupported signature scheme: ${wrappedKey.signatureScheme.codeName}"
            )
        }
        return softCryptoService.sign(wrappedKey, data, context)
    }

    @Suppress("ThrowsCount")
    private fun generateKeyPair(
        alias: String,
        signatureScheme: SignatureScheme,
        autoGenerating: Boolean
    ): PublicKey {
        logger.debug(
            "generateKeyPair(alias={}, scheme={}, storeInSigningCache={})",
            alias,
            signatureScheme,
            autoGenerating
        )
        return try {
            val entropy = alias.toBigIntegerEntropy()
            val keyPair = when(signatureScheme.codeName) {
                ECDSA_SECP256R1_CODE_NAME -> deriveECDSAKeyPairFromEntropy(signatureScheme, entropy)
                else -> throw CryptoServiceBadRequestException(
                    "Unsupported signature scheme: ${signatureScheme.codeName}"
                )
            }
            keyCache.save(alias, keyPair, signatureScheme)
            if(autoGenerating) {
                signingCache.save(
                    publicKey = keyPair.public,
                    scheme = signatureScheme,
                    category = category,
                    alias = alias,
                    hsmAlias = computeHSMAlias(alias)
                )
            }
            keyPair.public
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Exception) {
            throw CryptoServiceException(
                "Cannot generate key for alias $alias and signature scheme ${signatureScheme.codeName}",
                e
            )
        }
    }

    // Custom key pair generator from an entropy required for various tests. It is similar to deriveKeyPairECDSA,
    // but the accepted range of the input entropy is more relaxed:
    // 2 <= entropy < N, where N is the order of base-point G.
    private fun deriveECDSAKeyPairFromEntropy(signatureScheme: SignatureScheme, entropy: BigInteger): KeyPair {
        val parameterSpec = signatureScheme.algSpec as ECNamedCurveParameterSpec
        // The entropy might be a negative number and/or out of range (e.g. PRNG output).
        // In such cases we retry with hash(currentEntropy).
        while (entropy < ECConstants.TWO || entropy >= parameterSpec.n) {
            return deriveECDSAKeyPairFromEntropy(signatureScheme, BigInteger(1, entropy.toByteArray().sha256Bytes()))
        }
        val privateKeySpec = ECPrivateKeySpec(entropy, parameterSpec)
        val priv = BCECPrivateKey("EC", privateKeySpec, BouncyCastleProvider.CONFIGURATION)
        val pointQ = FixedPointCombMultiplier().multiply(parameterSpec.g, entropy)
        while (pointQ.isInfinity) {
            // Instead of throwing an exception, we retry with hash(entropy).
            return deriveECDSAKeyPairFromEntropy(signatureScheme, BigInteger(1, entropy.toByteArray().sha256Bytes()))
        }
        val publicKeySpec = ECPublicKeySpec(pointQ, parameterSpec)
        val pub = BCECPublicKey("EC", publicKeySpec, BouncyCastleProvider.CONFIGURATION)
        return KeyPair(pub, priv)
    }

    private fun isSupported(scheme: SignatureScheme): Boolean = scheme.codeName in supportedSchemeCodes

    private fun computeHSMAlias(alias: String): String
            = Base32.toBase32String((tenantId + alias).encodeToByteArray().sha256Bytes()).take(30).toLowerCase()
}