package net.corda.crypto.impl.config

class CipherSuiteConfig(
    map: Map<String, Any?>
) : CryptoConfigMap(map) {
    companion object {
        const val DEFAULT_VALUE = "default"
    }

    val schemeMetadataProvider: String
        get() = getString(this::schemeMetadataProvider.name, DEFAULT_VALUE)

    val signatureVerificationProvider: String
        get() = getString(this::signatureVerificationProvider.name, DEFAULT_VALUE)

    val digestProvider: String
        get() = getString(this::digestProvider.name, DEFAULT_VALUE)
}