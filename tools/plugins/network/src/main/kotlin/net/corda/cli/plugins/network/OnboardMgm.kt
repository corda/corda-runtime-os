package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.cli.plugins.packaging.CreateCpiV2
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.sdk.network.ExportGroupPolicyFromMgm
import net.corda.sdk.rest.RestClientUtils
import net.corda.v5.base.exceptions.CordaRuntimeException
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Command(
    name = "onboard-mgm",
    description = [
        "Onboard MGM",
    ],
    mixinStandardHelpOptions = true,
)
class OnboardMgm : Runnable, BaseOnboard() {
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

    private val groupPolicy by lazy {
        mapOf(
            "fileFormatVersion" to 1,
            "groupId" to "CREATE_ID",
            "registrationProtocol" to "net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
            "synchronisationProtocol" to "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl",
        ).let { groupPolicyMap ->
            ByteArrayOutputStream().use { outputStream ->
                json.writeValue(outputStream, groupPolicyMap)
                outputStream.toByteArray()
            }
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
        val groupPolicyResponse = ExportGroupPolicyFromMgm().exportPolicy(restClient, holdingId)
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

    override val registrationContext by lazy {
        val tlsType = if (mtls) {
            "Mutual"
        } else {
            "OneWay"
        }

        val endpoints = mutableMapOf<String, String>()

        p2pGatewayUrls.mapIndexed { index, url ->
            endpoints["corda.endpoints.$index.connectionURL"] = url
            endpoints["corda.endpoints.$index.protocolVersion"] = "1"
        }

        mapOf(
            "corda.session.keys.0.id" to sessionKeyId,
            "corda.ecdh.key.id" to ecdhKeyId,
            "corda.group.protocol.registration"
                to "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
            "corda.group.protocol.synchronisation"
                to "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
            "corda.group.protocol.p2p.mode" to "Authenticated_Encryption",
            "corda.group.key.session.policy" to "Distinct",
            "corda.group.tls.type" to tlsType,
            "corda.group.pki.session" to "NoPKI",
            "corda.group.pki.tls" to "Standard",
            "corda.group.tls.version" to "1.3",
            "corda.group.trustroot.tls.0" to tlsTrustRoot,
        ) + endpoints
    }

    private val cpi by lazy {
        val mgmGroupPolicyFile = File.createTempFile("mgm.groupPolicy.", ".json").also {
            it.deleteOnExit()
            it.writeBytes(groupPolicy)
        }
        val cpiFile = File.createTempFile("mgm.", ".cpi").also {
            it.deleteOnExit()
            it.delete()
        }
        cpiFile.parentFile.mkdirs()
        val creator = CreateCpiV2()
        creator.groupPolicyFileName = mgmGroupPolicyFile.absolutePath
        creator.cpiName = cpiName
        creator.cpiVersion = "1.0"
        creator.cpiUpgrade = false
        creator.outputFileName = cpiFile.absolutePath
        creator.signingOptions = createDefaultSingingOptions()
        val exitCode = creator.call()
        if (exitCode != 0) {
            throw CordaRuntimeException("Create CPI returned non-zero exit code")
        }
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

            uploadCpi(cpi.inputStream(), cpiName)
        }
    }

    private fun getExistingCpiHash(hash: String? = null): String? {
        return createRestClient(CpiUploadRestResource::class).use { client ->
            val response = client.start().proxy.getAllCpis()
            response.cpis
                .filter { it.cpiFileChecksum == hash || (hash == null && it.groupPolicy?.contains("CREATE_ID") ?: false) }
                .map { it.cpiFileChecksum }
                .firstOrNull()
        }
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
