package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.Yaml
import net.corda.p2p.deployment.getAndCheckEnv
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Command(
    name = "create-stores",
    showDefaultValues = true,
    description = ["Create key and trust stores"],
    mixinStandardHelpOptions = true,
)
class CreateStores : Runnable {
    companion object {
        const val authorityName = "R3P2pAuthority"
    }
    private val mapper = ObjectMapper()

    @Option(
        names = ["-k", "--tinycert-api-key"],
        description = ["The TinyCert API Key"]
    )
    private var apiKey = getAndCheckEnv("TINYCERT_API_KEY")

    @Option(
        names = ["-p", "--tinycert-passphrase"],
        description = ["The TinyCert Pass phrase"]
    )
    private var passPhrase = getAndCheckEnv("TINYCERT_PASS_PHRASE")

    @Option(
        names = ["-e", "--tinycert-email"],
        description = ["The TinyCert email"]
    )
    private var email = getAndCheckEnv("TINYCERT_EMAIL")

    @Option(
        names = ["--hosts"],
        description = ["The host names"]
    )
    var hosts = listOf("corda.net", "www.corda.net", "dev.corda.net")

    @Option(
        names = ["-t", "--trust-store"],
        description = ["The trust store file"]
    )
    var trustStoreFile: File? = File("truststore.pem")

    @Option(
        names = ["-c", "--certificate-chain-file"],
        description = ["The TLS certificate chain file"]
    )
    var tlsCertificates: File? = null

    @Option(
        names = ["-s", "--ssl-store"],
        description = ["The SSL store file"]
    )
    var sslStoreFile = File("keystore.jks")

    @Option(
        names = ["--key-store-password"],
        description = ["The key store password"]
    )
    private var keyStorePassword = "password"

    private val digester by lazy {
        Mac.getInstance("HmacSHA256").also { mac ->
            mac.init(
                SecretKeySpec(
                    apiKey.toByteArray(),
                    "HmacSHA256"
                )
            )
        }
    }
    private val client = HttpClient.newHttpClient()

    private fun callApi(api: String, data: Map<String, String>): String {
        val encodedData = data.toSortedMap().mapValues {
            URLEncoder.encode(it.value, "utf-8")
        }.mapKeys {
            URLEncoder.encode(it.key, "utf-8")
        }.map { (key, value) ->
            "$key=$value"
        }.joinToString("&")

        val rawDigest = digester.doFinal(encodedData.toByteArray())
        val digest = rawDigest.joinToString(separator = "") {
            eachByte ->
            "%02x".format(eachByte)
        }
        val postData = "$encodedData&digest=$digest"
        val connect = HttpRequest.newBuilder()
            .uri(URI("https://www.tinycert.org/api/v1/$api"))
            .header("content-type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(postData))
            .build()
        val response = client.send(
            connect,
            HttpResponse.BodyHandlers.ofString()
        )
        return response.body()
    }

    @Suppress("UNCHECKED_CAST")
    private fun callApiMap(api: String, data: Map<String, String>): Yaml {
        return mapper.readValue(
            callApi(api, data),
            Map::class.java
        ) as Yaml
    }

    @Suppress("UNCHECKED_CAST")
    private fun callApiList(api: String, data: Map<String, String>): Collection<Yaml> {
        return mapper.readValue(
            callApi(api, data),
            Collection::class.java
        ) as Collection<Yaml>
    }

    private val token by lazy {
        val called = callApiMap(
            "connect",
            mapOf(
                "email" to email,
                "passphrase" to passPhrase
            )
        )
        called["token"] as String
    }

    private fun disconnect() {
        callApi(
            "disconnect",
            mapOf(
                "token" to token,
            )
        )
    }

    private val certificationAuthorities by lazy {
        callApiList(
            "ca/list",
            mapOf(
                "token" to token,
            )
        )
    }

    private val authority by lazy {
        val knownAuthority = certificationAuthorities.firstOrNull {
            it["name"] == authorityName
        }
        if (knownAuthority == null) {
            callApiMap(
                "ca/new",
                mapOf(
                    "C" to "GB",
                    "O" to authorityName,
                    "hash_method" to "sha256",
                    "token" to token
                )
            )["ca_id"] as Number
        } else {
            knownAuthority["id"] as Number
        }
    }

    private val pemFile by lazy {
        File.createTempFile("p2p", ".pem").also { pemFile ->
            pemFile.deleteOnExit()
            val pem = callApiMap(
                "ca/get",
                mapOf(
                    "ca_id" to authority.toString(),
                    "token" to token,
                    "what" to "cert"
                )
            )["pem"] as String
            pemFile.writeText(pem)
        }
    }

    private val certificates by lazy {
        callApiList(
            "cert/list",
            mapOf(
                "ca_id" to authority.toString(),
                "what" to "2",
                "token" to token,
            )
        )
    }

    private val certificate by lazy {
        val knownCertificate = certificates.firstOrNull {
            it["name"] == hosts.first()
        }
        if (knownCertificate == null) {
            val sans = hosts.mapIndexed { index, host ->
                "SANs[$index][DNS]" to host
            }.toMap()
            (
                callApiMap(
                    "cert/new",
                    mapOf(
                        "C" to "GB",
                        "CN" to hosts.first(),
                        "O" to authorityName,
                        "ca_id" to authority.toString(),
                        "token" to token
                    ) + sans
                )["cert_id"] as Number
                )
        } else {
            knownCertificate["id"] as Number
        }
    }

    private val chain by lazy {
        callApiMap(
            "cert/get",
            mapOf(
                "cert_id" to certificate.toString(),
                "token" to token,
                "what" to "chain"
            )
        )["pem"] as String
    }

    private val privateKey by lazy {
        callApiMap(
            "cert/get",
            mapOf(
                "cert_id" to certificate.toString(),
                "token" to token,
                "what" to "key.dec"
            )
        )["pem"] as String
    }

    private fun runCommand(vararg commands: String) {
        val success = ProcessRunner.follow(*commands)
        if (!success) {
            throw DeploymentException("Could not run command ${commands.joinToString(" ")}")
        }
    }

    private fun createTrustStore(trustStoreFile: File) {
        pemFile.copyTo(trustStoreFile)
    }

    private fun createSslStore() {
        runCommand(
            "keytool",
            "-genkey",
            "-keyalg",
            "RSA",
            "-alias",
            "cordaclienttls",
            "-keystore",
            sslStoreFile.absolutePath,
            "-dname", "CN=r3.com, OU=ID, O=R3, C=GB",
            "-storepass", keyStorePassword,
            "-keypass", keyStorePassword,
        )

        runCommand(
            "keytool",
            "-delete",
            "-alias",
            "cordaclienttls",
            "-keystore",
            sslStoreFile.absolutePath,
            "-storepass", keyStorePassword,
            "-keypass", keyStorePassword,
        )

        val combine = File.createTempFile("combined", ".pem").also {
            it.deleteOnExit()
        }
        combine.writeText(chain)
        combine.appendText(privateKey)

        val combinePkcs12 = File.createTempFile("combined", ".pkcs12").also {
            it.deleteOnExit()
        }
        combinePkcs12.delete()

        runCommand(
            "openssl",
            "pkcs12",
            "-export",
            "-password", "pass:$keyStorePassword",
            "-out",
            combinePkcs12.absolutePath,
            "-in",
            combine.absolutePath,

        )

        runCommand(
            "keytool",
            "-v",
            "-importkeystore",
            "-srckeystore",
            combinePkcs12.absolutePath,
            "-srcstoretype",
            "PKCS12",
            "-destkeystore",
            sslStoreFile.absolutePath,
            "-deststoretype",
            "JKS",
            "-srcstorepass", keyStorePassword,
            "-deststorepass", keyStorePassword,
            "-noprompt",
        )
    }

    override fun run() {
        try {
            trustStoreFile?.let {
                createTrustStore(it)
            }
            createSslStore()
            tlsCertificates?.writeText(chain)
        } finally {
            disconnect()
        }
    }
}
