package net.corda.cli.plugins.packaging

import net.corda.cli.plugins.packaging.aws.kms.KmsProvider
import net.corda.cli.plugins.packaging.aws.kms.ec.KmsECKeyFactory
import net.corda.cli.plugins.packaging.aws.kms.rsa.KmsRSAKeyFactory
import net.corda.cli.plugins.packaging.aws.kms.signature.KmsSigningAlgorithm
import net.corda.cli.plugins.packaging.aws.kms.utils.csr.CsrGenerator
import net.corda.cli.plugins.packaging.aws.kms.utils.csr.CsrInfo
import picocli.CommandLine
import java.io.File
import java.security.Security

@CommandLine.Command(
    name = "create-csr-for-kms-key",
    description = ["Create a CSR for a KMS signing key."]
)
class CreateCsrForKmsKey : Runnable {

    @CommandLine.Option(names = ["--key-id", "-k"], required = true, description = ["Key id of AWS KMS key"])
    lateinit var keyId: String

    @CommandLine.Option(names = ["--csr-file", "-f"], required = true, description = ["File to store the CSR"])
    lateinit var csrFile: String

    @CommandLine.Option(names = ["--common-name", "--cn", "-n"], description = ["Common name"])
    var commonName: String? = null

    @CommandLine.Option(names = ["--organizational-unit", "--ou", "-d"], description = ["Department Name/Organizational Unit"])
    var organizationalUnit: String? = null

    @CommandLine.Option(names = ["--organization", "-o"], description = ["Business Name/Organization"])
    var organization: String? = null

    @CommandLine.Option(names = ["--locality", "-l"], description = ["Town/City"])
    var locality: String? = null

    @CommandLine.Option(names = ["--state", "--st", "-s"], description = ["Province, Region, County or State"])
    var state: String? = null

    @CommandLine.Option(names = ["--country", "-c"], description = ["Country"])
    var country: String? = null

    @CommandLine.Option(names = ["--mail", "-m"], description = ["Email address"])
    var mail: String? = null

    override fun run() {
        val csrFilePath = FileHelpers.requireFileDoesNotExist(csrFile)

        val kmsClient = getKmsClient()
        val kmsProvider = KmsProvider(kmsClient)
        Security.addProvider(kmsProvider) // It is important to register the provider!

        val keyIsRsa = isRsaKeyType(kmsClient, keyId)
        val keyPair = if (keyIsRsa) { KmsRSAKeyFactory.getKeyPair(kmsClient, keyId) } else { KmsECKeyFactory.getKeyPair(kmsClient, keyId) }
        val kmsSigningAlgorithm = if (keyIsRsa) { KmsSigningAlgorithm.RSASSA_PKCS1_V1_5_SHA_256 } else { KmsSigningAlgorithm.ECDSA_SHA_256 }

        val csrInfo = CsrInfo.CsrInfoBuilder()
            .cn(commonName) //Common Name
            .ou(organizationalUnit) //Department Name / Organizational Unit
            .o(organization) //Business name / Organization
            .l(locality) //Town / City
            .st(state) //Province, Region, County or State
            .c(country) //Country
            .mail(mail) //Email address
            .build()

        val csr = CsrGenerator.generate(keyPair, csrInfo, kmsSigningAlgorithm)
        File(csrFilePath.toString()).writeText(csr)

        kmsClient.close()
    }
}
