package net.corda.cli.plugins.mgm

import kong.unirest.Unirest
import kong.unirest.json.JSONArray
import kong.unirest.json.JSONObject
import net.corda.cli.plugins.packaging.CreateCpiV2
import net.corda.crypto.test.certificates.generation.toPem
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Command(
    name = "mgm",
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
    var groupPolicyFile: File = File(File(File(File(System.getProperty("user.home")), ".corda"), "gp"), "groupPolicy.json")

    @Option(
        names = ["--cpi-file"],
        description = [
            "Location of the MGM CPI file.",
            "To create the CPI use the package create-cpi command.",
            "Leave empty to auto generate CPI file (not recommended)."
        ]
    )
    var cpiFile: File? = null

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
        repeat(10) {
            try {
                val response = Unirest.get("/mgm/$holdingId/info").asString()
                groupPolicyFile.parentFile.mkdirs()
                json.writerWithDefaultPrettyPrinter()
                    .writeValue(
                        groupPolicyFile,
                        json.readTree(response.bodyOrThrow())
                    )
                println("Group policy file created at $groupPolicyFile")
                return@saveGroupPolicy
            } catch (e: Exception) {
                Thread.sleep(300)
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
            "corda.session.key.id" to sessionKeyId,
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
            "corda.group.truststore.tls.0" to tlsTrustRoot,
        )
    }

    private val cpi by lazy {
        val parametersCpiFile = cpiFile
        if(parametersCpiFile != null) {
            return@lazy parametersCpiFile
        }
        val mgmGroupPolicyFile = File.createTempFile("mgm.groupPolicy.", ".json").also {
            it.deleteOnExit()
            it.writeBytes(groupPolicy)
        }
        val cpiFile = File.createTempFile("mgm.", ".cpi").also {
            it.deleteOnExit()
            it.delete()
        }
        println("Using the cpi file is recommended." +
                " It is advised to create CPI using the package create-cpi command.")
        cpiFile.parentFile.mkdirs()
        val creator = CreateCpiV2()
        creator.groupPolicyFileName = mgmGroupPolicyFile.absolutePath
        creator.cpiName = cpbName
        creator.cpiVersion = "1.0"
        creator.cpiUpgrade = false
        creator.outputFileName = cpiFile.absolutePath
        creator.signingOptions = createDefaultSingingOptions()
        creator.run()
        uploadSigningCertificates()
        cpiFile
    }

    override val cpiFileChecksum by lazy {
        val existingHash = Unirest.get("/cpi")
            .asJson()
            .bodyOrThrow()
            .let {
                val cpis = it.`object`.get("cpis") as JSONArray
                cpis
                    .filterIsInstance<JSONObject>()
                    .firstOrNull { cpi ->
                        cpi.get("groupPolicy").toString().contains("CREATE_ID")
                    }?.get("cpiFileChecksum")
            }
        if (existingHash is String) return@lazy existingHash

        uploadCpi(cpi.inputStream(), cpbName)
    }
    override fun run() {
        println("This sub command should only be used in for internal development")
        println("On-boarding MGM member $x500Name")

        setupClient()

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
