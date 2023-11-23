package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.network.enums.MemberRole
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.cli.plugins.network.utils.inferCpiName
import net.corda.cli.plugins.packaging.CreateCpiV2
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.membership.lib.MemberInfoExtension.Companion.CUSTOM_KEY_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEYS_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_BACKCHAIN_REQUIRED
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.v5.base.exceptions.CordaRuntimeException
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

@Command(
    name = "onboard-member",
    description = [
        "Onboard a member"
    ],
    mixinStandardHelpOptions = true
)
class OnboardMember : Runnable, BaseOnboard() {
    private companion object {
        const val CPI_VERSION = "1.0"
    }

    @Option(
        names = ["--cpb-file", "-b"],
        description = [
            "Location of a CPB file. The plugin will generate a CPI signed with default options when a CPB is " +
                    "provided. Use either --cpb-file or --cpi-hash.",
        ]
    )
    var cpbFile: File? = null

    @Option(
        names = ["--role", "-r"],
        description = ["Member role, if any. Multiple roles may be specified"],
    )
    var roles: Set<MemberRole> = emptySet()

    @Option(
        names = ["--set", "-s"],
        description = ["Pass a custom key-value pair to the command to be included in the registration context. " +
                "Specified as <key>=<value>. Multiple --set arguments may be provided."],
    )
    var customProperties: Map<String, String> = emptyMap()

    @Option(
        names = ["--group-policy-file", "-gp"],
        description = [
            "Location of a group policy file (default to ~/.corda/gp/groupPolicy.json).",
            "Relevant only if cpb-file is used"
        ]
    )
    var groupPolicyFile: File =
        File(File(File(File(System.getProperty("user.home")), ".corda"), "gp"), "groupPolicy.json")

    @Option(
        names = ["--cpi-hash", "-h"],
        description = ["The CPI hash of a previously uploaded CPI (use either --cpb-file or --cpi-hash)."]
    )
    var cpiHash: String? = null

    @Option(
        names = ["--pre-auth-token", "-a"],
        description = ["Pre-auth token to use for registration."]
    )
    var preAuthToken: String? = null

    @Option(
        names = ["--wait", "-w"],
        description = ["Wait until member gets approved/declined. False, by default."]
    )
    var waitForFinalStatus: Boolean = false

    override val cpiFileChecksum by lazy {
        if (cpiHash != null) {
            return@lazy cpiHash!!
        }
        if (cpbFile?.canRead() != true) {
            throw OnboardException("Please set either CPB file or CPI hash")
        } else {
            uploadCpb(cpbFile!!)
        }
    }

    private val cpisRoot by lazy {
        File(
            File(
                File(
                    System.getProperty(
                        "user.home"
                    )
                ),
                ".corda"
            ),
            "cached-cpis"
        )
    }

    private fun uploadCpb(cpbFile: File): String {
        val cpiName = inferCpiName(cpbFile, groupPolicyFile)
        val cpiFile = File(cpisRoot, "$cpiName.cpi")
        println("Creating and uploading CPI using CPB '${cpbFile.name}'")
        val cpisFromCluster = createRestClient(CpiUploadRestResource::class).use { client ->
            client.start().proxy.getAllCpis().cpis
        }
        cpisFromCluster.firstOrNull { it.id.cpiName == cpiName && it.id.cpiVersion == CPI_VERSION }?.let {
            println("CPI already exists, using CPI ${it.id}")
            return it.cpiFileChecksum
        }
        if (!cpiFile.exists()) {
            val exitCode = createCpi(cpbFile, cpiFile)
            if (exitCode != 0) {
                throw CordaRuntimeException("Create CPI returned non-zero exit code")
            }
            println("CPI file saved as ${cpiFile.absolutePath}")
        }
        uploadSigningCertificates()
        return uploadCpi(cpiFile.inputStream(), cpiFile.name)
    }

    private fun createCpi(cpbFile: File, cpiFile: File): Int {
        println(
            "Using the cpb file is not recommended." +
                    " It is advised to create CPI using the package create-cpi command."
        )
        cpiFile.parentFile.mkdirs()
        val creator = CreateCpiV2()
        creator.cpbFileName = cpbFile.absolutePath
        creator.groupPolicyFileName = groupPolicyFile.absolutePath
        creator.cpiName = cpiFile.nameWithoutExtension
        creator.cpiVersion = CPI_VERSION
        creator.cpiUpgrade = false
        creator.outputFileName = cpiFile.absolutePath
        creator.signingOptions = createDefaultSingingOptions()
        return creator.call()
    }

    private val ledgerKeyId by lazy {
        assignSoftHsmAndGenerateKey("LEDGER")
    }

    private val notaryKeyId by lazy {
        assignSoftHsmAndGenerateKey("NOTARY")
    }

    override val registrationContext by lazy {
        val preAuth = preAuthToken?.let { mapOf("corda.auth.token" to it) } ?: emptyMap()
        val roleProperty: Map<String, String> = roles.mapIndexed { index: Int, memberRole: MemberRole ->
            "$ROLES_PREFIX.$index" to memberRole.value
        }.toMap()

        val extProperties = customProperties.filterKeys { it.startsWith("$CUSTOM_KEY_PREFIX.") }

        val notaryProperties = if (roles.contains(MemberRole.NOTARY)) {
            val notaryServiceName = customProperties[NOTARY_SERVICE_NAME] ?:
                throw IllegalArgumentException("When specifying a NOTARY role, " +
                        "you also need to specify a custom property for its name under $NOTARY_SERVICE_NAME.")
            val backchainRequired = customProperties[NOTARY_SERVICE_BACKCHAIN_REQUIRED] ?: true
            val notaryProtocol = customProperties[NOTARY_SERVICE_PROTOCOL] ?: "com.r3.corda.notary.plugin.nonvalidating"
            mapOf(
                NOTARY_SERVICE_NAME to notaryServiceName,
                NOTARY_SERVICE_BACKCHAIN_REQUIRED to "$backchainRequired",
                NOTARY_SERVICE_PROTOCOL to notaryProtocol,
                NOTARY_SERVICE_PROTOCOL_VERSIONS.format("0") to "1",
                NOTARY_KEYS_ID.format("0") to notaryKeyId,
                NOTARY_KEY_SPEC.format("0") to "SHA256withECDSA"
            )
        } else {
            emptyMap()
        }

        val endpoints: Map<String, String> = p2pGatewayUrls
            .flatMapIndexed { index, url ->
                listOf(
                    URL_KEY.format(index) to url,
                    PROTOCOL_VERSION.format(index) to "1"
                )
            }
            .toMap()

        val sessionKeys = mapOf(
            PARTY_SESSION_KEYS_ID.format(0) to sessionKeyId,
            SESSION_KEYS_SIGNATURE_SPEC.format(0) to "SHA256withECDSA"
        )
        val ledgerKeys = if (roles.contains(MemberRole.NOTARY)) {
            emptyMap()
        } else {
            mapOf(
                LEDGER_KEYS_ID.format(0) to ledgerKeyId,
                LEDGER_KEY_SIGNATURE_SPEC.format(0) to "SHA256withECDSA"
            )
        }

        sessionKeys + ledgerKeys + endpoints + preAuth + roleProperty + notaryProperties + extProperties
    }

    override fun run() {
        verifyAndPrintError {
            println("Onboarding member '$name'.")

            configureGateway()

            createTlsKeyIdNeeded()

            if (mtls) {
                println(
                    "Using '$certificateSubject' as client certificate. " +
                            "Onboarding will fail until the the subject is added to the MGM's allowed list. " +
                            "See command: 'allowClientCertificate'."
                )
            }

            setupNetwork()

            println("Provided registration context: ")
            println(registrationContext)

            register(waitForFinalStatus)

            if (waitForFinalStatus) {
                println("Member '$name' was onboarded.")
            } else {
                println(
                    "Registration request has been submitted. Wait for MGM approval for registration to be finalised." +
                            " MGM may need to approve your request manually."
                )
            }
        }
    }
}
