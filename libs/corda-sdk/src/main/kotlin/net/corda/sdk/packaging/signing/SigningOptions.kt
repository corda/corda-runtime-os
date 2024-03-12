package net.corda.sdk.packaging.signing

import java.nio.file.Path

/**
 * CPx file signing options
 * @property keyStoreFile: Path - Keystore holding signing keys
 * @property keyStorePass: String - Keystore password
 * @property keyAlias: String - Key alias
 * @property tsaUrl: String? - Time Stamping Authority (TSA) URL; default: `null`
 * @property signatureFileName: String? - Base file name for signature related files; default: `null`
 */
data class SigningOptions(
    val keyStoreFile: Path,
    val keyStorePass: String,
    val keyAlias: String,
    val tsaUrl: String? = null,
    val signatureFileName: String? = null,
)