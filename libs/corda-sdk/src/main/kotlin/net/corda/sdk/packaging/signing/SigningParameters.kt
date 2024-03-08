package net.corda.sdk.packaging.signing

/**
 * Signing Options
 */
data class SigningParameters(
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
