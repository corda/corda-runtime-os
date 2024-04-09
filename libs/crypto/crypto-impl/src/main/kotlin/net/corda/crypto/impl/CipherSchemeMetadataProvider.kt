package net.corda.crypto.impl

import java.io.StringWriter
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.crypto.cipher.suite.schemes.KeySchemeTemplate
import net.corda.metrics.CordaMetrics
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.CordaOID.OID_COMPOSITE_KEY
import net.corda.v5.crypto.KeySchemeCodes.COMPOSITE_KEY_CODE_NAME
import net.corda.v5.crypto.exceptions.CryptoException
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
//import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.bouncycastle.util.encoders.Base64
import org.bouncycastle.util.io.pem.PemObject

private val PEM_BEGIN = "-----BEGIN "
private val PEM_HEADER_TERMINATOR = "-----"
private val PEM_END = "-----END "
private val PUBLIC_KEY = "PUBLIC KEY"

private const val DECODE_PUBLIC_KEY_FROM_BYTE_ARRAY_OPERATION_NAME = "decodePublicKeyFromByteArray"
private const val DECODE_PUBLIC_KEY_FROM_STRING_OPERATION_NAME = "decodePublicKeyFromString"
private const val ENCODE_PUBLIC_KEY_TO_STRING_OPERATION_NAME = "encodePublicKeyToString"

class CipherSchemeMetadataProvider : KeyEncodingService {

    private val cordaSecurityProvider = CordaSecurityProvider(this)

    private val cordaBouncyCastleProvider: Provider = BouncyCastleProvider()

//    private val bouncyCastlePQCProvider = BouncyCastlePQCProvider()

    val providers: Map<String, Provider> = listOf(
        cordaBouncyCastleProvider,
        cordaSecurityProvider,
//        bouncyCastlePQCProvider
    )
        .associateBy(Provider::getName)

    val secureRandom: SecureRandom = SecureRandom.getInstance(
        CordaSecureRandomService.algorithm,
        cordaSecurityProvider
    )

    val keyFactories = KeyFactoryProvider(providers)

    /**
     * RSA PKCS#1 key scheme.
     * The actual algorithm id is 1.2.840.113549.1.1.1
     * Note: Recommended key size >= 3072 bits.
     */
    @Suppress("VariableNaming", "PropertyName")
    val RSA: KeySchemeInfo = RSAKeySchemeInfo(cordaBouncyCastleProvider)

    /** ECDSA key scheme using the secp256k1 Koblitz curve. */
    @Suppress("VariableNaming", "PropertyName")
    val ECDSA_SECP256K1: KeySchemeInfo = ECDSAK1KeySchemeInfo(cordaBouncyCastleProvider)

    /** ECDSA signature scheme using the secp256r1 (NIST P-256) curve. */
    @Suppress("VariableNaming", "PropertyName")
    val ECDSA_SECP256R1: KeySchemeInfo = ECDSAR1KeySchemeInfo(cordaBouncyCastleProvider)

    /**
     * EdDSA key scheme using the ed25519 twisted Edwards curve.
     * The actual algorithm is PureEdDSA Ed25519 as defined in https://tools.ietf.org/html/rfc8032
     * Not to be confused with the EdDSA variants, Ed25519ctx and Ed25519ph.
     */
    @Suppress("VariableNaming", "PropertyName")
    val EDDSA_ED25519: KeySchemeInfo = EDDSAKeySchemeInfo(cordaBouncyCastleProvider)

    /**
     * EdDSA key scheme using the X25519 twisted Edwards curve for ECDH.
     */
    @Suppress("VariableNaming", "PropertyName")
    val X25519 = X25519KeySchemeInfo(cordaBouncyCastleProvider)

    /** ECDSA key scheme using the sm2p256v1 (Chinese SM2) curve. */
    @Suppress("VariableNaming", "PropertyName")
    val SM2: KeySchemeInfo = SM2KeySchemeInfo(cordaBouncyCastleProvider)

    /** GOST3410. */
    @Suppress("VariableNaming", "PropertyName")
    val GOST3410_GOST3411: KeySchemeInfo = GOST3410GOST3411KeySchemeInfo(cordaBouncyCastleProvider)

    /**
     * SPHINCS-256 hash-based key scheme using SHA512 for message hashing. It provides 128bit security against
     * post-quantum attackers at the cost of larger key nd signature sizes and loss of compatibility.
     */
//    @Suppress("VariableNaming", "PropertyName")
//    val SPHINCS256: KeySchemeInfo = SPHINCS256KeySchemeInfo(bouncyCastlePQCProvider)

    @Suppress("VariableNaming", "PropertyName")
    val OID_COMPOSITE_KEY_IDENTIFIER = ASN1ObjectIdentifier(OID_COMPOSITE_KEY)

    @Suppress("VariableNaming", "PropertyName")
    val COMPOSITE_KEY_TEMPLATE = KeySchemeTemplate(
        codeName = COMPOSITE_KEY_CODE_NAME,
        algorithmOIDs = listOf(AlgorithmIdentifier(OID_COMPOSITE_KEY_IDENTIFIER)),
        algorithmName = "COMPOSITE",
        algSpec = null,
        keySize = null,
        capabilities = setOf(KeySchemeCapability.SIGN)
    )

    /** Corda [CompositeKey] key type. */
    @Suppress("VariableNaming", "PropertyName")
    val COMPOSITE_KEY: KeyScheme = COMPOSITE_KEY_TEMPLATE.makeScheme(
        providerName = cordaSecurityProvider.name
    )

    val keySchemeInfoMap = mapOf(
        RSA.scheme to RSA,
        ECDSA_SECP256R1.scheme to ECDSA_SECP256R1,
        ECDSA_SECP256K1.scheme to ECDSA_SECP256K1,
        EDDSA_ED25519.scheme to EDDSA_ED25519,
        SM2.scheme to SM2,
        GOST3410_GOST3411.scheme to GOST3410_GOST3411,
//        SPHINCS256.scheme to SPHINCS256
    )

    val schemes: List<KeyScheme> = listOf(
        RSA.scheme,
        ECDSA_SECP256K1.scheme,
        ECDSA_SECP256R1.scheme,
        EDDSA_ED25519.scheme,
        X25519.scheme,
//        SPHINCS256.scheme,
        SM2.scheme,
        GOST3410_GOST3411.scheme,
        COMPOSITE_KEY
    )

    private val algorithmMap: Map<AlgorithmIdentifier, KeyScheme> = schemes.flatMap { scheme ->
        scheme.algorithmOIDs.map { identifier -> identifier to scheme }
    }.toMap()

    fun findKeyScheme(algorithm: AlgorithmIdentifier): KeyScheme =
        algorithmMap[normaliseAlgorithmIdentifier(algorithm)]
            ?: throw IllegalArgumentException("Unrecognised algorithm: ${algorithm.algorithm.id}, with parameters=${algorithm.parameters}")


    // We don't use lambdas here because of the difficulties that causes with Quasar,
    // e.g. a lambda can't be marked as @Suspendable and cannot have checked exceptions, so
    // makes the instrumentation analysis harder. We don't want to get suspended while doing a timing
    // operation.

    private fun recordPublicKeyOperationDuration(operationName: String, duration: Duration) {
        val b = CordaMetrics.Metric.Crypto.CipherSchemeTimer.builder()
        b.withTag(CordaMetrics.Tag.OperationName, operationName)
        val built = b.build()
        built.record(duration)
    }

    override fun decodePublicKey(encodedKey: ByteArray): PublicKey {

        try {
            val startTime = System.nanoTime()
            val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(encodedKey)
            val scheme = findKeyScheme(subjectPublicKeyInfo.algorithm)
            val keyFactory = keyFactories[scheme]
            val r = keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
            val endTime = System.nanoTime() - startTime
            recordPublicKeyOperationDuration(
                DECODE_PUBLIC_KEY_FROM_BYTE_ARRAY_OPERATION_NAME,
                Duration.ofNanos(endTime - startTime)
            )
            return r
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoException("Failed to decode public key", e)
        }
    }

    override fun decodePublicKey(encodedKey: String): PublicKey = try {
        val startTime = System.nanoTime()
        val pemContent = parsePemPublicKeyContent(encodedKey)
        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(pemContent)
        if (publicKeyInfo == null) throw IllegalArgumentException("Unable to extract public key (got null)")
        val converter = getJcaPEMKeyConverter(publicKeyInfo)
        val publicKey = converter.getPublicKey(publicKeyInfo)
        val r = toSupportedPublicKey(publicKey)
        val endTime = System.nanoTime() - startTime
        recordPublicKeyOperationDuration(
            DECODE_PUBLIC_KEY_FROM_STRING_OPERATION_NAME,
            Duration.ofNanos(endTime - startTime)
        )
        r
    } catch (e: RuntimeException) {
        throw e
    } catch (e: Exception) {
        throw CryptoException("Failed to decode public key", e)
    }

    override fun encodeAsString(publicKey: PublicKey): String = try {
        val startTime = System.nanoTime()
        val r = objectToPem(publicKey)
        val endTime = System.nanoTime()
        recordPublicKeyOperationDuration(
            ENCODE_PUBLIC_KEY_TO_STRING_OPERATION_NAME,
            Duration.ofNanos(endTime - startTime)
        )
        r
    } catch (e: RuntimeException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoException("Failed to encode public key in PEM format", e)
    }

    override fun toSupportedPublicKey(key: PublicKey): PublicKey {
        return when (key) {
            is CompositeKey -> key
            else -> decodePublicKey(key.encoded)
        }
    }

    private fun getJcaPEMKeyConverter(publicKeyInfo: SubjectPublicKeyInfo): JcaPEMKeyConverter {
        val scheme = findKeyScheme(publicKeyInfo.algorithm)
        val converter = JcaPEMKeyConverter()
        converter.setProvider(providers[scheme.providerName])
        return converter
    }

    private fun objectToPem(obj: Any): String =
        StringWriter().use { strWriter ->
            JcaPEMWriter(strWriter).use { pemWriter ->
                pemWriter.writeObject(obj)
            }
            return strWriter.toString()
        }

    @Suppress("ThrowsCount")
    private fun parsePemPublicKeyContent(pem: String): ByteArray {
        // we no longer use Bouncy Castle PemReader since it required use of StringReader/BufferReader, which has
        // locking, and that may not be safe from flow execution due to our use of Quasar Fibers. 
        // Therefore we implement our own logic below to parse PEM files. This is simply a question of decoding base64,
        // so not a cryptographic algorithm; we call from to Bouncy Castle to interpret the ASN.1 content.

        // This is not the full PEM spec; we do not support headers or multiple sections  
        val lines = pem.split('\n').map { it.trim() }
        val header =
            lines.withIndex()
                .firstOrNull { it.value.startsWith(PEM_BEGIN) && it.value.endsWith((PEM_HEADER_TERMINATOR)) }
                ?: throw IllegalArgumentException("No PEM header found starting [$PEM_BEGIN] and ending [$PEM_HEADER_TERMINATOR]")
        var section =
            header.value.substring(PEM_BEGIN.length, header.value.length - PEM_HEADER_TERMINATOR.length).trim()
        if (section.uppercase() != PUBLIC_KEY) throw IllegalArgumentException("PEM $section not supported; only $PUBLIC_KEY")
        val expectedFooter = "${PEM_END}${section}${PEM_HEADER_TERMINATOR}"
        val footer = lines.withIndex().firstOrNull { it.value == expectedFooter }
            ?: throw IllegalArgumentException("No PEM footer found expecting [$expectedFooter]")
        val base64Content = lines.slice(header.index + 1..footer.index - 1).joinToString("")
        val decodedContent = Base64.decode(base64Content)
        val pemObject = PemObject(section, emptyList<String>(), decodedContent)
        if (pemObject.content == null) throw IllegalArgumentException("Key content was null")
        return pemObject.content
    }

    private fun normaliseAlgorithmIdentifier(id: AlgorithmIdentifier): AlgorithmIdentifier =
        if (id.parameters is DERNull) {
            AlgorithmIdentifier(id.algorithm, null)
        } else {
            id
        }
}
