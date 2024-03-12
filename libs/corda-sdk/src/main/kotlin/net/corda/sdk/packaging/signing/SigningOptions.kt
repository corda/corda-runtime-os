package net.corda.sdk.packaging.signing

import java.io.File

/**
 * CPx file signing options
 * @property keyStoreFile: File - Keystore holding signing keys
 * @property keyStorePass: String - Keystore password
 * @property keyAlias: String - Key alias
 * @property tsaUrl: String? - Time Stamping Authority (TSA) URL; default: `null`
 * @property signatureFile: String? - Base file name for signature related files; default: `null`
 */
data class SigningOptions(
    val keyStoreFile: File, // TODO use Path
    val keyStorePass: String,
    val keyAlias: String,
    val tsaUrl: String? = null,
    val signatureFile: String? = null,
)