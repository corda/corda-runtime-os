package net.corda.cli.plugins.mgm

import kong.unirest.Unirest
import kong.unirest.json.JSONArray
import kong.unirest.json.JSONObject
import net.corda.v5.base.util.toBase64
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

@Command(
    name = "member",
    description = [
        "Onboard a member",
        "This sub command should only be used in for internal development"
    ]
)
class OnBoardMember : Runnable, BaseOnboard() {
    @Option(
        names = ["--cpi-file"],
        description = [
            "Location of a CPI file (Use either cpi-file, cpb-file or cpi-hash).",
            "To create the CPI use the MGM created group policy file and sign it using the package create-cpi command."
        ]
    )
    var cpiFile: File? = null

    @Option(
        names = ["--cpb-file"],
        description = [
            "Location of a CPI file (Use either cpi-file, cpb-file or cpi-hash).",
            "To create the CPI use the MGM created group policy file and sign it using the package create-cpi command."
        ]
    )
    var cpbFile: File? = null

    @Option(
        names = ["--group-policy-file"],
        description = [
            "Location of a group policy file (default to ~/.corda/gp/groupPolicy.json).",
            "Relevant only if cpb-file is used"
        ]
    )
    var groupPolicyFile: File = File(File(File(File(System.getProperty("user.home")), ".corda"), "gp"), "groupPolicy.json")

    @Option(
        names = ["--cpi-hash"],
        description = ["The CPI hash (Use either cpi-file, cpb-file or cpi-hash)."]
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
        if (cpiFile != null) {
            return@lazy uploadCpi(cpiFile!!)
        }
        if (cpbFile?.canRead() != true) {
            throw OnboardException("Please set either CPB file, CPI file or CPI hash")
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
        val currentCpis = Unirest.get("/cpi")
            .asJson()
            .bodyOrThrow()
            .`object`
            .get("cpis") as JSONArray
        return currentCpis.filterIsInstance<JSONObject>()
            .mapNotNull {
                it.get("cpiFileChecksum") as? String
            }.any {
                it == cpiFileChecksum
            }
    }

    private fun uploadCpi(cpiFile: File): String {
        val hash = listOf(cpiFile).hash()
        val cpiHashesFile = File(cpisRoot, "$hash.shortHash")
        if (cpiHashesFile.canRead()) {
            val cpiFileChecksum = cpiHashesFile.readText()
            if (checkIfCpiWasUploaded(cpiFileChecksum)) {
                println("CPI was uploaded and it's hash checksum is $cpiFileChecksum")
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
        val baseNetworkName = if (cordaClusterName == null) {
            "combined-worker"
        } else {
            cordaClusterName
        }
        val cpiHashesFile = File(cpiRoot, "$baseNetworkName.shortHash")
        if (cpiHashesFile.canRead()) {
            val cpiFileChecksum = cpiHashesFile.readText()
            val currentCpis = Unirest.get("/cpi")
                .asJson()
                .bodyOrThrow()
                .`object`
                .get("cpis") as JSONArray
            val exists = currentCpis.filterIsInstance<JSONObject>()
                .mapNotNull {
                    it.get("cpiFileChecksum") as? String
                }.any {
                    it == cpiFileChecksum
                }
            if (exists) {
                println("CPI was already uploaded in $cpiFile. CPI hash checksum is $cpiFileChecksum")
                return cpiFileChecksum
            } else {
                println("CPI $cpiFileChecksum no longer exists. Will create the CPI file again")
                cpiHashesFile.delete()
            }
        }
        if (!cpiFile.canRead()) {
            val cpi = createCpi(cpbFile)
            cpiFile.parentFile.mkdirs()
            cpiFile.writeBytes(cpi)
            println("CPI file saved as ${cpiFile.absolutePath}")
        }
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
            .toBase64()
            .replace('/', '.')
            .replace('+', '-')
            .replace('=', '_')
    }

    private fun copyJar(source: JarInputStream, target: JarOutputStream) {
        while (true) {
            val entry = source.nextJarEntry ?: return
            target.putNextEntry(entry)
            source.copyTo(target)
            target.closeEntry()
        }
    }
    private fun createCpi(cpbFile: File): ByteArray {
        return JarInputStream(cpbFile.inputStream()).use { cpbJar ->
            ByteArrayOutputStream().use { cpiByteStream ->
                JarOutputStream(cpiByteStream, cpbJar.manifest).use { cpiJar ->
                    cpiJar.putNextEntry(ZipEntry("GroupPolicy.json"))
                    cpiJar.write(groupPolicyFile.readBytes())
                    copyJar(cpbJar, cpiJar)
                }
                cpiByteStream.toByteArray()
            }
        }
    }

    private val ledgerKeyId by lazy {
        assignSoftHsmAndGenerateKey("LEDGER")
    }

    override val registrationContext by lazy {
        mapOf(
            "corda.session.key.id" to sessionKeyId,
            "corda.session.key.signature.spec" to "SHA256withECDSA",
            "corda.ledger.keys.0.id" to ledgerKeyId,
            "corda.ledger.keys.0.signature.spec" to "SHA256withECDSA",
            "corda.endpoints.0.connectionURL" to p2pUrl,
            "corda.endpoints.0.protocolVersion" to "1"
        ) + if (preAuthToken != null) mapOf("corda.auth.token" to preAuthToken) else emptyMap()
    }
    override fun run() {
        println("This sub command should only be used in for internal development")
        println("On-boarding member $x500Name")

        setupClient()

        createTlsKeyIdNeeded()

        setupNetwork()

        disableClrChecks()
        println("Provided registration context: ")
        println(registrationContext)

        register(waitForFinalStatus)

        if (waitForFinalStatus) {
            println("Member $x500Name was onboarded.")
        } else {
            println("Registration request has been submitted. Wait for MGM approval to finalize registration. " +
                    "MGM may need to approve your request manually.")
        }
    }
}
