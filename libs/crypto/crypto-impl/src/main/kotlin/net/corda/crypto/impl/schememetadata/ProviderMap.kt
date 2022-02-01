package net.corda.crypto.impl.schememetadata

import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.schemes.COMPOSITE_KEY_TEMPLATE
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_NONE_TEMPLATE
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_TEMPLATE
import net.corda.v5.cipher.suite.schemes.ID_CURVE_25519PH
import net.corda.v5.cipher.suite.schemes.RSA_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SM2_SM3_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SPHINCS256_SHA512_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.CompositeKey
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSASecurityProvider
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jcajce.provider.asymmetric.ec.AlgorithmParametersSpi
import org.bouncycastle.jcajce.provider.util.AsymmetricKeyInfoConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class ProviderMap(
    private val keyEncoder: KeyEncodingService
) {
    private val cordaSecurityProvider = CordaSecurityProvider(keyEncoder)

    private val cordaBouncyCastleProvider: Provider = SecurityProviderWrapper(BouncyCastleProvider().apply {
        addKeyInfoConverter(ID_CURVE_25519PH, object : AsymmetricKeyInfoConverter {
            override fun generatePublic(keyInfo: SubjectPublicKeyInfo) = decodePublicKey(EDDSA_ED25519_NONE, keyInfo.encoded)
            override fun generatePrivate(keyInfo: PrivateKeyInfo) = decodePrivateKey(EDDSA_ED25519_NONE, keyInfo.encoded)
        })
    }).apply {
        putAll(EdDSASecurityProvider())
        // Required due to [X509CRL].verify() reported issues in network-services after BC 1.60 update.
        put("AlgorithmParameters.SHA256WITHECDSA", AlgorithmParametersSpi::class.java.name)
        put("Signature.${EdDSAEngine.SIGNATURE_ALGORITHM}", EdDSAEngine::class.java.name)
        put("Signature.Ed25519", EdDSAEngine::class.java.name)
    }

    private val bouncyCastlePQCProvider = BouncyCastlePQCProvider().apply {
        require(name == "BCPQC") { "Invalid PQCProvider name" }
    }

    val providers: Map<String, Provider> = listOf(
        cordaBouncyCastleProvider,
        cordaSecurityProvider,
        bouncyCastlePQCProvider
    )
        .associateBy(Provider::getName)

    val secureRandom: SecureRandom = SecureRandom.getInstance(CordaSecureRandomService.algorithm, cordaSecurityProvider)

    val keyFactories = KeyFactoryProvider(providers)

    /**
     * RSA PKCS#1 signature scheme.
     * The actual algorithm id is 1.2.840.113549.1.1.1
     * Note: Recommended key size >= 3072 bits.
     */
    @Suppress("VariableNaming", "PropertyName")
    val RSA_SHA256: SignatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
        providerName = cordaBouncyCastleProvider.name
    )

    /** ECDSA signature scheme using the secp256k1 Koblitz curve. */
    @Suppress("VariableNaming", "PropertyName")
    val ECDSA_SECP256K1_SHA256: SignatureScheme = ECDSA_SECP256K1_SHA256_TEMPLATE.makeScheme(
        providerName = cordaBouncyCastleProvider.name
    )

    /** ECDSA signature scheme using the secp256r1 (NIST P-256) curve. */
    @Suppress("VariableNaming", "PropertyName")
    val ECDSA_SECP256R1_SHA256: SignatureScheme = ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme(
        providerName = cordaBouncyCastleProvider.name
    )

    /**
     * EdDSA signature scheme using the ed25519 twisted Edwards curve.
     * The actual algorithm is PureEdDSA Ed25519 as defined in https://tools.ietf.org/html/rfc8032
     * Not to be confused with the EdDSA variants, Ed25519ctx and Ed25519ph.
     */
    @Suppress("VariableNaming", "PropertyName")
    val EDDSA_ED25519_NONE: SignatureScheme = EDDSA_ED25519_NONE_TEMPLATE.makeScheme(
        providerName = cordaBouncyCastleProvider.name
    )

    /** ECDSA signature scheme using the sm2p256v1 (Chinese SM2) curve. */
    @Suppress("VariableNaming", "PropertyName")
    val SM2_SM3: SignatureScheme = SM2_SM3_TEMPLATE.makeScheme(
        providerName = cordaBouncyCastleProvider.name
    )

    /** GOST3410. */
    @Suppress("VariableNaming", "PropertyName")
    val GOST3410_GOST3411: SignatureScheme = GOST3410_GOST3411_TEMPLATE.makeScheme(
        providerName = cordaBouncyCastleProvider.name
    )

    /**
     * SPHINCS-256 hash-based signature scheme using SHA512 for message hashing. It provides 128bit security against
     * post-quantum attackers at the cost of larger key nd signature sizes and loss of compatibility.
     */
    @Suppress("VariableNaming", "PropertyName")
    val SPHINCS256_SHA512: SignatureScheme = SPHINCS256_SHA512_TEMPLATE.makeScheme(
        providerName = bouncyCastlePQCProvider.name
    )

    /** Corda [CompositeKey] signature type. */
    @Suppress("VariableNaming", "PropertyName")
    val COMPOSITE_KEY: SignatureScheme = COMPOSITE_KEY_TEMPLATE.makeScheme(
        providerName = cordaSecurityProvider.name
    )

    /**
     * Decode an X509 encoded key to its [PublicKey] object based on the input scheme code name.
     * This should be used when the type key is known, e.g. during deserialization or with key caches or key managers.
     */
    private fun decodePublicKey(signatureScheme: SignatureScheme, encodedKey: ByteArray): PublicKey {
        try {
            val keyFactory = keyFactories[signatureScheme]
            return keyEncoder.toSupportedPublicKey(keyFactory.generatePublic(X509EncodedKeySpec(encodedKey)))
        } catch (e: InvalidKeySpecException) {
            throw InvalidKeySpecException(
                "This public key cannot be decoded, please ensure it is X509 encoded and " +
                        "that it corresponds to the input scheme's code name.", e
            )
        }
    }

    /**
     * Decode a PKCS8 encoded key to its [PrivateKey] object based on the input scheme code name.
     * This should be used when the type key is known, e.g. during deserialization or with key caches or key managers.
     */
    private fun decodePrivateKey(scheme: SignatureScheme, encodedKey: ByteArray): PrivateKey {
        try {
            val keyFactory = keyFactories[scheme]
            return keyEncoder.toSupportedPrivateKey(keyFactory.generatePrivate(PKCS8EncodedKeySpec(encodedKey)))
        } catch (e: InvalidKeySpecException) {
            throw InvalidKeySpecException(
                "This private key cannot be decoded, please ensure it is PKCS8 encoded and that " +
                        "it corresponds to the input scheme's code name.", e
            )
        }
    }

    // Wrapper needed because BC isn't wired to eddsa, so if we use the BC provider
    // directly we can't access the eddsa contents. By doing this, we force OSGi to
    // select the crypto bundle, which does have an eddsa wiring.
    private class SecurityProviderWrapper(source: Provider) : Provider(source.name, source.versionStr, source.info) {
        init {
            putAll(source)
        }
    }
}