package net.corda.cli.plugins.network

import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.cli.plugins.packaging.CreateCpiV2
import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.network.utils.InvariantUtils.checkInvariant
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.membership.rest.v1.MGMRestResource
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Command(
    name = "onboard-mgm",
    description = [
        "Onboard MGM member.",
        "This sub command should only be used in for internal development",
    ]
)
class OnboardMgm : Runnable, BaseOnboard() {
    @Option(
        names = ["--cpb-name"],
        description = ["The name of the CPB. Default to random UUID"]
    )
    var cpbName: String = UUID.randomUUID().toString()

    @Option(
        names = ["--x500-name"],
        description = ["The X500 name of the MGM. Default to a random name"]
    )
    override var x500Name: String = "O=Mgm, L=London, C=GB, OU=${UUID.randomUUID()}"

    @Option(
        names = ["--save-group-policy-as", "-s"],
        description = ["Location to save the group policy file (default to ~/.corda/gp/groupPolicy.json)"]
    )
    var groupPolicyFile: File =
        File(File(File(File(System.getProperty("user.home")), ".corda"), "gp"), "groupPolicy.json")

    @Option(
        names = ["--cpi-file"],
        description = [
            "Location of the MGM CPI file.",
            "To create the CPI use the package create-cpi command.",
            "Leave empty to auto generate CPI file (not recommended)."
        ]
    )
    var cpiFile: File? = null

    private var groupIdFile: File = File("groupId.txt")

    private val groupPolicy by lazy {
        mapOf(
            "fileFormatVersion" to 1,
            "groupId" to "CREATE_ID",
            "registrationProtocol" to "net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
            "synchronisationProtocol" to "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl"
        ).let { groupPolicyMap ->
            ByteArrayOutputStream().use { outputStream ->
                json.writeValue(outputStream, groupPolicyMap)
                outputStream.toByteArray()
            }
        }
    }

    private fun saveGroupPolicy() {
        createRestClient(MGMRestResource::class).use { client ->
            checkInvariant(
                maxAttempts = MAX_ATTEMPTS,
                waitInterval = WAIT_INTERVAL,
                errorMessage = "Save group policy: Invariant check failed after maximum attempts."
            ) {
                val resource = client.start().proxy
                val response = resource.generateGroupPolicy(holdingId)
                groupPolicyFile.parentFile.mkdirs()
                json.writerWithDefaultPrettyPrinter()
                    .writeValue(
                        groupPolicyFile,
                        json.readTree(response)
                    )
                println("Group policy file created at $groupPolicyFile")
                // extract the groupId from the response
                val groupId = json.readTree(response).get("groupId").asText()

                // write the groupId to the file
                groupIdFile.writeText(groupId)
                true // Return true to indicate the invariant is satisfied
            }
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
            "corda.endpoints.0.connectionURL" to p2pUrl,
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.group.trustroot.tls.0" to tlsTrustRoot,
        )
    }

    private val cpi by lazy {
        if (cpiFile != null) {
            return@lazy cpiFile!!
        }
        val mgmGroupPolicyFile = File.createTempFile("mgm.groupPolicy.", ".json").also {
            it.deleteOnExit()
            it.writeBytes(groupPolicy)
        }
        val cpiFile = File.createTempFile("mgm.", ".cpi").also {
            it.deleteOnExit()
            it.delete()
        }
        println(
            "Using the cpi file is recommended." +
                    " It is advised to create CPI using the package create-cpi command."
        )
        cpiFile.parentFile.mkdirs()
        val creator = CreateCpiV2()
        creator.groupPolicyFileName = mgmGroupPolicyFile.absolutePath
        creator.cpiName = x500Name
        creator.cpiVersion = "1.0"
        creator.cpiUpgrade = false
        creator.outputFileName = cpiFile.absolutePath
        creator.signingOptions = createDefaultSingingOptions()
        creator.run()
        uploadSigningCertificates()
        cpiFile
    }

    override val cpiFileChecksum: String by lazy {
        val existingHash = createRestClient(CpiUploadRestResource::class).use { client ->
            val response = client.start().proxy.getAllCpis()
            response.cpis
                .filter { it.groupPolicy?.contains("CREATE_ID") ?: false }
                .map { it.cpiFileChecksum }
                .firstOrNull()
        }
        if (existingHash != null) {
            return@lazy existingHash
        }

        uploadCpi(cpi.inputStream(), cpbName)
    }

    override fun run() {
        println("This sub command should only be used in for internal development")
        println("On-boarding MGM member $x500Name")

        configureGateway()

        createTlsKeyIdNeeded()

        register()

        setupNetwork()

        println("MGM Member $x500Name was onboarded")

        saveGroupPolicy()

        if (mtls) {
            println(
                "To onboard members to this group on other clusters, please add those members' " +
                        "client certificates subjects to this MGM's allow list. You can do that using the allowClientCertificate command."
            )
        }
    }
}
