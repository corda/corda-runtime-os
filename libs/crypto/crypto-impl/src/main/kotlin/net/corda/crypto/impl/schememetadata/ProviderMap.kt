package net.corda.crypto.impl.schememetadata

import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.schemes.COMPOSITE_KEY_TEMPLATE
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_NONE_TEMPLATE
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_TEMPLATE
import net.corda.v5.cipher.suite.schemes.RSA_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SM2_SM3_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SPHINCS256_SHA512_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.CompositeKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import java.security.Provider
import java.security.SecureRandom

class ProviderMap(
    keyEncoder: KeyEncodingService
) {
    private val cordaSecurityProvider = CordaSecurityProvider(keyEncoder)

    private val cordaBouncyCastleProvider: Provider = SecurityProviderWrapper(BouncyCastleProvider())

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

    // Wrapper needed because BC isn't wired to eddsa, so if we use the BC provider
    // directly we can't access the eddsa contents. By doing this, we force OSGi to
    // select the crypto bundle, which does have an eddsa wiring.
    private class SecurityProviderWrapper(source: Provider) : Provider(source.name, source.versionStr, source.info) {
        init {
            putAll(source)
        }
    }
}