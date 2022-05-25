package net.corda.crypto.impl.components

import net.corda.crypto.impl.schememetadata.ProviderMap
import net.corda.v5.cipher.suite.AlgorithmParameterSpecEncodingService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.schemes.AlgorithmParameterSpecSerializer
import net.corda.v5.cipher.suite.schemes.DigestScheme
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.cipher.suite.schemes.SerializedAlgorithmParameterSpec
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.EDDSA_ED25519_NONE_SIGNATURE_SPEC
import net.corda.v5.crypto.GOST3410_GOST3411_SIGNATURE_SPEC
import net.corda.v5.crypto.SPHINCS256_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.io.pem.PemReader
import org.osgi.service.component.annotations.Component
import java.io.StringReader
import java.io.StringWriter
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec

@Component(
    service = [
        CipherSchemeMetadata::class,
        KeyEncodingService::class,
        AlgorithmParameterSpecEncodingService::class
    ]
)
@Suppress("TooManyFunctions")
class CipherSchemeMetadataImpl : CipherSchemeMetadata {
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

    private val paramSpecSerializers = mapOf<String, AlgorithmParameterSpecSerializer<out AlgorithmParameterSpec>>(
        PSSParameterSpec::class.java.name to PSSParameterSpecSerializer()
    )

    override fun findKeyScheme(algorithm: AlgorithmIdentifier): KeyScheme =
        algorithmMap[normaliseAlgorithmIdentifier(algorithm)]
            ?: throw IllegalArgumentException("Unrecognised algorithm: ${algorithm.algorithm.id}")

    @Suppress("ComplexMethod")
    override fun inferSignatureSpec(publicKey: PublicKey, digest: DigestAlgorithmName): SignatureSpec? {
        val scheme = findKeyScheme(publicKey)
        val normalisedDigestName = digest.name.replace("-", "")
        return when (scheme) {
            providerMap.ECDSA_SECP256R1, providerMap.ECDSA_SECP256K1 ->
                SignatureSpec(
                    signatureName = "${normalisedDigestName}withECDSA"
                )
            providerMap.RSA ->
                SignatureSpec(
                    signatureName = "${normalisedDigestName}withRSA"
                )
            providerMap.EDDSA_ED25519 -> {
                if (digest.name.equals("NONE", true)) {
                    EDDSA_ED25519_NONE_SIGNATURE_SPEC
                } else {
                    null
                }
            }
            providerMap.SM2 -> {
                if (digest.name.equals("SM3", true) ||
                    digest.name.equals("SHA-256", true) ||
                    digest.name.equals("SHA-384", true) ||
                    digest.name.equals("SHA-512", true) ||
                    digest.name.equals("WHIRLPOOL", true) ||
                    digest.name.equals("BLAKE2B-256", true) ||
                    digest.name.equals("BLAKE2B-512", true)
                ) {
                    SignatureSpec(
                        signatureName = "${normalisedDigestName}withSM2"
                    )
                } else {
                    null
                }
            }
            providerMap.GOST3410_GOST3411 -> {
                if (digest.name.equals("GOST3411", true)) {
                    GOST3410_GOST3411_SIGNATURE_SPEC
                } else {
                    null
                }
            }
            providerMap.SPHINCS256 -> {
                if (digest.name.equals("SHA-512", true)) {
                    SPHINCS256_SHA512_SIGNATURE_SPEC
                } else {
                    null
                }
            }
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun serialize(params: AlgorithmParameterSpec): SerializedAlgorithmParameterSpec {
        val clazz = params::class.java.name
        val serializer = paramSpecSerializers[clazz] as? AlgorithmParameterSpecSerializer<AlgorithmParameterSpec>
            ?: throw IllegalArgumentException("$clazz is not supported.")
        return SerializedAlgorithmParameterSpec(
            clazz = clazz,
            bytes = serializer.serialize(params)
        )
    }

    override fun findKeyScheme(key: PublicKey): KeyScheme {
        val keyInfo = SubjectPublicKeyInfo.getInstance(key.encoded)
        return findKeyScheme(keyInfo.algorithm)
    }

    private val providerMap by lazy(LazyThreadSafetyMode.PUBLICATION) { ProviderMap(this) }

    override val schemes: Array<KeyScheme> = arrayOf(
        providerMap.RSA,
        providerMap.ECDSA_SECP256K1,
        providerMap.ECDSA_SECP256R1,
        providerMap.EDDSA_ED25519,
        providerMap.SPHINCS256,
        providerMap.SM2,
        providerMap.GOST3410_GOST3411,
        providerMap.COMPOSITE_KEY
    )

    private val algorithmMap: Map<AlgorithmIdentifier, KeyScheme> = schemes.flatMap { scheme ->
        scheme.algorithmOIDs.map { identifier -> identifier to scheme }
    }.toMap()

    override val digests: Array<DigestScheme> = providerMap.providers.values
        .flatMap { it.services }
        .filter {
            it.type.equals(MESSAGE_DIGEST_TYPE, true)
                    && !CipherSchemeMetadata.BANNED_DIGESTS.contains(it.algorithm)
                    && DIGEST_CANDIDATES.contains(it.algorithm)
        }
        .map { DigestScheme(algorithmName = it.algorithm, providerName = it.provider.name) }
        .distinctBy { it.algorithmName }
        .toTypedArray()

    override val providers: Map<String, Provider> get() = providerMap.providers

    override val secureRandom: SecureRandom get() = providerMap.secureRandom

    override fun findKeyScheme(codeName: String): KeyScheme =
        schemes.firstOrNull { it.codeName == codeName }
            ?: throw IllegalArgumentException("Unrecognised scheme code name: $codeName")

    override fun findKeyFactory(scheme: KeyScheme): KeyFactory = providerMap.keyFactories[scheme]

    override fun decodePublicKey(encodedKey: ByteArray): PublicKey = try {
        val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(encodedKey)
        val scheme = findKeyScheme(subjectPublicKeyInfo.algorithm)
        val keyFactory = providerMap.keyFactories[scheme]
        keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
    } catch (e: CryptoServiceLibraryException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceLibraryException("Failed to decode public key", e)
    }

    override fun decodePublicKey(encodedKey: String): PublicKey = try {
        val pemContent = parsePemContent(encodedKey)
        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(pemContent)
        val converter = getJcaPEMKeyConverter(publicKeyInfo)
        val publicKey = converter.getPublicKey(publicKeyInfo)
        toSupportedPublicKey(publicKey)
    } catch (e: CryptoServiceLibraryException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceLibraryException("Failed to decode public key", e)
    }

    override fun deserialize(params: SerializedAlgorithmParameterSpec): AlgorithmParameterSpec {
        val serializer = paramSpecSerializers[params.clazz]
            ?: throw IllegalArgumentException("${params.clazz} is not supported.")
        return serializer.deserialize(params.bytes)
    }

    override fun encodeAsString(publicKey: PublicKey): String = try {
        objectToPem(publicKey)
    } catch (e: CryptoServiceLibraryException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceLibraryException("Failed to encode public key", e)
    }

    override fun toSupportedPublicKey(key: PublicKey): PublicKey {
        return when {
            key::class.java.`package` == BCECPublicKey::class.java.`package` -> key
            key is CompositeKey -> key
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
