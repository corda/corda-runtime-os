package net.corda.cli.plugins.packaging

import net.corda.cli.plugins.packaging.FileHelpers.requireFileDoesNotExist
import net.corda.cli.plugins.packaging.FileHelpers.requireFileExists
import net.corda.cli.plugins.packaging.signing.SignWithKmsOptions
import net.corda.cli.plugins.packaging.signing.SigningHelpers
import picocli.CommandLine
import java.nio.file.Files

@CommandLine.Command(
    name = "sign-with-kms",
    description = ["Signs a CPK/CPB/CPI with AWS KMS key."]
)
class SignCpxWithKmsKey : Runnable {

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
    var signWithKmsOptions = SignWithKmsOptions()

    override fun run() {
        val cpxFilePath = requireFileExists(cpxFile)
        val signedCpxPath = requireFileDoesNotExist(outputSignedCpxFile)

        if (multipleSignatures) {
            SigningHelpers.signWithKms(
                cpxFilePath,
                signedCpxPath,
                signWithKmsOptions.keyId,
                signWithKmsOptions.sigFile,
                signWithKmsOptions.tsaUrl
            )
        } else {
            val removedSignaturesCpx = Files.createTempFile("removedSignaturesCpx", null)
            try {
                SigningHelpers.removeSignatures(cpxFilePath, removedSignaturesCpx)
                SigningHelpers.signWithKms(
                    removedSignaturesCpx,
                    signedCpxPath,
                    signWithKmsOptions.keyId,
                    signWithKmsOptions.sigFile,
                    signWithKmsOptions.tsaUrl
                )
            } finally {
                Files.deleteIfExists(removedSignaturesCpx)
            }
        }
    }
}
