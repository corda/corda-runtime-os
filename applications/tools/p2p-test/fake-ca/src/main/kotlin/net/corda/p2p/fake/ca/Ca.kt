package net.corda.p2p.fake.ca

import net.corda.crypto.test.certificates.generation.Algorithm
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.KeysFactoryDefinitions
import org.bouncycastle.jce.ECNamedCurveTable
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.security.Security
import java.time.Duration

@Command(
    header = ["Fake CA certificate"],
    name = "fake-ca",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    showDefaultValues = true,
    subcommands = [
        CreateCa::class,
        CreateCertificate::class,
    ],
    showAtFileInUsageHelp = true,
    subcommandsRepeatable = true,
)
class Ca {
    companion object {
        internal const val CA_NAME = "ca"
    }
    @Option(
        names = ["-m", "--home"],
        description = ["The CA home directory where certificates/keys will be generated"],
    )
    internal var home = File(File(System.getProperty("user.home")), ".fake.ca")

    @Option(
        names = ["-d", "--duration", "--certificates-duration"],
        description = ["The duration of each certificate (in days)"],
    )
    private var certificatesDurationInDays: Int = 30

    @Option(
        names = ["-a", "--algorithm", "--alg"],
        description = [
            "The algorithm to be used for the generated keys",
            "(one of: \${COMPLETION-CANDIDATES})"
        ],
    )
    private var algorithm: Algorithm = Algorithm.EC

    @Option(
        names = ["-s", "--key-size"],
        description = [
            "The key size in number of bits (one of 2048, 3072 or 4096).",
            "Valid only for RSA algorithm."
        ],
    )
    private var keySize: Int = 2048

    @Option(
        names = ["-c", "--curve-name"],
        description = [
            "The curve name (one of: \${COMPLETION-CANDIDATES}).",
            "Valid only for EC algorithms"
        ],
        completionCandidates = ValidCurvedNames::class
    )
    private var curveName: String = "secp256r1"

    internal val authority by lazy {
        if (certificatesDurationInDays <= 0) {
            throw FakeCaException("Invalid duration: $certificatesDurationInDays")
        }
        home.mkdirs()
        if (!home.isDirectory) {
            throw FakeCaException("Invalid home directory: $home")
        }
        val definitions = when (algorithm) {
            Algorithm.RSA -> {
                if ((keySize != 2048) &&
                    (keySize != 4096) &&
                    (keySize != 3072)
                ) {
                    throw FakeCaException("Invalid key size: $keySize")
                }
                KeysFactoryDefinitions(
                    algorithm,
                    keySize = keySize,
                    spec = null
                )
            }
            Algorithm.EC -> {
                val disabled = Security.getProperty("jdk.disabled.namedCurves")
                    .split(",").map {
                        it.trim()
                    }.contains(curveName)
                if (disabled) {
                    throw FakeCaException("Curve name: $curveName disabled")
                }
                val spec = ECNamedCurveTable.getParameterSpec(curveName) ?: throw FakeCaException("Unknown curve name: $curveName")
                if (!ValidCurvedNames().contains(curveName)) {
                    throw FakeCaException("Invalid curve name: $curveName")
                }
                KeysFactoryDefinitions(
                    algorithm,
                    keySize = null,
                    spec = spec
                )
            }
        }
        CertificateAuthorityFactory.createFileSystemLocalAuthority(
            definitions,
            File(home, "$CA_NAME/.ca"),
            Duration.ofDays(certificatesDurationInDays.toLong())
        )
    }
}
