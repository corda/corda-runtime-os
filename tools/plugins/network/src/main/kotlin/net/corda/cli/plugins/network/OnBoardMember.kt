package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.cli.plugins.packaging.CreateCpiV2
import net.corda.v5.base.util.EncodingUtils.toBase64
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import net.corda.cli.plugins.network.enums.MemberRole

@Command(
    name = "onboard-member",
    description = [
        "Onboard a member",
        "This sub command should only be used in for internal development"
    ]
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
        description = ["Member role, if any. It is not mandatory to provide a role"],
    )
    var role: MemberRole? = null

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
        names = ["--x500-name", "-x"],
        description = ["The X500 name of the member. Default to a random member name"]
    )
    override var x500Name: String = "O=${UUID.randomUUID()}, L=London, C=GB"

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
        if (customProperties.size > 100) {
            throw IllegalArgumentException("Cannot specify more than 100 key-value pairs")
        }
        val notaryServiceName = customProperties["corda.notary.service.name"]
        customProperties = customProperties.filterKeys { it != "corda.notary.service.name" }
        val extProperties = customProperties.filter {
            val key = it.key
            val value = it.value
            val keyAndValueNotEmpty = (key.isNotEmpty()).and(value.isNotEmpty())
            val keyStartsWithExt = key.startsWith("ext.")
            val keyDoesNotEndWithExt = !key.endsWith("ext.")
            val allChecks = keyAndValueNotEmpty.and(keyStartsWithExt).and(keyDoesNotEndWithExt)

            if (!keyAndValueNotEmpty) {
                throw IllegalArgumentException("Please specify a key or value, either cannot be empty")
            }
            if (!keyStartsWithExt) {
                throw IllegalArgumentException("The key: $key has to start with `ext.` prefix")
            }
            if (!keyDoesNotEndWithExt) {
                throw IllegalArgumentException("The key: $key cannot end with `ext.`")
            }
            if (key.length > 128) {
                throw IllegalArgumentException("The key length cannot exceed 128 characters")
            }
            if (value.length > 800) {
                throw IllegalArgumentException("The value length cannot exceed 800 characters")
            }

            allChecks
        }
        val roleProperty = if (role != null) {
                if (notaryServiceName != null) {
                    mapOf("corda.roles.0" to role!!.value)
                } else {
                    throw IllegalArgumentException("Cannot specify 'corda.notary.service.name'")
                }
            } else {
                emptyMap()
            }

        val notaryProperties = if (role != null) {
                mapOf(
                    "corda.notary.service.name" to notaryServiceName,
                    "corda.notary.service.flow.protocol.name" to "com.r3.corda.notary.plugin.nonvalidating",
                    "corda.notary.service.flow.protocol.version.0" to "1",
                    "corda.notary.keys.0.id" to notaryKeyId,
                    "corda.notary.keys.0.signature.spec" to "SHA256withECDSA"
                )
            } else {
            emptyMap()
            }

        val endpoints: Map<String, String> = p2pGatewayUrls
            .flatMapIndexed { index, url ->
                    listOf(
                        "corda.endpoints.$index.connectionURL" to url,
                        "corda.endpoints.$index.protocolVersion" to "1"
                    )
            }
            .toMap()


        val sessionKeys = mapOf(
            "corda.session.keys.0.id" to sessionKeyId,
            "corda.session.keys.0.signature.spec" to "SHA256withECDSA"
        )
        val ledgerKeys = mapOf(
            "corda.ledger.keys.0.id" to ledgerKeyId,
            "corda.ledger.keys.0.signature.spec" to "SHA256withECDSA"
        )

        sessionKeys + ledgerKeys + endpoints + preAuth + roleProperty + notaryProperties + extProperties
    }

    override fun run() {
        println("This sub command should only be used in for internal development")
        println("On-boarding member $x500Name")

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
            println("Member $x500Name was onboarded.")
        } else {
            println(
                "Registration request has been submitted. Wait for MGM approval to finalize registration. " +
                        "MGM may need to approve your request manually."
            )
        }
    }
}
