package net.corda.v5.cipher.suite.schemes

import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.spec.AlgorithmParameterSpec

/**
 * This class is used to define a digital key scheme template.
 *
 * @param codeName Unique code name for this key scheme (e.g. CORDA.RSA, CORDA.ECDSA.SECP256K1, CORDA.ECDSA_SECP256R1,
 * CORDA.EDDSA.ED25519, CORDA.SPHINCS-256).
 * @param algorithmOIDs ASN.1 algorithm identifiers for keys which are used to match keys
 * to their schemes. There must be at least one defined.
 * @param algorithmName Key algorithm (e.g. RSA, ECDSA. EdDSA, SPHINCS-256).
 * @param algSpec Parameter specs for the underlying algorithm. Note that RSA is defined by the key size
 * rather than algSpec. eg. ECGenParameterSpec("secp256k1").
 * @param keySize The private key size (currently used for RSA only), it's used to initialize the key generator
 * if the [algSpec] is not specified, if [algSpec] and [keySize] are bth null then default initialization is used.
 * @param capabilities Defines the usage of the key, there must be at least one specified.
 */
@Suppress("LongParameterList")
data class KeySchemeTemplate(
    /**
     * Unique name for this key scheme.
     */
    val codeName: String,
    /**
     * ASN.1 algorithm identifiers for the key scheme.
     */
    val algorithmOIDs: List<AlgorithmIdentifier>,
    /**
     * Key's algorithm name.
     */
    val algorithmName: String,
    /**
     * Parameter specs for the underlying algorithm.
     */
    val algSpec: AlgorithmParameterSpec?,
    /**
     * The key size, normally defined only for RSA.
     */
    val keySize: Int?,
    /**
     * How the key can be used.
     */
    val capabilities: Set<KeySchemeCapability>
) {
    init {
        require(codeName.isNotBlank()) { "The codeName must not be blank." }
        require(algorithmName.isNotBlank()) { "The algorithmName must not be blank." }
        require(algorithmOIDs.isNotEmpty()) { "The algorithmOIDs must not be empty." }
        require(capabilities.isNotEmpty()) { "There must be defined at least one capability." }
    }

    /**
     * Creates the [KeyScheme] out of the template.
     */
    fun makeScheme(providerName: String): KeyScheme = KeyScheme(
        providerName = providerName,
        codeName = codeName,
        algorithmOIDs = algorithmOIDs.toList(),
        algorithmName = algorithmName,
        algSpec = algSpec,
        keySize = keySize,
        capabilities = capabilities
    )
}

