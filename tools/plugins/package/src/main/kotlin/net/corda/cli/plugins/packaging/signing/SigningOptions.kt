package net.corda.cli.plugins.packaging.signing

import picocli.CommandLine

/**
 * Signing Options to be used by any command that does signing.
 */
class SigningOptions {
    @CommandLine.Option(names = ["--keystore", "-s"], description = ["Keystore holding signing keys"])
    var keyStoreFileName: String? = null

    @CommandLine.Option(names = ["--storepass", "--password", "-p"], description = ["Keystore password"])
    var keyStorePass: String? = null

    @CommandLine.Option(names = ["--key", "-k"], required = true, description = ["Key alias or keyId if using AWS KMS"])
    lateinit var keyAlias: String

    @CommandLine.Option(names = ["--key-provider", "-r"], description = ["Key provider: local or KMS"])
    var keyProvider: String = "local"

    @CommandLine.Option(names = ["--crt-chain"], description = ["Certificate chain"])
    var certChain: String? = null

    @CommandLine.Option(names = ["--tsa", "-t"], description = ["Time Stamping Authority (TSA) URL"])
    var tsaUrl: String? = null

    @CommandLine.Option(names = ["--sig-file"], description = ["Base file name for signature related files"])
    private var _sigFile: String? = null

    // The following has the same behavior as jarsigner in terms of signature files naming.
    val sigFile: String
        get() =
            _sigFile ?: keyAlias.run {
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