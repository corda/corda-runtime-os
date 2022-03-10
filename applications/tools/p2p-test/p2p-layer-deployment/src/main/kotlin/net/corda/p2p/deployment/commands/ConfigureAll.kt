package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.pods.Port
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.stub.certificates.StubCertificatesAuthority
import net.corda.p2p.test.stub.certificates.StubCertificatesAuthority.Companion.PASSWORD
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

@Command(
    name = "configure",
    showDefaultValues = true,
    description = ["configure a cluster (and the other cluster to know about it)"],
    mixinStandardHelpOptions = true,
)
class ConfigureAll : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace to configure"],
        required = true
    )
    lateinit var namespaceName: String

    @Option(
        names = ["-l", "--lm", "--link-manager"],
        description = ["Link manager extra configuration arguments (for example --sessionTimeoutMilliSecs=1800000)"]
    )
    var linkManagerExtraArguments = emptyList<String>()

    @Option(
        names = ["-g", "--gateway"],
        description = ["Gateway extra configuration arguments (for example --responseTimeoutMilliSecs=1800000)"]
    )
    var gatewayArguments = emptyList<String>()

    @Option(
        names = ["-a", "--key-algorithm"],
        description = ["The keys algorithm"]
    )
    var algo = KeyAlgorithm.RSA

    @Option(
        names = ["--trust-store"],
        description = ["The trust store name (leave empty to use TinyCert)"]
    )
    var trustStoreName: String? = null

    private val jsonReader = ObjectMapper()
    private val jsonWriter = jsonReader.writer()

    @Suppress("UNCHECKED_CAST")
    private val namespaces by lazy {
        ProcessRunner.execute(
            "kubectl",
            "get",
            "namespace",
            "-l",
            "namespace-type=p2p-deployment,creator=${MyUserName.userName}",
            "-o",
            "jsonpath={range .items[*]}{.metadata.name}{\"|\"}{.metadata.annotations}{\"\\n\"}{end}",
        ).lines()
            .filter {
                it.contains('|')
            }
            .associate { line ->
                val name = line.substringBefore('|')
                val annotations = line.substringAfter('|')
                name to jsonReader.readValue(annotations, Map::class.java)
            }
    }
    private val annotations by lazy {
        namespaces[namespaceName] ?: throw DeploymentException("Could not find $namespaceName")
    }

    private val host by lazy {
        annotations["host"] as? String ?: throw DeploymentException("Missing host for $namespaceName")
    }
    private val x500name by lazy {
        annotations["x500-name"] as? String ?: throw DeploymentException("Missing x500 name for $namespaceName")
    }
    private val groupId by lazy {
        annotations["group-id"] as? String ?: throw DeploymentException("Missing group ID for $namespaceName")
    }
    private val keyStoreDir = File("p2p-deployment/keystores/")
    private fun keyStoreFile(name: String): File {
        return File(keyStoreDir.absolutePath, "$name.keystore.jks").also { keyStoreFile ->
            if (!keyStoreFile.exists()) {
                keyStoreDir.mkdirs()
                val authority = StubCertificatesAuthority.createLocalAuthority(algo)
                val keyStore = authority.createAuthorityKeyStore("ec")
                keyStoreFile.outputStream().use {
                    keyStore.store(it, PASSWORD.toCharArray())
                }
            }
        }
    }
    private val keyStoreFile by lazy {
        keyStoreFile(host)
    }

    private val tenantId by lazy {
        "$groupId|$x500name"
    }

    private fun tlsCertificates(host: String): File {
        return File(keyStoreDir.absolutePath, "$host.tlsCertificates.pem").also {
            if (!it.exists()) {
                createSslKeys(host)
            }
        }
    }

    private fun createSslKeys(host: String) {
        val keyStoreFile = File(keyStoreDir.absolutePath, "$host.ssl.keystore.jks")
        val trustStoreFile = File(keyStoreDir.absolutePath, "truststore.pem")
        val tlsCertificates = File(keyStoreDir.absolutePath, "$host.tlsCertificates.pem")
        if ((!keyStoreFile.exists()) || (!trustStoreFile.exists())) {
            keyStoreDir.mkdirs()
            val creator = CreateStores()
            creator.sslStoreFile = keyStoreFile
            creator.tlsCertificates = tlsCertificates
            creator.hosts = listOf(host)
            creator.algo = algo
            creator.trustStoreLocation = trustStoreName?.let { File(keyStoreDir, it) }
            creator.trustStoreFile = trustStoreFile.let {
                if (it.exists()) {
                    null
                } else {
                    it
                }
            }
            creator.run()
        }
    }

    private val sslKeyStore by lazy {
        File(keyStoreDir.absolutePath, "$host.ssl.keystore.jks").also {
            if (!it.exists()) {
                createSslKeys(host)
            }
        }
    }

    private val trustStoreFile by lazy {
        File(keyStoreDir.absolutePath, "truststore.pem").also {
            if (!it.exists()) {
                createSslKeys(host)
            }
        }
    }

    private fun publishMySelfToOthers() {
        val configurationFile = File.createTempFile("network-map.", ".conf").also {
            it.deleteOnExit()
        }
        val entry =
            mapOf(
                "x500name" to x500name,
                "groupId" to groupId,
                "data" to mapOf(
                    "publicKeyStoreFile" to keyStoreFile.absolutePath,
                    "publicKeyAlias" to "ec",
                    "keystorePassword" to PASSWORD,
                    "address" to "http://$host:${Port.Gateway.port}",
                    "networkType" to "CORDA_5",
                    "protocolModes" to listOf("AUTHENTICATED_ENCRYPTION"),
                    "trustStoreCertificates" to listOf(trustStoreFile.absolutePath),
                )
            )

        val configurationMap = mapOf(
            "entriesToAdd" to listOf(entry),
            "entriesToDelete" to emptyList()
        )
        jsonWriter.writeValue(configurationFile, configurationMap)
        namespaces.keys.forEach { nameSpace ->
            println("Publishing $namespaceName to $nameSpace")
            RunJar(
                "network-map-creator",
                listOf(
                    "network-map",
                    "--netmap-file", configurationFile.absolutePath, "--kafka",
                    RunJar.kafkaFile(nameSpace).absolutePath
                )
            ).run()
        }
    }

    private fun publishLocallyHostedIdentities() {
        val configurationFile = File.createTempFile("hosting-map.", ".conf").also {
            it.deleteOnExit()
        }
        val entry =
            mapOf(
                "x500name" to x500name,
                "groupId" to groupId,
                "data" to mapOf(
                    "tlsTenantId" to tenantId,
                    "identityTenantId" to tenantId,
                    "tlsCertificates" to listOf(tlsCertificates(host).absolutePath),
                )
            )

        val configurationMap = mapOf(
            "entriesToAdd" to listOf(entry),
            "entriesToDelete" to emptyList()
        )
        jsonWriter.writeValue(configurationFile, configurationMap)
        println("Publishing locally hosted map")
        RunJar(
            "network-map-creator",
            listOf(
                "locally-hosted-map",
                "--hosting-map-file", configurationFile.absolutePath, "--kafka",
                RunJar.kafkaFile(namespaceName).absolutePath
            )
        ).run()
    }

    private fun publishOthersToMySelf() {
        val configurationFile = File.createTempFile("network-map.", ".conf").also {
            it.deleteOnExit()
        }
        val otherNamespaces = namespaces.filterKeys {
            it != namespaceName
        }
        if (otherNamespaces.isEmpty()) {
            return
        }
        val otherEntriesToAdd = otherNamespaces
            .values
            .map { annotations ->
                val host = annotations["host"] as String
                mapOf(
                    "x500name" to annotations["x500-name"],
                    "groupId" to annotations["group-id"],
                    "data" to mapOf(
                        "publicKeyStoreFile" to keyStoreFile(host).absolutePath,
                        "publicKeyAlias" to "ec",
                        "keystorePassword" to "password",
                        "address" to "http://$host:${Port.Gateway.port}",
                        "networkType" to "CORDA_5",
                        "protocolModes" to listOf("AUTHENTICATED_ENCRYPTION"),
                        "trustStoreCertificates" to listOf(trustStoreFile.absolutePath),
                    )
                )
            }
        val configurationMap = mapOf(
            "entriesToAdd" to otherEntriesToAdd,
            "entriesToDelete" to emptyList()
        )
        jsonWriter.writeValue(configurationFile, configurationMap)
        println("Publishing ${otherNamespaces.keys} to $namespaceName")
        RunJar(
            "network-map-creator",
            listOf(
                "network-map",
                "--netmap-file", configurationFile.absolutePath, "--kafka",
                RunJar.kafkaFile(namespaceName).absolutePath
            )
        ).run()
    }

    private fun publishNetworkMap() {
        publishMySelfToOthers()
        publishOthersToMySelf()
    }

    private fun publishKeys() {
        val configurationFile = File.createTempFile("keys.", ".conf").also {
            it.deleteOnExit()
        }
        val configurationMap = mapOf(
            "keys" to listOf(
                mapOf(
                    "keystoreFile" to keyStoreFile.absolutePath,
                    "password" to "password",
                    "tenantId" to tenantId,
                    "publishAlias" to "$x500name.$groupId.ec",
                ),
                mapOf(
                    "keystoreFile" to sslKeyStore.absolutePath,
                    "password" to "password",
                    "tenantId" to tenantId,
                    "publishAlias" to "$host.$x500name.rsa"
                )
            ),
        )
        jsonWriter.writeValue(configurationFile, configurationMap)
        println("Publishing keys to $namespaceName")
        RunJar(
            "cryptoservice-key-creator",
            listOf(
                "--keys-config", configurationFile.absolutePath, "--kafka",
                RunJar.kafkaFile(namespaceName).absolutePath
            )
        ).run()
    }

    private fun configureLinkManager() {
        println("Configure link manager of $namespaceName")
        RunJar(
            "p2p-configuration-publisher",
            listOf(
                "-k",
                RunJar.kafkaServers(namespaceName),
                "link-manager",
            ) + linkManagerExtraArguments
        ).run()
    }

    private fun configureGateway() {
        println("Configure gateway of $namespaceName")
        RunJar(
            "p2p-configuration-publisher",
            listOf(
                "-k",
                RunJar.kafkaServers(namespaceName),
                "gateway",
                "--hostAddress=0.0.0.0",
                "--port=${Port.Gateway.port}",
            ) + gatewayArguments + if (trustStoreName == null) {
                listOf("--revocationCheck=HARD_FAIL")
            } else {
                emptyList()
            }
        ).run()
    }

    override fun run() {
        RunJar.startTelepresence()
        publishLocallyHostedIdentities()
        publishNetworkMap()
        publishKeys()
        configureLinkManager()
        configureGateway()
    }
}
