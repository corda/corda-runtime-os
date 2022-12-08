package net.corda.cipher.suite.impl

import net.corda.crypto.cipher.suite.AlgorithmParameterSpecEncodingService
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.schemes.AlgorithmParameterSpecSerializer
import net.corda.crypto.cipher.suite.schemes.DigestScheme
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.SerializedAlgorithmParameterSpec
import net.corda.crypto.impl.CipherSchemeMetadataProvider
import net.corda.crypto.impl.PSSParameterSpecSerializer
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.osgi.service.component.annotations.Component
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.PSSParameterSpec

@Component(
    service = [
        CipherSchemeMetadata::class,
        KeyEncodingService::class,
        AlgorithmParameterSpecEncodingService::class,
        SingletonSerializeAsToken::class
    ]
)
@Suppress("TooManyFunctions")
class CipherSchemeMetadataImpl : CipherSchemeMetadata, SingletonSerializeAsToken {
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

    private val metadataProvider = CipherSchemeMetadataProvider()

    private val paramSpecSerializers = mapOf<String, AlgorithmParameterSpecSerializer<out AlgorithmParameterSpec>>(
        PSSParameterSpec::class.java.name to PSSParameterSpecSerializer()
    )

    override val schemes: List<KeyScheme> = metadataProvider.schemes

    override val digests: List<DigestScheme> = metadataProvider.providers.values
        .flatMap { it.services }
        .filter {
            it.type.equals(MESSAGE_DIGEST_TYPE, true)
                    && !CipherSchemeMetadata.BANNED_DIGESTS.contains(it.algorithm)
                    && DIGEST_CANDIDATES.contains(it.algorithm)
        }
        .map { DigestScheme(algorithmName = it.algorithm, providerName = it.provider.name) }
        .distinctBy { it.algorithmName }

    override val providers: Map<String, Provider> get() = metadataProvider.providers

    override val secureRandom: SecureRandom get() = metadataProvider.secureRandom

    override fun defaultSignatureSpec(publicKey: PublicKey): SignatureSpec? =
        metadataProvider.keySchemeInfoMap[findKeyScheme(publicKey)]?.defaultSignatureSpec

    override fun inferSignatureSpec(publicKey: PublicKey, digest: DigestAlgorithmName): SignatureSpec? =
        metadataProvider.keySchemeInfoMap[findKeyScheme(publicKey)]?.getSignatureSpec(digest)

    override fun supportedSignatureSpec(scheme: KeyScheme): List<SignatureSpec> =
        metadataProvider.keySchemeInfoMap[scheme]?.digestToSignatureSpecMap?.values?.toList() ?: emptyList()

    // TODO This can now only return one `SignatureSpec` for the specified key and digest due to underlying infrastructure/ apis.
    //  This infrastructure should be modified to take into account more crypto parameters (like RSA padding).
    //  `supportedSignatureSpec(scheme: KeyScheme, digest: DigestAlgorithmName)` should be able to return > 1 signature specs.
    override fun supportedSignatureSpec(scheme: KeyScheme, digest: DigestAlgorithmName): List<SignatureSpec> =
        metadataProvider.keySchemeInfoMap[scheme]?.getSignatureSpec(digest)?.let {
            listOf(it)
        } ?: emptyList()

    override fun inferableDigestNames(scheme: KeyScheme): List<DigestAlgorithmName> =
        metadataProvider.keySchemeInfoMap[scheme]?.digestToSignatureSpecMap?.keys?.toList() ?: emptyList()

    override fun findKeyScheme(algorithm: AlgorithmIdentifier): KeyScheme =
        metadataProvider.findKeyScheme(algorithm)

    override fun findKeyScheme(key: PublicKey): KeyScheme {
        val keyInfo = SubjectPublicKeyInfo.getInstance(key.encoded)
        return findKeyScheme(keyInfo.algorithm)
    }

    override fun findKeyScheme(codeName: String): KeyScheme =
        schemes.firstOrNull { it.codeName == codeName }
            ?: throw IllegalArgumentException("Unrecognised scheme code name: $codeName")

    override fun findKeyFactory(scheme: KeyScheme): KeyFactory = metadataProvider.keyFactories[scheme]

    override fun decodePublicKey(encodedKey: ByteArray): PublicKey = metadataProvider.decodePublicKey(encodedKey)

    override fun decodePublicKey(encodedKey: String): PublicKey = metadataProvider.decodePublicKey(encodedKey)

    override fun encodeAsString(publicKey: PublicKey): String = metadataProvider.encodeAsString(publicKey)

    override fun toSupportedPublicKey(key: PublicKey): PublicKey = metadataProvider.toSupportedPublicKey(key)

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

    override fun deserialize(params: SerializedAlgorithmParameterSpec): AlgorithmParameterSpec {
        val serializer = paramSpecSerializers[params.clazz]
            ?: throw IllegalArgumentException("${params.clazz} is not supported.")
        return serializer.deserialize(params.bytes)
    }
}
