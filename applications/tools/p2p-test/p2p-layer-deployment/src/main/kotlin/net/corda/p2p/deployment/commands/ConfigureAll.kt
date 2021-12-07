package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.Yaml
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.nio.file.Files

@Command(
    name = "configure",
    showDefaultValues = true,
    description = ["configure a cluster (and the other cluster to know about it)"]
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
        description = ["Link manager extra configuration arguments"]
    )
    var linkManagerExtraArguments = listOf("--sessionTimeoutMilliSecs", "1800000")

    @Option(
        names = ["-g", "--gateway"],
        description = ["Gateway extra configuration arguments"]
    )
    var gatewayArguments = listOf("--responseTimeoutMilliSecs", "1800000")

    private val yamlReader = ObjectMapper(YAMLFactory()).reader()
    private val jsonWriter = ObjectMapper().writer()

    @Suppress("UNCHECKED_CAST")
    private val namespaces by lazy {
        val getAll = ProcessBuilder().command(
            "kubectl",
            "get",
            "namespace",
            "-o",
            "yaml"
        ).start()
        if (getAll.waitFor() != 0) {
            System.err.println(getAll.errorStream.reader().readText())
            throw DeploymentException("Could not get namespaces")
        }
        val rawData = yamlReader.readValue(getAll.inputStream, Map::class.java)
        val items = rawData["items"] as List<Yaml>
        items.map {
            it["metadata"] as Yaml
        }.filter {
            val annotations = it["annotations"] as? Yaml
            annotations?.get("type") == "p2p"
        }.associate {
            it["name"] as String to it["annotations"] as Yaml
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
                val createKetstore = ProcessBuilder().command(
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
                ).inheritIO()
                    .start()
                if (createKetstore.waitFor() != 0) {
                    throw DeploymentException("Could not create key store for $name")
                }
            }
        }
    }
    private val keyStoreFile by lazy {
        keyStoreFile(host)
    }

    private val sslKeyStore by lazy {
        File(keyStoreDir.absolutePath, "$host.ssl.keystore.jks").also { keyStoreFile ->
            if (!keyStoreFile.exists()) {
                keyStoreDir.mkdirs()
                val creator = CreateStores()
                creator.sslStoreFile = keyStoreFile
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
    }

    private val trustStoreFile = File(keyStoreDir.absolutePath, "truststore.jks")

    private val kafkaServers = mutableMapOf<String, String>()
    @Suppress("UNCHECKED_CAST")
    private fun kafkaServers(namespace: String): String {
        return kafkaServers.computeIfAbsent(namespace) {
            val getAll = ProcessBuilder().command(
                "kubectl",
                "get",
                "service",
                "-n",
                namespace,
                "-o",
                "yaml"
            ).start()
            if (getAll.waitFor() != 0) {
                System.err.println(getAll.errorStream.reader().readText())
                throw DeploymentException("Could not get services")
            }
            val rawData = yamlReader.readValue(getAll.inputStream, Map::class.java)
            val items = rawData["items"] as List<Yaml>
            items.asSequence().map {
                it["metadata"] as Yaml
            }.mapNotNull {
                it["name"] as? String
            }.filter {
                it.startsWith("kafka-broker-")
            }.map {
                "$it.$namespace:9093"
            }.joinToString(",")
        }
    }

    private val kafkaFiles = mutableMapOf<String, File>()
    private fun kafkaFile(namespace: String): File {
        return kafkaFiles.computeIfAbsent(namespace) {
            File.createTempFile("$namespace.kafka.", ".properties").also { file ->
                file.deleteOnExit()
                file.delete()
                file.writeText("bootstrap.servers=${kafkaServers(namespace)}")
            }
        }
    }

    private val savedJar = mutableMapOf<String, File>()
    private fun jarToRun(jarName: String): File {
        return savedJar.computeIfAbsent(jarName) {
            File.createTempFile(jarName, ".jar").also { jarFile ->
                jarFile.deleteOnExit()
                jarFile.delete()
                ClassLoader.getSystemClassLoader()
                    .getResource("$jarName.jar")
                    ?.openStream()?.use { input ->
                        Files.copy(input, jarFile.toPath())
                    }
            }
        }
    }

    private fun runJar(jarName: String, arguments: Collection<String>) {
        val jarFile = jarToRun(jarName)
        val java = "${System.getProperty("java.home")}/bin/java"
        val commands = listOf(java, "-jar", jarFile.absolutePath) + arguments
        ProcessBuilder()
            .command(
                commands
            )
            .inheritIO()
            .start()
            .waitFor()
    }

    private fun startTelepresence() {
        ProcessBuilder()
            .command(
                "telepresence",
                "connect"
            )
            .inheritIO()
            .start()
            .waitFor()
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
                    "address" to "http://$host:1433",
                    "networkType" to "CORDA_5"
                )
            )

        val configurationMap = mapOf(
            "entriesToAdd" to listOf(entry),
            "entriesToDelete" to emptyList()
        )
        jsonWriter.writeValue(configurationFile, configurationMap)
        namespaces.keys.forEach { nameSpace ->
            println("Publishing $namespaceName to $nameSpace")
            runJar(
                "network-map-creator",
                listOf(
                    "--netmap-file", configurationFile.absolutePath, "--kafka",
                    kafkaFile(nameSpace).absolutePath
                )
            )
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
                        "address" to "http://$host:1433",
                        "networkType" to "CORDA_5"
                    )
                )
            }
        val configurationMap = mapOf(
            "entriesToAdd" to otherEntriesToAdd,
            "entriesToDelete" to emptyList()
        )
        jsonWriter.writeValue(configurationFile, configurationMap)
        println("Publishing ${otherNamespaces.keys} to $namespaceName")
        runJar(
            "network-map-creator",
            listOf(
                "--netmap-file", configurationFile.absolutePath, "--kafka",
                kafkaFile(namespaceName).absolutePath
            )
        )
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
                    "algo" to "ECDSA"
                )
            )
        )
        jsonWriter.writeValue(configurationFile, configurationMap)
        println("Publishing keys to $namespaceName")
        runJar(
            "cryptoservice-key-creator",
            listOf(
                "--keys-config", configurationFile.absolutePath, "--kafka",
                kafkaFile(namespaceName).absolutePath
            )
        )
    }

    private fun configureLinkManager() {
        println("Configure link manager of $namespaceName")
        runJar(
            "configuration-publisher",
            listOf(
                "-k",
                kafkaServers(namespaceName),
                "link-manager",
                "--locallyHostedIdentity=$x500name:$groupId",
            ) + linkManagerExtraArguments
        )
    }

    private fun configureGateway() {
        println("Configure gateway of $namespaceName")
        runJar(
            "configuration-publisher",
            listOf(
                "-k",
                kafkaServers(namespaceName),
                "gateway",
                "--hostAddress=$host",
                "--port=1433",
                "--keyStore=${sslKeyStore.absolutePath}",
                "--keyStorePassword=password",
                "--trustStore=${trustStoreFile.absolutePath}",
                "--trustStorePassword=password",
            ) + gatewayArguments
        )
    }

    override fun run() {
        startTelepresence()
        publishNetworkMap()
        publishKeys()
        configureLinkManager()
        configureGateway()
    }
}
