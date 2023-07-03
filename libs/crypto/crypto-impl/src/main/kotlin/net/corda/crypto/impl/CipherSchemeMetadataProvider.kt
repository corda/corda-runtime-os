package net.corda.crypto.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.crypto.cipher.suite.schemes.KeySchemeTemplate
import net.corda.metrics.CordaMetrics
import net.corda.v5.base.annotations.Suspendable
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
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.bouncycastle.util.encoders.Base64
import org.bouncycastle.util.io.pem.PemObject
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.time.Duration

private const val DECODE_PUBLIC_KEY_FROM_BYTE_ARRAY_OPERATION_NAME = "decodePublicKeyFromByteArray"
private const val DECODE_PUBLIC_KEY_FROM_STRING_OPERATION_NAME = "decodePublicKeyFromString"
private const val ENCODE_PUBLIC_KEY_TO_STRING_OPERATION_NAME = "encodePublicKeyToString"

class CipherSchemeMetadataProvider : KeyEncodingService {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val cordaSecurityProvider = CordaSecurityProvider(this)

    private val cordaBouncyCastleProvider: Provider = BouncyCastleProvider()

    private val bouncyCastlePQCProvider = BouncyCastlePQCProvider()

    val providers: Map<String, Provider> = listOf(
        cordaBouncyCastleProvider,
        cordaSecurityProvider,
        bouncyCastlePQCProvider
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
    @Suppress("VariableNaming", "PropertyName")
    val SPHINCS256: KeySchemeInfo = SPHINCS256KeySchemeInfo(bouncyCastlePQCProvider)

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

    private val algorithmMap: Map<AlgorithmIdentifier, KeyScheme> = schemes.flatMap { scheme ->
        scheme.algorithmOIDs.map { identifier -> identifier to scheme }
    }.toMap()

    fun findKeyScheme(algorithm: AlgorithmIdentifier): KeyScheme =
        algorithmMap[normaliseAlgorithmIdentifier(algorithm)]
            ?: throw IllegalArgumentException("Unrecognised algorithm: ${algorithm.algorithm.id}, with parameters=${algorithm.parameters}")

    private fun <T : Any> recordPublicKeyOperation(operationName: String, op: () -> T): T {
        logger.info("recordPublicKeyOperation start")
        logger.info("cipher scheme timer {}", CordaMetrics.Metric.Crypto.CipherSchemeTimer)
        val b = CordaMetrics.Metric.Crypto.CipherSchemeTimer.builder()
        logger.info("made builder {}", b)
        b.withTag(CordaMetrics.Tag.OperationName, operationName)
        logger.info("tag set")
        val built = b.build()
        logger.info("do build returned {}", built)
        val r = built.recordCallable {
            logger.info("in recordCallabck callback")
            val r = op.invoke()
            logger.info("invoke callback result {}", r)
            r
        }   
        logger.info("record done {}", r)
        return r!! 
    }

    private fun recordPublicKeyOperationDuration(operationName: String, duration: Duration) {
        logger.info("recordPublicKeyOperation start")
        logger.info("cipher scheme timer {}", CordaMetrics.Metric.Crypto.CipherSchemeTimer)
        val b = CordaMetrics.Metric.Crypto.CipherSchemeTimer.builder()
        logger.info("made builder {}", b)
        b.withTag(CordaMetrics.Tag.OperationName, operationName)
        logger.info("tag set")
        val built = b.build()
        logger.info("do build returned {}", built)
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
            recordPublicKeyOperationDuration(DECODE_PUBLIC_KEY_FROM_BYTE_ARRAY_OPERATION_NAME, Duration.ofNanos(endTime-startTime))
            logger.info("decode public key byte array start $startTime end $endTime complete; $encodedKey -> $r")
            return r
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoException("Failed to decode public key", e)
        }
    }

    override fun decodePublicKey(encodedKey: String): PublicKey {
        try {
            logger.info("decode public key string start [${encodedKey}]")
            val startTime = System.nanoTime()

            logger.info("doing parse on ${encodedKey}")
            val pemContent = parsePemContent(encodedKey)
            logger.info("got pem content ${pemContent} for ${encodedKey}")
            if (pemContent == null) throw IllegalArgumentException("Unable to decode PEM")
            val publicKeyInfo = SubjectPublicKeyInfo.getInstance(pemContent)
            logger.info("got public key info {}", publicKeyInfo)
            if (publicKeyInfo == null) throw IllegalArgumentException("Unable to extract public key (got null)")
            val converter = getJcaPEMKeyConverter(publicKeyInfo)
            logger.info("converted down")
            val publicKey = converter.getPublicKey(publicKeyInfo)
            logger.info("got public key")
            val res = toSupportedPublicKey(publicKey)
            val endTime = System.nanoTime()
            logger.info("decode public key string started $startTime finished $endTime finished [${encodedKey}] as ${res}")
            recordPublicKeyOperationDuration(DECODE_PUBLIC_KEY_FROM_STRING_OPERATION_NAME, Duration.ofNanos(endTime-startTime))
            return res
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw CryptoException("Failed to decode public key", e)
        }
    }

    override fun encodeAsString(publicKey: PublicKey): String = try {
        recordPublicKeyOperation(ENCODE_PUBLIC_KEY_TO_STRING_OPERATION_NAME) {
            objectToPem(publicKey)
        }
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
        logger.info("algorithm {}", publicKeyInfo.algorithm)
        val scheme = findKeyScheme(publicKeyInfo.algorithm)
        logger.info("scheme {}", scheme)
        val converter = JcaPEMKeyConverter()
        logger.info("converter {}", converter)
        converter.setProvider(providers[scheme.providerName])
        logger.info("set provider done")
        return converter
    }

    private fun objectToPem(obj: Any): String =
        StringWriter().use { strWriter ->
            JcaPEMWriter(strWriter).use { pemWriter ->
                pemWriter.writeObject(obj)
            }
            return strWriter.toString()
        }

    @Suspendable
    private fun parsePemContent(pem: String): ByteArray? {
        logger.info("parse pem content starting")
        logger.info("parse pem content on ${pem}")
        val BEGIN = "-----BEGIN "
        val HEADER_TERMINATOR = "-----"
        val END = "-----END "
        
        var index = 0
        var numLines = (pem.count { it == '\n' })+1
        val lines = Array<String>(numLines){""}
        var lineNumber = 0
        while (index < pem.length) {
            val c= pem[index]
            if (c == '\n') {
                lineNumber++
            } else {
                lines.set(lineNumber, lines.get(lineNumber)+c) // copies the string each time
            }
            index ++
        }
        var line = 0
        while (line < numLines) {
            lines.set(line, lines.get(line).trim())
            line++
        }
        var header = 0
        while ( header < numLines && !(lines.get(header).startsWith(BEGIN) && lines.get(header).endsWith(HEADER_TERMINATOR))) header++
        if (header == numLines) throw IllegalArgumentException("header not found")
        var section = lines.get(header).substring(BEGIN.length, lines.get(header).length - HEADER_TERMINATOR.length).trim()
        logger.info("Found PEM $section in $pem")
        var footer = 0
        while (footer < numLines && !(lines.get(footer).startsWith(END) && lines.get(footer).endsWith(HEADER_TERMINATOR))) footer++
        if (footer == numLines) throw IllegalArgumentException("No candidate footer found, ")
        val expectedFooter = END + section + HEADER_TERMINATOR
        if (header > footer) throw IllegalArgumentException("Expceted footer after hreader")
        if (lines.get(footer) != expectedFooter ) throw IllegalArgumentException("Expected footer missing; wanted ${expectedFooter}") 
        val base64Content = lines.slice(header+1.. footer-1).joinToString("")
        logger.info("Separated out ${base64Content.length} base 64 encoded characters")
        val decodedContent = Base64.decode(base64Content)
        logger.info("Separated out ${decodedContent.size} raw characters")
        val pemObject = PemObject(section, emptyList<String>(), decodedContent)
        logger.info("pem object is ${pemObject} on ${pem}")
        logger.info("content is ${pemObject.content}")
        if (pemObject.content == null) {
            logger.info("content is null")
            throw IllegalArgumentException("Key content was null")
        } 
        logger.info("content not null")
        val c = pemObject.content
        logger.info("content is ${c}")
        return c
}

    private fun normaliseAlgorithmIdentifier(id: AlgorithmIdentifier): AlgorithmIdentifier =
        if (id.parameters is DERNull) {
            AlgorithmIdentifier(id.algorithm, null)
        } else {
            id
        }
}
