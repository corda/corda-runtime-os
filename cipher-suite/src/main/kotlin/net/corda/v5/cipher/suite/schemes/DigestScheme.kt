package net.corda.v5.cipher.suite.schemes

/**
 * This class is used to define a digital digest scheme.
 * @param algorithmName digest's algorithm name (e.g. SHA-256, SHA-512, etc.).
 * @param providerName the provider's name (e.g. "BC").
 */
data class DigestScheme(val algorithmName: String, val providerName: String) {
    init {
        require(algorithmName.isNotBlank()) { "The algorithmName must not be blank." }
        require(providerName.isNotBlank()) { "The providerName must not be blank." }
    }
}
