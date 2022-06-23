package net.corda.crypto.impl.schememetadata

import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.schemes.COMPOSITE_KEY_TEMPLATE
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.CompositeKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import java.security.Provider
import java.security.SecureRandom

class ProviderMap(
    keyEncoder: KeyEncodingService
) {
    private val cordaSecurityProvider = CordaSecurityProvider(keyEncoder)

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
     * RSA PKCS#1 signature scheme.
     * The actual algorithm id is 1.2.840.113549.1.1.1
     * Note: Recommended key size >= 3072 bits.
     */
    @Suppress("VariableNaming", "PropertyName")
    val RSA: KeySchemeInfo = RSAKeySchemeInfo(cordaBouncyCastleProvider)

    /** ECDSA signature scheme using the secp256k1 Koblitz curve. */
    @Suppress("VariableNaming", "PropertyName")
    val ECDSA_SECP256K1: KeySchemeInfo = ECDSAK1KeySchemeInfo(cordaBouncyCastleProvider)

    /** ECDSA signature scheme using the secp256r1 (NIST P-256) curve. */
    @Suppress("VariableNaming", "PropertyName")
    val ECDSA_SECP256R1: KeySchemeInfo = ECDSAR1KeySchemeInfo(cordaBouncyCastleProvider)

    /**
     * EdDSA signature scheme using the ed25519 twisted Edwards curve.
     * The actual algorithm is PureEdDSA Ed25519 as defined in https://tools.ietf.org/html/rfc8032
     * Not to be confused with the EdDSA variants, Ed25519ctx and Ed25519ph.
     */
    @Suppress("VariableNaming", "PropertyName")
    val EDDSA_ED25519: KeySchemeInfo = EDDSAKeySchemeInfo(cordaBouncyCastleProvider)

    /** ECDSA signature scheme using the sm2p256v1 (Chinese SM2) curve. */
    @Suppress("VariableNaming", "PropertyName")
    val SM2: KeySchemeInfo = SM2KeySchemeInfo(cordaBouncyCastleProvider)

    /** GOST3410. */
    @Suppress("VariableNaming", "PropertyName")
    val GOST3410_GOST3411: KeySchemeInfo = GOST3410GOST3411KeySchemeInfo(cordaBouncyCastleProvider)

    /**
     * SPHINCS-256 hash-based signature scheme using SHA512 for message hashing. It provides 128bit security against
     * post-quantum attackers at the cost of larger key nd signature sizes and loss of compatibility.
     */
    @Suppress("VariableNaming", "PropertyName")
    val SPHINCS256: KeySchemeInfo = SPHINCS256KeySchemeInfo(bouncyCastlePQCProvider)

    /** Corda [CompositeKey] signature type. */
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
}