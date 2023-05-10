package net.corda.cli.plugins.packaging.signing

import picocli.CommandLine

/**
 * Signing Options to be used by any command that does signing.
 */

class SignWithKmsOptions {
    @CommandLine.Option(names = ["--key", "-k"], required = true, description = ["Key id of AWS KMS key"])
    lateinit var keyId: String

    @CommandLine.Option(names = ["--tsa", "-t"], description = ["Time Stamping Authority (TSA) URL"])
    var tsaUrl: String? = null

    @CommandLine.Option(names = ["--sig-file"], description = ["Base file name for signature related files"])
    private var _sigFile: String? = null

    // The following has the same behavior as jarsigner in terms of signature files naming.
    val sigFile: String
        get() =
            _sigFile ?: keyId.run {
                var str = this
                if (str.length > 8) {
                    str = str.substring(0, 8).uppercase()
                }
                val strBuilder = StringBuilder()
                for (c in str) {
                    @Suppress("ComplexCondition")
                    if (c in 'A'..'Z' || c in 'a'..'z' || c == '-' || c == '_')
                        strBuilder.append(c)
                    else
                        strBuilder.append('_')
                }
                str = strBuilder.toString()
                str
            }
}
