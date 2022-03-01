package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.pods.Port
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
                val success = ProcessRunner.follow(
                    "keytool",
                    "-genkeypair",
                    "-alias",
                    "ec",
                    "-keyalg",
                    "EC",
                    "-storetype",
                    "JKS",
                    "-keystore",
                    keyStoreFile.absolutePath,
                    "-storepass",
                    "password",
                    "-dname",
                    "CN=GB",
                    "-keypass",
                    "password"
                )
                if (!success) {
                    throw DeploymentException("Could not create key store for $name")
                }
            }
        }
    }
    private val keyStoreFile by lazy {
        keyStoreFile(host)
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
                    "keystorePassword" to "password",
                    "publicKeyAlgo" to "ECDSA",
                    "address" to "http://$host:${Port.Gateway.port}",
                    "networkType" to "CORDA_5",
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
                    "--netmap-file", configurationFile.absolutePath, "--kafka",
                    RunJar.kafkaFile(nameSpace).absolutePath
                )
            ).run()
        }
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
                        "publicKeyAlgo" to "ECDSA",
                        "address" to "http://$host:${Port.Gateway.port}",
                        "networkType" to "CORDA_5",
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
                    "alias" to "ec",
                    "keystoreFile" to keyStoreFile.absolutePath,
                    "password" to "password",
                    "algo" to "ECDSA",
                    "holdingIdentity" to mapOf(
                        "x500name" to x500name,
                        "groupId" to groupId
                    ),
                ),
                mapOf(
                    "alias" to "$host.$x500name.rsa",
                    "keystoreFile" to sslKeyStore.absolutePath,
                    "password" to "password",
                    "algo" to "RSA",
                    "holdingIdentity" to mapOf(
                        "x500name" to x500name,
                        "groupId" to groupId
                    ),
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
                "--locallyHostedIdentity=$x500name:$groupId:${tlsCertificates(host).absolutePath}",
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
            ) + gatewayArguments
        ).run()
    }

    override fun run() {
        RunJar.startTelepresence()
        publishNetworkMap()
        publishKeys()
        configureLinkManager()
        configureGateway()
    }
}
