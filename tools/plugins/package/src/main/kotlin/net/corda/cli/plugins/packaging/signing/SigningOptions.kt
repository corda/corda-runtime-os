package net.corda.cli.plugins.packaging.signing

import picocli.CommandLine

/**
 * Signing Options to be used by any command that does signing.
 */
class SigningOptions {
    @CommandLine.Option(names = ["--keystore", "-s"], required = true, description = ["Keystore holding signing keys"])
    lateinit var keyStoreFileName: String

    @CommandLine.Option(names = ["--storepass", "--password", "-p"], required = true, description = ["Keystore password"])
    lateinit var keyStorePass: String

    @CommandLine.Option(names = ["--key", "-k"], required = true, description = ["Key alias"])
    lateinit var keyAlias: String

    @CommandLine.Option(names = ["--tsa", "-t"], description = ["Time Stamping Authority (TSA) URL"])
    var tsaUrl: String? = null
}