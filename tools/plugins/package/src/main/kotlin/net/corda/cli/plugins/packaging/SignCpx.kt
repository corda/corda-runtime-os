package net.corda.cli.plugins.packaging

import java.nio.file.Files
import net.corda.cli.plugins.packaging.FileHelpers.requireFileDoesNotExist
import net.corda.cli.plugins.packaging.FileHelpers.requireFileExists
import net.corda.cli.plugins.packaging.signing.SigningHelpers
import net.corda.cli.plugins.packaging.signing.SigningOptions
import picocli.CommandLine

@CommandLine.Command(
    name = "sign",
    description = ["Signs a CPK/CPB/CPI."]
)
class SignCpx : Runnable {

    @CommandLine.Parameters(index = "0", paramLabel = "CPI or CPB or CPK", description=["Path of the input CPI or CPB or CPK"])
    lateinit var cpxFile: String

    @CommandLine.Option(
        names = ["--multiple-signatures"],
        arity = "1",
        description = ["Adds new signature keeping existing signatures. If set to false, it adds new signature but erases previous ones"])
    var multipleSignatures: Boolean = false

    @CommandLine.Option(names = ["--file", "-f"], required = true, description = ["Output signed CPI or CPB or CPK file name"])
    lateinit var outputSignedCpxFile: String

    @CommandLine.Mixin
    var signingOptions = SigningOptions()

    override fun run() {
        val cpxFilePath = requireFileExists(cpxFile)
        val signedCpxPath = requireFileDoesNotExist(outputSignedCpxFile)

        if (multipleSignatures) {
            SigningHelpers.sign(
                cpxFilePath,
                signedCpxPath,
                signingOptions.keyStoreFileName,
                signingOptions.keyStorePass,
                signingOptions.keyAlias,
                signingOptions.sigFile,
                signingOptions.tsaUrl
            )
        } else {
            val removedSignaturesCpx = Files.createTempFile("removedSignaturesCpx", null)
            try {
                SigningHelpers.removeSignatures(cpxFilePath, removedSignaturesCpx)
                SigningHelpers.sign(
                    removedSignaturesCpx,
                    signedCpxPath,
                    signingOptions.keyStoreFileName,
                    signingOptions.keyStorePass,
                    signingOptions.keyAlias,
                    signingOptions.sigFile,
                    signingOptions.tsaUrl
                )
            } finally {
                Files.deleteIfExists(removedSignaturesCpx)
            }
        }
    }
}