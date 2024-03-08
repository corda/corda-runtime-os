package net.corda.sdk.packaging.signing

/**
 * CPx file signing options
 * @property keyStoreFileName: String - Keystore holding signing keys
 * @property keyStorePass: String - Keystore password
 * @property keyAlias: String - Key alias
 * @property tsaUrl: String? - Time Stamping Authority (TSA) URL; default: `null`
 * @property signatureFile: String - Base file name for signature related files;
 * If not provided, generate from [keyAlias], following jarsigner requirements
 */
data class SigningOptions(
    val keyStoreFileName: String,
    val keyStorePass: String,
    val keyAlias: String,
    val tsaUrl: String? = null,
    private val _signatureFile: String? = null,
) {
    // The following has the same behavior as jarsigner in terms of signature files naming.
    val signatureFile: String
        get() =
            _signatureFile ?: keyAlias.run {
                var str = this
                if (str.length > 8) {
                    str = str.substring(0, 8).uppercase()
                }
                val strBuilder = StringBuilder()
                for (c in str) {
                    @Suppress("ComplexCondition")
                    if (c in 'A'..'Z' || c in 'a'..'z' || c == '-' || c == '_') {
                        strBuilder.append(c)
                    } else {
                        strBuilder.append('_')
                    }
                }
                str = strBuilder.toString()
                str
            }
}
