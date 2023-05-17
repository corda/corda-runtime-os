package net.corda.cli.plugins.packaging

import net.corda.cli.plugins.packaging.FileHelpers.requireFileDoesNotExist
import net.corda.cli.plugins.packaging.FileHelpers.requireFileExists
import net.corda.cli.plugins.packaging.signing.SigningHelpers
import net.corda.cli.plugins.packaging.signing.SigningHelpers.checkInputParametersForSigning
import net.corda.cli.plugins.packaging.signing.SigningOptions
import picocli.CommandLine
import java.nio.file.Files

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

    @Suppress("NestedBlockDepth")
    override fun run() {
        checkInputParametersForSigning(signingOptions.keyStoreFileName, signingOptions.keyStorePass,
            signingOptions.keyProvider, signingOptions.certChain)
        val cpxFilePath = requireFileExists(cpxFile)
        val signedCpxPath = requireFileDoesNotExist(outputSignedCpxFile)

        when (signingOptions.keyProvider) {
            "local" -> {
                if (multipleSignatures) {
                    signingOptions.keyStoreFileName?.let {
                        signingOptions.keyStorePass?.let { it1 ->
                            SigningHelpers.sign(
                                cpxFilePath,
                                signedCpxPath,
                                it,
                                it1,
                                signingOptions.keyAlias,
                                signingOptions.sigFile,
                                signingOptions.tsaUrl
                            )
                        }
                    }
                } else {
                    val removedSignaturesCpx = Files.createTempFile("removedSignaturesCpx", null)
                    try {
                        SigningHelpers.removeSignatures(cpxFilePath, removedSignaturesCpx)
                        signingOptions.keyStoreFileName?.let {
                            signingOptions.keyStorePass?.let { it1 ->
                                SigningHelpers.sign(
                                    removedSignaturesCpx,
                                    signedCpxPath,
                                    it,
                                    it1,
                                    signingOptions.keyAlias,
                                    signingOptions.sigFile,
                                    signingOptions.tsaUrl
                                )
                            }
                        }
                    } finally {
                        Files.deleteIfExists(removedSignaturesCpx)
                    }
                }
            }
            "KMS" -> {
                val certChainPath = signingOptions.certChain?.let { requireFileExists(it) }

                if (multipleSignatures) {
                    if (certChainPath != null) {
                        SigningHelpers.signWithKms(
                            cpxFilePath,
                            signedCpxPath,
                            certChainPath,
                            signingOptions.keyAlias,
                            signingOptions.sigFile,
                            signingOptions.tsaUrl
                        )
                    }
                } else {
                    val removedSignaturesCpx = Files.createTempFile("removedSignaturesCpx", null)
                    try {
                        SigningHelpers.removeSignatures(cpxFilePath, removedSignaturesCpx)
                        if (certChainPath != null) {
                            SigningHelpers.signWithKms(
                                removedSignaturesCpx,
                                signedCpxPath,
                                certChainPath,
                                signingOptions.keyAlias,
                                signingOptions.sigFile,
                                signingOptions.tsaUrl
                            )
                        }
                    } finally {
                        Files.deleteIfExists(removedSignaturesCpx)
                    }
                }
            }
        }


    }
}