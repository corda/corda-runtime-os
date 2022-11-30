package net.corda.cli.plugins.mgm

import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.HttpResponse
import kong.unirest.Unirest
import kong.unirest.json.JSONObject
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.toFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.v5.cipher.suite.schemes.RSA_TEMPLATE
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import kotlin.concurrent.thread

abstract class BaseOnboard : Runnable {
    private companion object {
        const val P2P_TLS_KEY_ALIAS = "p2p-tls-key"
        const val P2P_TLS_CERTIFICATE_ALIAS = "p2p-tls-cert"
    }

    @Parameters(
        description = ["The name of the k8s namespace (leave empty for combined worker on local host)"],
        paramLabel = "NAME",
        arity = "0..1",
    )
    var cordaClusterName: String? = null

    @Option(
        names = ["--ca"],
        description = ["The CA location (default to ~/.corda/ca)"]
    )
    var caHome: File = File(File(File(System.getProperty("user.home")), ".corda"), "ca")

    @Option(
        names = ["--rpc-worker-deployment-name"],
        description = ["The RPC worker deployment name (default to corda-rpc-worker)"]
    )
    var rpcWorkerDeploymentName: String = "corda-rpc-worker"

    protected val json by lazy {
        ObjectMapper()
    }

    private val rpcPassword by lazy {
        if (cordaClusterName != null) {
            val getSecret = ProcessBuilder().command(
                "kubectl",
                "get",
                "secret",
                "corda-initial-admin-user",
                "--namespace",
                cordaClusterName,
                "-o",
                "go-template={{ .data.password | base64decode }}"
            ).start()
            if (getSecret.waitFor() != 0) {
                throw OnboardException("Can not get admin password. ${getSecret.errorStream.reader().readText()}")
            }
            getSecret.inputStream.reader().readText()
        } else {
            "admin"
        }
    }

    private val url by lazy {
        val rpcPort = if (cordaClusterName != null) {
            val port = ServerSocket(0).use {
                it.localPort
            }
            ProcessBuilder().command(
                "kubectl",
                "port-forward",
                "--namespace",
                cordaClusterName,
                "deployment/$rpcWorkerDeploymentName",
                "$port:8888"
            )
                .inheritIO()
                .start().also { process ->
                    Runtime.getRuntime().addShutdownHook(
                        thread(false) {
                            process.destroy()
                        }
                    )
                }
            Thread.sleep(2000)
            port
        } else {
            8888
        }

        "https://localhost:$rpcPort/api/v1"
    }

    protected fun setupClient() {
        Unirest.config()
            .verifySsl(false)
            .setDefaultBasicAuth("admin", rpcPassword)
            .defaultBaseUrl(url)
    }

    internal class OnboardException(message: String) : Exception(message)

    internal fun <T> HttpResponse<T>.bodyOrThrow(): T {
        if (!this.isSuccess) {
            throw OnboardException("Error: ${this.body}")
        }
        return this.body
    }
    protected fun uploadCpi(cpi: InputStream, name: String): String {
        val id = cpi.use { jarInputStream ->

            Unirest.post("/cpi")
                .field("upload", jarInputStream, "$name.cpi")
                .asJson()
                .bodyOrThrow()
                .`object`.get("id")
        }
        val end = System.currentTimeMillis() + 5 * 60 * 1000
        while (System.currentTimeMillis() < end) {
            val response = Unirest.get("/cpi/status/$id").asJson()
            val status = try {
                response.bodyOrThrow().`object`.get("status")
            } catch (e: OnboardException) {
                "Not ready yet"
            }
            if (status == "OK") {
                return response.body.`object`.get("cpiFileChecksum").toString()
            }
        }

        throw OnboardException("CPI request $id had failed!")
    }

    protected abstract val cpiFileChecksum: String

    abstract var x500Name: String

    protected abstract val registrationContext: Map<String, Any?>

    protected val holdingId by lazy {
        val body = mapOf(
            "request" to mapOf(
                "cpiFileChecksum" to cpiFileChecksum,
                "x500Name" to x500Name
            )
        ).let {
            json.writeValueAsString(it)
        }
        Unirest.post("/virtualnode")
            .body(body)
            .asJson()
            .bodyOrThrow()
            .let {
                it.`object`.let { reply ->
                    if (reply.get("state") != "ACTIVE") {
                        throw OnboardException("Virtual node is not active")
                    }
                    (reply.get("holdingIdentity") as JSONObject).let { holdingIdentity ->
                        println("Group ID is: ${holdingIdentity.get("groupId")}")
                        holdingIdentity.get("shortHash").toString()
                    }
                }
            }
    }

    protected fun assignSoftHsmAndGenerateKey(category: String): String {
        Unirest.post("/hsm/soft/$holdingId/$category").asJson().bodyOrThrow()
        val response = Unirest
            .post("/keys/$holdingId/alias/$holdingId-$category/category/$category/scheme/CORDA.ECDSA.SECP256R1")
            .asJson()
        return response.bodyOrThrow().`object`.get("id").toString()
    }

    protected val sessionKeyId by lazy {
        assignSoftHsmAndGenerateKey("SESSION_INIT")
    }
    protected val ecdhKeyId by lazy {
        assignSoftHsmAndGenerateKey("PRE_AUTH")
    }
    private val p2pHost by lazy {
        if (cordaClusterName == null) {
            "localhost"
        } else {
            "corda-p2p-gateway-worker.$cordaClusterName"
        }
    }

    protected val p2pUrl by lazy {
        "https://$p2pHost:8080"
    }
    protected val ca by lazy {
        caHome.parentFile.mkdirs()
        CertificateAuthorityFactory
            .createFileSystemLocalAuthority(
                RSA_TEMPLATE.toFactoryDefinitions(),
                caHome
            ).also { it.save() }
    }

    protected fun createTlsKeyIdNeeded() {
        val keys = Unirest.get("/keys/p2p?category=TLS")
            .asJson()
        if (!keys.bodyOrThrow().`object`.isEmpty) {
            return
        }
        val generateKeyPairResponse = Unirest.post("/keys/p2p/alias/$P2P_TLS_KEY_ALIAS/category/TLS/scheme/CORDA.ECDSA.SECP256R1").asJson()
        val tlsKeyId = generateKeyPairResponse
            .bodyOrThrow()
            .`object`.get("id").toString()

        val generateCsrResponse = Unirest.post("/certificates/p2p/$tlsKeyId/")
            .body(
                mapOf(
                    "x500Name" to x500Name,
                    "subjectAlternativeNames" to listOf(p2pHost)
                )
            ).asString()
        val csr = generateCsrResponse.bodyOrThrow().reader().use { reader ->
            PEMParser(reader).use { parser ->
                parser.readObject()
            }
        } as? PKCS10CertificationRequest ?: throw OnboardException("CSR is not a valid CSR: ${generateCsrResponse.body}")
        ca.signCsr(csr).toPem().byteInputStream().use { certificate ->
            Unirest.put("/certificates/cluster/p2p-tls")
                .field("certificate", certificate, "certificate.pem")
                .field("alias", P2P_TLS_CERTIFICATE_ALIAS)
                .asJson()
                .bodyOrThrow()
        }
    }

    protected fun setupNetwork() {
        Unirest.put("/network/setup/$holdingId")
            .body(
                mapOf(
                    "request" to mapOf(
                        "p2pTlsCertificateChainAlias" to P2P_TLS_CERTIFICATE_ALIAS,
                        "useClusterLevelTlsCertificateAndKey" to true,
                        "sessionKeyTenantId" to null,
                        "sessionKeyId" to sessionKeyId
                    )
                )
            ).asJson()
            .bodyOrThrow()
    }

    protected fun register() {
        val response = Unirest.post("/membership/$holdingId")
            .body(
                mapOf(
                    "memberRegistrationRequest" to mapOf(
                        "action" to "requestJoin",
                        "context" to registrationContext
                    )
                )
            ).asJson()

        val body = response.bodyOrThrow().`object`
        val submissionStatus = body.get("registrationStatus")
        if (submissionStatus != "SUBMITTED") {
            throw OnboardException("Could not submit MGM registration: ${response.body}")
        }
        val id = body.get("registrationId")
        println("Registration ID of $x500Name is $id")
        val end = System.currentTimeMillis() + 5 * 60 * 1000
        while (System.currentTimeMillis() < end) {
            val status = Unirest.get("/membership/$holdingId/$id").asJson()
            val registrationStatus = status.bodyOrThrow().`object`.get("registrationStatus")
            if (registrationStatus == "APPROVED") {
                return
            } else {
                println("Status of $x500Name registration is $registrationStatus")
                Thread.sleep(400)
            }
        }
        throw OnboardException("Registration had failed!")
    }

    protected fun disableClrChecks() {
        val currentConfig = Unirest.get("/config/corda.p2p.gateway/")
            .asJson()
            .bodyOrThrow()
        val rawConfig = json.readTree(currentConfig.`object`.get("configWithDefaults") as String)
        val mode = rawConfig.get("sslConfig").get("revocationCheck").get("mode").asText()
        if (mode != "OFF") {
            val newConfig = mapOf(
                "sslConfig" to mapOf(
                    "revocationCheck" to mapOf(
                        "mode" to "OFF"
                    )
                )
            )
            Unirest.put("/config")
                .body(
                    mapOf(
                        "section" to "corda.p2p.gateway",
                        "version" to currentConfig.`object`.get("version"),
                        "config" to json.writeValueAsString(newConfig),
                        "schemaVersion" to mapOf(
                            "major" to 1,
                            "minor" to 0
                        )
                    )
                ).asJson()
                .bodyOrThrow()
        }
    }
}
