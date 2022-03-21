package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.test.certificates.generation.CertificateAuthority.Companion.PASSWORD
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.pods.Port
import net.corda.p2p.test.KeyAlgorithm
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.RSA_SHA256_TEMPLATE
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
        description = ["The trust store type (\${COMPLETION-CANDIDATES})"]
    )
    var trustStoreType: Deploy.TrustStoreType = Deploy.TrustStoreType.TINY_CERT

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

    private fun KeyAlgorithm.signatureScheme() = when (this) {
        KeyAlgorithm.RSA -> RSA_SHA256_TEMPLATE
        KeyAlgorithm.ECDSA -> ECDSA_SECP256K1_SHA256_TEMPLATE
    }

    private fun keyStoreFile(name: String): File {
        return File(keyStoreDir.absolutePath, "$name.identity.keystore.jks").also { keyStoreFile ->
            if (!keyStoreFile.exists()) {
                keyStoreDir.mkdirs()
                // There is no real need for a Certificate Authority. Java KeyStore need to have a certificate
                // with the public key in order to publish a key pair. So this in memory authority is created just
                // in order to use its key pair to key store facility.
                val authority = CertificateAuthorityFactory.createMemoryAuthority(algo.signatureScheme())
                val keyStore = authority.asKeyStore("identity")
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
            val creator = CreateStores(
                sslStoreFile = keyStoreFile,
                trustStoreFile = trustStoreFile,
                tlsCertificates = tlsCertificates,
                trustStoreLocation = if (trustStoreType == Deploy.TrustStoreType.TINY_CERT) {
                    null
                } else {
                    File(keyStoreDir, "trust-store")
                }
            )
            creator.create(hosts = listOf(host), algo.signatureScheme())
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
                    "publicKeyAlias" to "identity",
                    "keystorePassword" to PASSWORD,
                    "address" to "http://$host:${Port.Gateway.port}",
                    "networkType" to "CORDA_5",
                    "protocolModes" to listOf("AUTHENTICATED_ENCRYPTION"),
                    "trustStoreCertificates" to listOf(trustStoreFile.absolutePath),
                )
            )

        val configurationMap = mapOf(
            "membersToAdd" to listOf(entry),
            "membersToDelete" to emptyList(),
            "groupsToAdd" to emptyList(),
            "groupsToDelete" to emptyList(),
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

    private fun publishGroup() {
        val configurationFile = File.createTempFile("network-map.", ".conf").also {
            it.deleteOnExit()
        }
        val entriesToAdd = mapOf(
            "groupId" to annotations["group-id"],
            "data" to mapOf(
                "networkType" to "CORDA_5",
                "protocolModes" to listOf("AUTHENTICATED_ENCRYPTION"),
                "trustStoreCertificates" to listOf(trustStoreFile.absolutePath),
            )
        )
        val configurationMap = mapOf(
            "membersToAdd" to emptyList(),
            "membersToDelete" to emptyList(),
            "groupsToAdd" to listOf(entriesToAdd),
            "groupsToDelete" to emptyList(),
        )
        jsonWriter.writeValue(configurationFile, configurationMap)
        println("Publishing groups to $namespaceName")
        RunJar(
            "network-map-creator",
            listOf(
                "network-map",
                "--netmap-file", configurationFile.absolutePath,
                "--kafka", RunJar.kafkaFile(namespaceName).absolutePath
            )
        ).run()
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
                        "publicKeyAlias" to "identity",
                        "keystorePassword" to PASSWORD,
                        "address" to "http://$host:${Port.Gateway.port}",
                        "networkType" to "CORDA_5",
                        "protocolModes" to listOf("AUTHENTICATED_ENCRYPTION"),
                        "trustStoreCertificates" to listOf(trustStoreFile.absolutePath),
                    )
                )
            }
        val configurationMap = mapOf(
            "membersToAdd" to otherEntriesToAdd,
            "membersToDelete" to emptyList(),
            "groupsToAdd" to emptyList(),
            "groupsToDelete" to emptyList(),
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
        publishGroup()
    }

    private fun publishKeys() {
        val configurationFile = File.createTempFile("keys.", ".conf").also {
            it.deleteOnExit()
        }
        val configurationMap = mapOf(
            "keys" to listOf(
                mapOf(
                    "keystoreFile" to keyStoreFile.absolutePath,
                    "password" to PASSWORD,
                    "tenantId" to tenantId,
                    "publishAlias" to "$x500name.$groupId.ec",
                ),
                mapOf(
                    "keystoreFile" to sslKeyStore.absolutePath,
                    "password" to PASSWORD,
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
        val gatewayArguments = if (trustStoreType == Deploy.TrustStoreType.LOCAL) {
            gatewayArguments
        } else {
            // Adding revocationCheck when using TinyCert to allow CRL usage
            gatewayArguments + "--revocationCheck=HARD_FAIL"
        }
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
        publishLocallyHostedIdentities()
        publishNetworkMap()
        publishKeys()
        configureLinkManager()
        configureGateway()
    }
}
