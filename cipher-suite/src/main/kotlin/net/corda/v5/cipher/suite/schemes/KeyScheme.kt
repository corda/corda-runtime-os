package net.corda.v5.cipher.suite.schemes

import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.spec.AlgorithmParameterSpec

/**
 * This class is used to define a digital key scheme.
 *
 * @property codeName unique code name for this key scheme (e.g. CORDA.RSA, CORDA.ECDSA.SECP256K1, CORDA.ECDSA_SECP256R1,
 * CORDA.EDDSA.ED25519, CORDA.SPHINCS-256).
 * @property algorithmOIDs ASN.1 algorithm identifiers for keys of the signature which are used to match keys
 * to their schemes. There must be at least one defined.
 * @property providerName the provider's name (e.g. "BC") which supports the key scheme.
 * @property algorithmName which signature algorithm is used (e.g. RSA, ECDSA. EdDSA, SPHINCS-256).
 * @property algSpec parameter specs for the underlying algorithm. Note that RSA is defined by the key size
 * rather than algSpec. eg. ECGenParameterSpec("secp256k1").
 * @property keySize the private key size (currently used for RSA only), it's used to initialize the key generator
 * if the [algSpec] is not specified, if [algSpec] and [keySize] are bth null then default initialization is used.
 */
data class KeyScheme(
    val codeName: String,
    val algorithmOIDs: List<AlgorithmIdentifier>,
    val providerName: String,
    val algorithmName: String,
    val algSpec: AlgorithmParameterSpec?,
    val keySize: Int?
) {
    init {
        require(codeName.isNotBlank()) { "The codeName must not be blank." }
        require(providerName.isNotBlank()) { "The providerName must not be blank." }
        require(algorithmName.isNotBlank()) { "The algorithmName must not be blank." }
        require(algorithmOIDs.isNotEmpty()) { "The algorithmOIDs must not be empty." }
    }

    override fun toString(): String =
        "$codeName($providerName,$algorithmName)"
}

