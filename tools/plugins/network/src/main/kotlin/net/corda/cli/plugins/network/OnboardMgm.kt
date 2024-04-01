package net.corda.cli.plugins.network

import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.sdk.network.ExportGroupPolicyFromMgm
import net.corda.sdk.network.RegistrationRequest
import net.corda.sdk.packaging.CpiAttributes
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.packaging.CpiV2Creator
import net.corda.sdk.rest.RestClientUtils
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
    var cpiHash: String? = null

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

    private val cpiName: String = "MGM-${UUID.randomUUID()}"

    private val mgmGroupPolicy by lazy {
        mapOf(
            "fileFormatVersion" to 1,
            "groupId" to "CREATE_ID",
            "registrationProtocol" to "net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
            "synchronisationProtocol" to "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl",
        ).let { groupPolicyMap ->
            json.writeValueAsString(groupPolicyMap)
        }
    }

    private fun saveGroupPolicy() {
        val restClient = RestClientUtils.createRestClient(
            MGMRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val groupPolicyResponse = ExportGroupPolicyFromMgm().exportPolicy(restClient, holdingId, waitDurationSeconds.seconds)
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

        runCatching {
            CpiV2Creator.createCpi(
                null,
                cpiFile.toPath(),
                mgmGroupPolicy,
                CpiAttributes(cpiName, CPI_VERSION, false),
                createDefaultSingingOptions().asSigningOptionsSdk
            )
        }.onFailure { e -> throw CordaRuntimeException("Create CPI failed: ${e.message}", e) }

        uploadSigningCertificates()
        cpiFile
    }

    override val cpiFileChecksum: String by lazy {
        if (cpiHash != null) {
            val existingHash = getExistingCpiHash(cpiHash)
            if (existingHash != null) {
                return@lazy existingHash
            } else {
                throw IllegalArgumentException("Invalid CPI hash provided. CPI hash does not exist on the Corda cluster.")
            }
        } else {
            val existingHash = getExistingCpiHash()
            if (existingHash != null) {
                return@lazy existingHash
            }

            uploadCpi(cpi, "$cpiName.cpi")
        }
    }

    private fun getExistingCpiHash(hash: String? = null): String? {
        val restClient = RestClientUtils.createRestClient(
            CpiUploadRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val response = CpiUploader().getAllCpis(restClient = restClient, wait = waitDurationSeconds.seconds)
        return response.cpis
            .filter { it.cpiFileChecksum == hash || (hash == null && it.groupPolicy?.contains("CREATE_ID") ?: false) }
            .map { it.cpiFileChecksum }
            .firstOrNull()
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
