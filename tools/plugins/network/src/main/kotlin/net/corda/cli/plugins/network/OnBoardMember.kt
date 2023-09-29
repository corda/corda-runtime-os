package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.cli.plugins.packaging.CreateCpiV2
import net.corda.v5.base.util.EncodingUtils.toBase64
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.security.MessageDigest
import net.corda.cli.plugins.network.enums.MemberRole
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.membership.lib.MemberInfoExtension.Companion.CUSTOM_KEY_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEYS_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_SIGNATURE_SPEC

@Command(
    name = "onboard-member",
    description = [
        "Onboard a member"
    ],
    mixinStandardHelpOptions = true
)
class OnBoardMember : Runnable, BaseOnboard() {
    @Option(
        names = ["--cpb-file"],
        description = [
            "Location of a CPB file (Use either cpb-file or cpi-hash).",
            "The CPI will be signed with default options."
        ]
    )
    var cpbFile: File? = null

    @Option(
        names = ["--role", "-r"],
        description = ["Member role, if any. It is not mandatory to provide a role. Multiple roles can be specified"],
    )
    var roles: Set<MemberRole> = emptySet()

    @Option(
        names = ["--set", "-s"],
        description = ["Pass a custom key-value pair to the command to be included in the registration context. " +
                "Specified as <key>=<value>. Multiple --set arguments may be provided."],
    )
    var customProperties: Map<String, String> = emptyMap()

    @Option(
        names = ["--group-policy-file"],
        description = [
            "Location of a group policy file (default to ~/.corda/gp/groupPolicy.json).",
            "Relevant only if cpb-file is used"
        ]
    )
    var groupPolicyFile: File =
        File(File(File(File(System.getProperty("user.home")), ".corda"), "gp"), "groupPolicy.json")

    @Option(
        names = ["--cpi-hash"],
        description = ["The CPI hash (Use either cpb-file or cpi-hash)."]
    )
    var cpiHash: String? = null

    @Option(
        names = ["--pre-auth-token"],
        description = ["Pre-auth token to use for registration."]
    )
    var preAuthToken: String? = null

    @Option(
        names = ["--wait"],
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

    private fun checkIfCpiWasUploaded(cpiFileChecksum: String): Boolean {
        val currentCpis = createRestClient(CpiUploadRestResource::class)
            .use { client ->
                client.start().proxy.getAllCpis().cpis
            }

        return currentCpis.any { it.cpiFileChecksum == cpiFileChecksum }
    }

    private fun uploadCpi(cpiFile: File): String {
        val hash = listOf(cpiFile).hash()
        val cpiHashesFile = File(cpisRoot, "$hash.shortHash")

        if (cpiHashesFile.canRead()) {
            val cpiFileChecksum = cpiHashesFile.readText()
            if (checkIfCpiWasUploaded(cpiFileChecksum)) {
                println("CPI was uploaded and its hash checksum is $cpiFileChecksum")
                return cpiFileChecksum
            }
        }

        return uploadCpi(cpiFile.inputStream(), cpiFile.name).also {
            cpiHashesFile.writeText(it)
            println("CPI hash checksum is $it")
        }
    }

    private fun uploadCpb(cpbFile: File): String {
        val hash = listOf(cpbFile, groupPolicyFile).hash()
        val cpiRoot = File(cpisRoot, hash)
        val cpiFile = File(cpiRoot, "${cpbFile.name}.cpi")
        val cpiHashesFile = File(cpiRoot, "$hash.shortHash")
        if (cpiHashesFile.canRead()) {
            val cpiFileChecksum = cpiHashesFile.readText()
            val currentCpis = createRestClient(CpiUploadRestResource::class).use { client ->
                client.start().proxy.getAllCpis().cpis
            }
            if (currentCpis.any { it.cpiFileChecksum == cpiFileChecksum }) {
                println("CPI was already uploaded in $cpiFile. CPI hash checksum is $cpiFileChecksum")
                return cpiFileChecksum
            } else {
                println("CPI $cpiFileChecksum no longer exists. Will create the CPI file again")
                cpiHashesFile.delete()
            }
        }
        if (!cpiFile.canRead()) {
            createCpi(cpbFile, cpiFile)
            println("CPI file saved as ${cpiFile.absolutePath}")
        }
        uploadSigningCertificates()
        return uploadCpi(cpiFile).also {
            cpiHashesFile.writeText(it)
        }
    }

    private fun Collection<File>.hash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        this.forEach { file ->
            digest.update(file.readBytes())
        }
        return digest
            .digest()
            .let(::toBase64)
            .replace('/', '.')
            .replace('+', '-')
            .replace('=', '_')
    }

    private fun createCpi(cpbFile: File, cpiFile: File) {
        println(
            "Using the cpb file is not recommended." +
                    " It is advised to create CPI using the package create-cpi command."
        )
        cpiFile.parentFile.mkdirs()
        val creator = CreateCpiV2()
        creator.cpbFileName = cpbFile.absolutePath
        creator.groupPolicyFileName = groupPolicyFile.absolutePath
        creator.cpiName = cpbFile.name
        creator.cpiVersion = "1.0"
        creator.cpiUpgrade = false
        creator.outputFileName = cpiFile.absolutePath
        creator.signingOptions = createDefaultSingingOptions()
        creator.run()
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
            mapOf(
                NOTARY_SERVICE_NAME to notaryServiceName,
                NOTARY_SERVICE_PROTOCOL to "com.r3.corda.notary.plugin.nonvalidating",
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
        val ledgerKeys = mapOf(
            LEDGER_KEYS_ID.format(0) to ledgerKeyId,
            LEDGER_KEY_SIGNATURE_SPEC.format(0) to "SHA256withECDSA"
        )

        sessionKeys + ledgerKeys + endpoints + preAuth + roleProperty + notaryProperties + extProperties
    }

    override fun run() {
        verifyAndPrintError {
            println("On-boarding member $name")

            configureGateway()

            createTlsKeyIdNeeded()

            if (mtls) {
                println(
                    "Using $certificateSubject as client certificate. " +
                            "The onboarding will fail until the the subject is added to the MGM's allow list. " +
                            "You can do that using the allowClientCertificate command."
                )
            }

            setupNetwork()

            println("Provided registration context: ")
            println(registrationContext)

            register(waitForFinalStatus)

            if (waitForFinalStatus) {
                println("Member $name was onboarded.")
            } else {
                println(
                    "Registration request has been submitted. Wait for MGM approval to finalize registration. " +
                            "MGM may need to approve your request manually."
                )
            }
        }
    }
}
