package net.corda.cipher.suite.impl.platform

import net.corda.cipher.suite.OID_COMPOSITE_KEY_IDENTIFIER
import net.corda.v5.cipher.suite.providers.encoding.KeyEncodingHandler
import net.corda.v5.cipher.suite.scheme.DigestScheme
import net.corda.v5.cipher.suite.scheme.KeyScheme
import net.corda.v5.cipher.suite.scheme.KeySchemeCapability
import net.corda.v5.crypto.COMPOSITE_KEY_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.bouncycastle.util.io.pem.PemReader
import java.io.StringReader
import java.io.StringWriter
import java.security.MessageDigest
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec

class PlatformCipherSuiteMetadata : KeyEncodingHandler {
    companion object {
        val MESSAGE_DIGEST_TYPE: String = MessageDigest::class.java.simpleName

        private val DIGEST_CANDIDATES = listOf(
            "BLAKE2B-256",
            "BLAKE2B-384",
            "BLAKE2B-512",
            "BLAKE2S-256",
            "DSTU7564-256",
            "DSTU7564-384",
            "DSTU7564-512",
            "GOST3411",
            "GOST3411-2012-256",
            "GOST3411-2012-512",
            "KECCAK-256",
            "KECCAK-288",
            "KECCAK-384",
            "KECCAK-512",
            "RIPEMD256",
            "RIPEMD320",
            "SHA-256",
            "SHA-384",
            "SHA-512",
            "SHA-512/256",
            "SHA3-256",
            "SHA3-384",
            "SHA3-512",
            "SHAKE256-512",
            "SM3",
            "Skein-1024-1024",
            "Skein-1024-384",
            "Skein-1024-512",
            "Skein-256-256",
            "Skein-512-128",
            "Skein-512-160",
            "Skein-512-256",
            "Skein-512-384",
            "Skein-512-512",
            "TIGER",
            "WHIRLPOOL"
        )
    }

    private val cordaSecurityProvider = CordaSecurityProvider(this)

    private val cordaBouncyCastleProvider: Provider = BouncyCastleProvider()

    private val bouncyCastlePQCProvider = BouncyCastlePQCProvider()

    val providers: Map<String, Provider> = listOf(
        cordaBouncyCastleProvider,
        cordaSecurityProvider,
        bouncyCastlePQCProvider
    ).associateBy(Provider::getName)

    private val schemeProviderMap: MutableMap<KeyScheme, Provider> = mutableMapOf()

    val secureRandom: SecureRandom = SecureRandom.getInstance(
        CordaSecureRandomService.algorithm,
        cordaSecurityProvider
    )

    private val keyFactories = KeyFactoryProvider(schemeProviderMap)

    /**
     * RSA PKCS#1 key scheme.
     * The actual algorithm id is 1.2.840.113549.1.1.1
     * Note: Recommended key size >= 3072 bits.
     */
    @Suppress("VariableNaming", "PropertyName")
    private val RSA: KeySchemeInfo = RSAKeySchemeInfo().also {
        schemeProviderMap[it.scheme] = cordaBouncyCastleProvider
    }

    /** ECDSA key scheme using the secp256k1 Koblitz curve. */
    @Suppress("VariableNaming", "PropertyName")
    private val ECDSA_SECP256K1: KeySchemeInfo = ECDSAK1KeySchemeInfo().also {
        schemeProviderMap[it.scheme] = cordaBouncyCastleProvider
    }

    /** ECDSA signature scheme using the secp256r1 (NIST P-256) curve. */
    @Suppress("VariableNaming", "PropertyName")
    private val ECDSA_SECP256R1: KeySchemeInfo = ECDSAR1KeySchemeInfo().also {
        schemeProviderMap[it.scheme] = cordaBouncyCastleProvider
    }

    /**
     * EdDSA key scheme using the ed25519 twisted Edwards curve.
     * The actual algorithm is PureEdDSA Ed25519 as defined in https://tools.ietf.org/html/rfc8032
     * Not to be confused with the EdDSA variants, Ed25519ctx and Ed25519ph.
     */
    @Suppress("VariableNaming", "PropertyName")
    private val EDDSA_ED25519: KeySchemeInfo = EDDSAKeySchemeInfo().also {
        schemeProviderMap[it.scheme] = cordaBouncyCastleProvider
    }

    /**
     * EdDSA key scheme using the X25519 twisted Edwards curve for ECDH.
     */
    @Suppress("VariableNaming", "PropertyName")
    private val X25519 = X25519KeySchemeInfo().also {
        schemeProviderMap[it.scheme] = cordaBouncyCastleProvider
    }

    /** ECDSA key scheme using the sm2p256v1 (Chinese SM2) curve. */
    @Suppress("VariableNaming", "PropertyName")
    private val SM2: KeySchemeInfo = SM2KeySchemeInfo().also {
        schemeProviderMap[it.scheme] = cordaBouncyCastleProvider
    }

    /** GOST3410. */
    @Suppress("VariableNaming", "PropertyName")
    private val GOST3410_GOST3411: KeySchemeInfo = GOST3410GOST3411KeySchemeInfo().also {
        schemeProviderMap[it.scheme] = cordaBouncyCastleProvider
    }

    /**
     * SPHINCS-256 hash-based key scheme using SHA512 for message hashing. It provides 128bit security against
     * post-quantum attackers at the cost of larger key nd signature sizes and loss of compatibility.
     */
    @Suppress("VariableNaming", "PropertyName")
    private val SPHINCS256: KeySchemeInfo = SPHINCS256KeySchemeInfo().also {
        schemeProviderMap[it.scheme] = bouncyCastlePQCProvider
    }

    @Suppress("VariableNaming", "PropertyName")
    val COMPOSITE_KEY = KeyScheme(
        codeName = COMPOSITE_KEY_CODE_NAME,
        algorithmOIDs = listOf(AlgorithmIdentifier(OID_COMPOSITE_KEY_IDENTIFIER)),
        algorithmName = "COMPOSITE",
        algSpec = null,
        keySize = null,
        capabilities = setOf(KeySchemeCapability.SIGN)
    ).also {
        schemeProviderMap[it] = cordaSecurityProvider
    }

    val keySchemeInfoMap = mapOf(
        RSA.scheme to RSA,
        ECDSA_SECP256R1.scheme to ECDSA_SECP256R1,
        ECDSA_SECP256K1.scheme to ECDSA_SECP256K1,
        EDDSA_ED25519.scheme to EDDSA_ED25519,
        SM2.scheme to SM2,
        GOST3410_GOST3411.scheme to GOST3410_GOST3411,
        SPHINCS256.scheme to SPHINCS256
    )

    val schemes: List<KeyScheme> = listOf(
        RSA.scheme,
        ECDSA_SECP256K1.scheme,
        ECDSA_SECP256R1.scheme,
        EDDSA_ED25519.scheme,
        X25519.scheme,
        SPHINCS256.scheme,
        SM2.scheme,
        GOST3410_GOST3411.scheme,
        COMPOSITE_KEY
    )

    val algorithmMap: Map<AlgorithmIdentifier, KeyScheme> = schemes.flatMap { scheme ->
        scheme.algorithmOIDs.map { identifier -> identifier to scheme }
    }.toMap()

    val supportedSigningSchemes: Map<KeyScheme, List<SignatureSpec>> = mapOf(
        RSA.scheme to RSA.supportedSignatureSpecs,
        ECDSA_SECP256R1.scheme to ECDSA_SECP256R1.supportedSignatureSpecs,
        ECDSA_SECP256K1.scheme to ECDSA_SECP256K1.supportedSignatureSpecs,
        EDDSA_ED25519.scheme to EDDSA_ED25519.supportedSignatureSpecs,
        SPHINCS256.scheme to SPHINCS256.supportedSignatureSpecs,
        SM2.scheme to SM2.supportedSignatureSpecs,
        GOST3410_GOST3411.scheme to GOST3410_GOST3411.supportedSignatureSpecs
    )

    val digests: List<DigestScheme> = providers.values
        .flatMap { it.services }
        .filter {
            it.type.equals(MESSAGE_DIGEST_TYPE, true)
                    && DIGEST_CANDIDATES.contains(it.algorithm)
        }
        .map { DigestScheme(algorithmName = it.algorithm, providerName = it.provider.name) }
        .distinctBy { it.algorithmName }

    fun providerFor(scheme: KeyScheme): Provider = schemeProviderMap.getValue(scheme)

    fun findKeyScheme(key: PublicKey): KeyScheme {
        val keyInfo = SubjectPublicKeyInfo.getInstance(key.encoded)
        return findKeyScheme(keyInfo.algorithm)
    }

    fun findKeyScheme(algorithm: AlgorithmIdentifier): KeyScheme =
        algorithmMap[normaliseAlgorithmIdentifier(algorithm)]
            ?: throw IllegalArgumentException("Unrecognised algorithm: ${algorithm.algorithm.id}")

    override val rank: Int = 0

    override fun decode(encodedKey: ByteArray): PublicKey? {
        val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(encodedKey)
        val scheme = findKeyScheme(subjectPublicKeyInfo.algorithm)
        val keyFactory = keyFactories[scheme]
        return keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
    }

    override fun decode(scheme: KeyScheme, publicKeyInfo: SubjectPublicKeyInfo, encodedKey: ByteArray): PublicKey {
        val keyFactory = keyFactories[scheme]
        return keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
    }

    override fun decodePem(encodedKey: String): PublicKey? {
        val pemContent = parsePemContent(encodedKey)
        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(pemContent)
        val converter = getJcaPEMKeyConverter(publicKeyInfo)
        return converter.getPublicKey(publicKeyInfo)
    }

    override fun decodePem(scheme: KeyScheme, publicKeyInfo: SubjectPublicKeyInfo, pemContent: ByteArray): PublicKey {
        val converter = getJcaPEMKeyConverter(publicKeyInfo)
        return converter.getPublicKey(publicKeyInfo)
    }

    override fun encodeAsPem(scheme: KeyScheme, publicKey: PublicKey): String =
        StringWriter().use { strWriter ->
            JcaPEMWriter(strWriter).use { pemWriter ->
                pemWriter.writeObject(publicKey)
            }
            return strWriter.toString()
        }

    private fun getJcaPEMKeyConverter(publicKeyInfo: SubjectPublicKeyInfo): JcaPEMKeyConverter {
        val scheme = findKeyScheme(publicKeyInfo.algorithm)
        val converter = JcaPEMKeyConverter()
        converter.setProvider(schemeProviderMap[scheme])
        return converter
    }

    private fun parsePemContent(pem: String): ByteArray =
        StringReader(pem).use { strReader ->
            return PemReader(strReader).use { pemReader ->
                pemReader.readPemObject().content
            }
        }

    private fun normaliseAlgorithmIdentifier(id: AlgorithmIdentifier): AlgorithmIdentifier =
        if (id.parameters is DERNull) {
            AlgorithmIdentifier(id.algorithm, null)
        } else {
            id
        }
}