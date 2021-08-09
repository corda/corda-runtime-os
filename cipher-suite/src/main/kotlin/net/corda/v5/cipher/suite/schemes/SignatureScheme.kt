package net.corda.v5.cipher.suite.schemes

import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.spec.AlgorithmParameterSpec

/**
 * This class is used to define a digital key scheme.
 * @param codeName unique code name for this key scheme (e.g. CORDA.RSA, CORDA.ECDSA.SECP256K1, CORDA.ECDSA_SECP256R1,
 * CORDA.EDDSA.ED25519, CORDA.SPHINCS-256).
 * @param algorithmOIDs ASN.1 algorithm identifiers for keys of the signature which are used to match keys to their schemes.
 * There must be at least one defined.
 * @param providerName the provider's name (e.g. "BC").
 * @param algorithmName which signature algorithm is used (e.g. RSA, ECDSA. EdDSA, SPHINCS-256).
 * @param algSpec parameter specs for the underlying algorithm. Note that RSA is defined by the key size rather than algSpec.
 * eg. ECGenParameterSpec("secp256k1").
 * @param keySize the private key size (currently used for RSA only), it's used to initialize the key generator if the [algSpec] is not specified,
 * if [algSpec] and [keySize] are bth null then default initialization is used.
 * @param signatureSpec the signature scheme
 */
data class SignatureScheme(
    val codeName: String,
    val algorithmOIDs: List<AlgorithmIdentifier>,
    val providerName: String,
    val algorithmName: String,
    val algSpec: AlgorithmParameterSpec?,
    val keySize: Int?,
    val signatureSpec: SignatureSpec
) {
    init {
        require(codeName.isNotBlank()) { "The codeName must not be blank." }
        require(providerName.isNotBlank()) { "The providerName must not be blank." }
        require(algorithmName.isNotBlank()) { "The algorithmName must not be blank." }
        require(algorithmOIDs.isNotEmpty()) { "The algorithmOIDs must not be empty." }
    }
}

