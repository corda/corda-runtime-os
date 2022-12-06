package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.pods.Port
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.security.KeyPairGenerator

@Command(
    name = "configure",
    showDefaultValues = true,
    description = ["configure a cluster (and the other cluster to know about it)"],
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true,
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
            "namespace-type=p2p-deployment,creator=${MyUserName.userName},p2p-namespace-type=p2p-cluster",
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
    private val x500Name by lazy {
        annotations["x500-name"] as? String ?: throw DeploymentException("Missing x500 name for $namespaceName")
    }
    private val groupId by lazy {
        annotations["group-id"] as? String ?: throw DeploymentException("Missing group ID for $namespaceName")
    }
    private val keyStoreDir = File("p2p-deployment/keystores/")

    private fun KeyAlgorithm.keyScheme() = when (this) {
        KeyAlgorithm.RSA -> RSA_TEMPLATE
        KeyAlgorithm.ECDSA -> ECDSA_SECP256R1_TEMPLATE
    }

    private fun identityKeyFilePair(name: String): Pair<File, File> {
        val privateKeyFile = File(keyStoreDir.absolutePath, "$name.identity.private.key.pem")
        val publicKeyFile = File(keyStoreDir.absolutePath, "$name.identity.public.key.pem")
        if (!privateKeyFile.exists()) {
            privateKeyFile.parentFile.mkdirs()
            val keySchemeTemplate = algo.keyScheme()
            val keysFactory = KeyPairGenerator.getInstance(keySchemeTemplate.algorithmName, BouncyCastleProvider())
            if (keySchemeTemplate.algSpec != null) {
                keysFactory.initialize(keySchemeTemplate.algSpec)
            }
            val pair = keysFactory.generateKeyPair()

            privateKeyFile.writer().use { writer ->
                JcaPEMWriter(writer).use {
                    it.writeObject(pair)
                }
            }
            publicKeyFile.writer().use { writer ->
                JcaPEMWriter(writer).use {
                    it.writeObject(pair.public)
                }
            }
        }
        return privateKeyFile to publicKeyFile
    }
    private fun publicKeyFile(name: String): File {
        return identityKeyFilePair(name).second
    }
    private fun privateKeyFile(name: String): File {
        return identityKeyFilePair(name).first
    }

    private val tenantId by lazy {
        "$groupId|$x500Name"
    }

    private fun tlsCertificates(host: String): File {
        return File(keyStoreDir.absolutePath, "$host.tlsCertificates.pem").also {
            if (!it.exists()) {
                createSslKeys(host)
            }
        }
    }

    private fun createSslKeys(host: String) {
        val sslPrivateKeyFile = File(keyStoreDir.absolutePath, "$host.ssl.keystore.pem")
        val trustStoreFile = File(keyStoreDir.absolutePath, "truststore.pem")
        val tlsCertificates = File(keyStoreDir.absolutePath, "$host.tlsCertificates.pem")
        if ((!sslPrivateKeyFile.exists()) || (!trustStoreFile.exists())) {
            keyStoreDir.mkdirs()
            val creator = CreateStores(
                sslPrivateKeyFile = sslPrivateKeyFile,
                trustStoreFile = trustStoreFile,
                tlsCertificates = tlsCertificates,
                trustStoreLocation = if (trustStoreType == Deploy.TrustStoreType.TINY_CERT) {
                    null
                } else {
                    File(keyStoreDir, "trust-store")
                }
            )
            creator.create(hosts = listOf(host), algo.keyScheme())
        }
    }

    private val sslPrivateKeyFile by lazy {
        File(keyStoreDir.absolutePath, "$host.ssl.keystore.pem").also {
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
                "x500Name" to x500Name,
                "groupId" to groupId,
                "data" to mapOf(
                    "publicSessionKey" to publicKeyFile(host).readText(),
                    "address" to "http://$host:${Port.Gateway.port}",
                    "networkType" to "CORDA_5",
                    "protocolModes" to listOf("AUTHENTICATED_ENCRYPTION"),
                    "trustRootCertificates" to listOf(trustStoreFile.absolutePath),
                )
            )

        jsonWriter.writeValue(configurationFile, entry)
        namespaces.keys.forEach { nameSpace ->
            println("Publishing $namespaceName to $nameSpace")
            RunJar(
                "p2p-setup",
                listOf(
                    "-k",
                    RunJar.kafkaServers(nameSpace),
                    "add-member",
                    configurationFile.absolutePath
                )
            ).run()
        }
    }

    private fun groupCommands(): Collection<String> {
        val configurationFile = File.createTempFile("network-map.", ".conf").also {
            it.deleteOnExit()
        }
        val entriesToAdd = mapOf(
            "groupId" to annotations["group-id"],
            "x500Name" to x500Name,
            "data" to mapOf(
                "networkType" to "CORDA_5",
                "protocolModes" to listOf("AUTHENTICATED_ENCRYPTION"),
                "trustRootCertificates" to listOf(trustStoreFile.readText()),
            )
        )
        jsonWriter.writeValue(configurationFile, entriesToAdd)

        return listOf(
            "add-group",
            configurationFile.absolutePath
        )
    }

    private fun locallyHostedIdentitiesCommands(): List<String> {
        val configurationFile = File.createTempFile("hosting-map.", ".conf").also {
            it.deleteOnExit()
        }
        val entry =
            mapOf(
                "x500Name" to x500Name,
                "groupId" to groupId,
                "data" to mapOf(
                    "tlsTenantId" to tenantId,
                    "sessionKeyTenantId" to tenantId,
                    "tlsCertificates" to listOf(tlsCertificates(host).readText()),
                    "publicSessionKey" to publicKeyFile(host).readText(),
                )
            )

        jsonWriter.writeValue(configurationFile, entry)
        return listOf(
            "add-identity",
            configurationFile.absolutePath
        )
    }

    private fun othersToMySelfCommands(): List<String> {
        val otherNamespaces = namespaces.filterKeys {
            it != namespaceName
        }
        if (otherNamespaces.isEmpty()) {
            return emptyList()
        }
        return otherNamespaces
            .values
            .map { annotations ->
                val host = annotations["host"] as String
                mapOf(
                    "x500Name" to annotations["x500-name"],
                    "groupId" to annotations["group-id"],
                    "data" to mapOf(
                        "publicSessionKey" to publicKeyFile(host).readText(),
                        "address" to "http://$host:${Port.Gateway.port}",
                        "networkType" to "CORDA_5",
                        "protocolModes" to listOf("AUTHENTICATED_ENCRYPTION"),
                    )
                )
            }.map { configurationMap ->
                File.createTempFile("network-map.", ".conf").also { file ->
                    file.deleteOnExit()
                    jsonWriter.writeValue(file, configurationMap)
                }
            }.flatMap { configurationFile ->
                listOf("add-member", configurationFile.absolutePath)
            }
    }

    private fun keysCommands(): List<String> {
        val keyStoreFile = File.createTempFile("keys.", ".conf").also {
            it.deleteOnExit()
            jsonWriter.writeValue(
                it,
                mapOf(
                    "keys" to privateKeyFile(host).readText(),
                    "tenantId" to tenantId,
                    "publishAlias" to "$x500Name.$groupId.ec",
                )
            )
        }
        val sslKeyStoreFile = File.createTempFile("keys.", ".conf").also {
            it.deleteOnExit()
            jsonWriter.writeValue(
                it,
                mapOf(
                    "keys" to sslPrivateKeyFile.readText(),
                    "tenantId" to tenantId,
                    "publishAlias" to "$host.$x500Name.rsa"
                )
            )
        }
        return listOf(
            "add-keys", keyStoreFile.absolutePath,
            "add-keys", sslKeyStoreFile.absolutePath,
        )
    }

    private fun configurationCommands(): Collection<String> {
        val gatewayArguments = if (trustStoreType == Deploy.TrustStoreType.LOCAL) {
            gatewayArguments
        } else {
            // Adding revocationCheck when using TinyCert to allow CRL usage
            gatewayArguments + "--revocationCheck=HARD_FAIL"
        }
        return listOf(
            "config-link-manager"
        ) + linkManagerExtraArguments +
            listOf(
                "config-gateway",
                "--hostAddress=0.0.0.0",
                "--port=${Port.Gateway.port}",
            ) + gatewayArguments
    }

    private fun setUp() {
        val commands = locallyHostedIdentitiesCommands() +
            othersToMySelfCommands() + groupCommands() +
            keysCommands() + configurationCommands()

        println("Setting up...")
        RunJar(
            "p2p-setup",
            listOf(
                "-k",
                RunJar.kafkaServers(namespaceName)
            ) + commands
        )
            .run()
    }

    override fun run() {
        RunJar.startTelepresence()
        publishMySelfToOthers()
        setUp()
    }
}
