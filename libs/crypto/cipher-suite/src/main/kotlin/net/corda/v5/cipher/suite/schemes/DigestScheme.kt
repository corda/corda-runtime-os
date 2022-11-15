package net.corda.v5.cipher.suite.schemes

/**
 * This class is used to define a digital digest scheme.
 * @param algorithmName Digest's algorithm name (e.g. SHA-256, SHA-512, etc.).
 * @param providerName The provider's name (e.g. "BC") which supports the scheme.
 */
data class DigestScheme(
    /**
     * Digest algorithm name.
     */
    val algorithmName: String,
    /**
     * The provider name.
     */
    val providerName: String
) {
    init {
        require(algorithmName.isNotBlank()) { "The algorithmName must not be blank." }
        require(providerName.isNotBlank()) { "The providerName must not be blank." }
    }
}
