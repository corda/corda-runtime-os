package net.corda.cli.commands.packaging.signing

import net.corda.sdk.packaging.signing.SigningOptions as SigningOptionsSdk
import picocli.CommandLine
import java.nio.file.Path

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

    @CommandLine.Option(names = ["--sig-file"], description = ["Base file name for signature related files"])
    var sigFile: String? = null

    val asSigningOptionsSdk: SigningOptionsSdk
        get() = SigningOptionsSdk(Path.of(keyStoreFileName), keyStorePass, keyAlias, tsaUrl, sigFile)
}