package net.corda.crypto.impl

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSchemeMetadata.Companion.BANNED_DIGESTS
import net.corda.v5.cipher.suite.CipherSchemeMetadataProvider
import net.corda.v5.cipher.suite.schemes.DigestScheme
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.osgi.service.component.annotations.Component
import java.security.MessageDigest

@Component(service = [CipherSchemeMetadataProvider::class])
class CipherSchemeMetadataProviderImpl : CipherSchemeMetadataProvider {
    companion object {
        const val SERVICE_NAME = "default"
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

    val providerMap = ProviderMap(::cipherSchemeMetadata)

    private val signatureSchemes: Array<SignatureScheme> = arrayOf(
        providerMap.RSA_SHA256,
        providerMap.ECDSA_SECP256K1_SHA256,
        providerMap.ECDSA_SECP256R1_SHA256,
        providerMap.EDDSA_ED25519_NONE,
        providerMap.SPHINCS256_SHA512,
        providerMap.SM2_SM3,
        providerMap.GOST3410_GOST3411,
        providerMap.COMPOSITE_KEY
    )

    private val algorithmMap: Map<AlgorithmIdentifier, SignatureScheme> = signatureSchemes.flatMap { scheme ->
        scheme.algorithmOIDs.map { identifier -> identifier to scheme }
    }.toMap()

    private val digests: Array<DigestScheme> = providerMap.providers.values
        .flatMap { it.services }
        .filter {
            it.type.equals(MESSAGE_DIGEST_TYPE, true)
                    && !BANNED_DIGESTS.contains(it.algorithm)
                    && DIGEST_CANDIDATES.contains(it.algorithm)
        }
        .map { DigestScheme(algorithmName = it.algorithm, providerName = it.provider.name) }
        .distinctBy { it.algorithmName }
        .toTypedArray()

    private val cipherSchemeMetadata: CipherSchemeMetadata by lazy(LazyThreadSafetyMode.PUBLICATION) {
        CipherSchemeMetadataImpl(
            providers = providerMap.providers,
            schemes = signatureSchemes,
            digests = digests,
            secureRandom = providerMap.secureRandom,
            keyFactories = providerMap.keyFactories,
            algorithmMap = algorithmMap
        )
    }

    override val name: String = SERVICE_NAME

    override fun getInstance(): CipherSchemeMetadata = cipherSchemeMetadata
}
