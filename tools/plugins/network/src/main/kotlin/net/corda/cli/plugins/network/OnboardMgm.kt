package net.corda.cli.plugins.network

import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.sdk.data.Checksum
import net.corda.sdk.network.ExportGroupPolicyFromMgm
import net.corda.sdk.network.MGM_GROUP_POLICY
import net.corda.sdk.network.RegistrationRequest
import net.corda.sdk.packaging.CpiAttributes
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.packaging.CpiV2Creator
import net.corda.v5.base.exceptions.CordaRuntimeException
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@Command(
    name = "onboard-mgm",
    description = [
        "Onboard MGM",
    ],
    mixinStandardHelpOptions = true,
)
class OnboardMgm : Runnable, BaseOnboard() {
    private companion object {
        const val CPI_VERSION = "1.0"
    }

    @Option(
        names = ["--cpi-hash", "-h"],
        description = [
            "The CPI hash of a previously uploaded CPI. " +
                "If not specified, an auto-generated MGM CPI will be used.",
        ],
    )
    var cpiChecksum: Checksum? = null

    @Option(
        names = ["--save-group-policy-as", "-s"],
        description = ["Location to save the group policy file (default to ~/.corda/gp/groupPolicy.json)"],
    )
    var groupPolicyFile: File =
        File(File(File(File(System.getProperty("user.home")), ".corda"), "gp"), "groupPolicy.json")

    private val groupIdFile: File = File(
        File(File(File(System.getProperty("user.home")), ".corda"), "groupId"),
        "groupId.txt",
    )

    private fun saveGroupPolicy() {
        val groupPolicyResponse = ExportGroupPolicyFromMgm(restClient).exportPolicy(holdingIdentityShortHash = holdingId)
        groupPolicyFile.parentFile.mkdirs()
        json.writerWithDefaultPrettyPrinter()
            .writeValue(
                groupPolicyFile,
                json.readTree(groupPolicyResponse),
            )
        println("Group policy file created at $groupPolicyFile")
        // extract the groupId from the response
        val groupId = json.readTree(groupPolicyResponse).get("groupId").asText()

        // write the groupId to the file
        groupIdFile.apply {
            parentFile.mkdirs()
            writeText(groupId)
        }
    }

    private val tlsTrustRoot by lazy {
        ca.caCertificate.toPem()
    }

    override val memberRegistrationRequest by lazy {
        RegistrationRequest().createMgmRegistrationRequest(
            mtls = mtls,
            p2pGatewayUrls = p2pGatewayUrls,
            sessionKey = sessionKeyId,
            ecdhKey = ecdhKeyId,
            tlsTrustRoot = tlsTrustRoot
        )
    }

    private val cpi by lazy {
        val cpiFile = File.createTempFile("mgm.", ".cpi").also {
            it.deleteOnExit()
            it.delete()
        }
        cpiFile.parentFile.mkdirs()

        val cpiName = "MGM-${UUID.randomUUID()}"
        runCatching {
            CpiV2Creator.createCpi(
                null,
                cpiFile.toPath(),
                MGM_GROUP_POLICY,
                CpiAttributes(cpiName, CPI_VERSION, false),
                createDefaultSingingOptions().asSigningOptionsSdk
            )
        }.onFailure { e -> throw CordaRuntimeException("Create CPI failed: ${e.message}", e) }

        uploadSigningCertificates()
        cpiFile
    }

    override val cpiFileChecksum: Checksum by lazy {
        if (cpiChecksum != null) {
            val existingChecksum = getExistingCpiChecksum(cpiChecksum)
            if (existingChecksum != null) {
                return@lazy existingChecksum
            } else {
                throw IllegalArgumentException("Invalid CPI hash provided. CPI hash does not exist on the Corda cluster.")
            }
        } else {
            val existingChecksum = getExistingCpiChecksum()
            if (existingChecksum != null) {
                return@lazy existingChecksum
            }

            uploadCpi(cpi)
        }
    }

    private fun getExistingCpiChecksum(checksum: Checksum? = null): Checksum? {
        val response = CpiUploader(restClient).getAllCpis(wait = waitDurationSeconds.seconds)
        return response.cpis
            .filter { it.cpiFileChecksum == checksum?.value || (checksum?.value == null && it.groupPolicy?.contains("CREATE_ID") ?: false) }
            .map { it.cpiFileChecksum }
            .firstOrNull()?.let { Checksum(it) }
    }

    override fun run() {
        verifyAndPrintError {
            println("Onboarding MGM '$name'.")

            configureGateway()

            createTlsKeyIdNeeded()

            register()

            setupNetwork()

            println("MGM '$name' was onboarded.")

            saveGroupPolicy()

            if (mtls) {
                println(
                    "To onboard members to this group on other clusters, please add those members' " +
                        "client certificates subjects to this MGM's allowed list. " +
                        "See command: 'allowClientCertificate'.",
                )
            }
        }
    }
}
