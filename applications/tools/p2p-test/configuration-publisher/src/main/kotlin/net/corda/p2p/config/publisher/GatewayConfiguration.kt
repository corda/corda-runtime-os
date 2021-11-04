package net.corda.p2p.config.publisher

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.v5.base.util.toBase64
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Duration

@Command(
    name = "gateway",
    description = ["Publish the P2P gateway configuration"],
    showAtFileInUsageHelp = true,
    showDefaultValues = true,
)
class GatewayConfiguration : ConfigProducer() {
    companion object {
        private fun getDefaultHostname(): String {
            return try {
                InetAddress.getLocalHost().hostName
            } catch (e: UnknownHostException) {
                // getLocalHost might fail if the local host name can not be
                // resolved (for example, when custom hosts file is used)
                @Suppress("TooGenericExceptionCaught")
                return try {
                    ProcessBuilder()
                        .command("hostname")
                        .start()
                        .inputStream
                        .bufferedReader()
                        .readText()
                } catch (e: Exception) {
                    "localhost"
                }
            }
        }
    }

    @Option(
        names = ["--host"],
        description = ["The name of the HTTP host"]
    )
    var hostname = getDefaultHostname()

    @Option(
        names = ["--port"],
        description = ["The HTTP port"]
    )
    var port = 80

    @Option(
        names = ["--keyStore"],
        description = ["The key store file"]
    )
    var keyStoreFile = File("keystore.jks")

    @Option(
        names = ["--keyStorePassword"],
        description = ["The key store password"]
    )
    var keyStorePassword = "password"

    @Option(
        names = ["--trustStore"],
        description = ["The trust store file"]
    )
    var trustStoreFile = File("truststore.jks")

    @Option(
        names = ["--trustStorePassword"],
        description = ["The trust store password"]
    )
    var trustStorePassword = "password"

    @Option(
        names = ["--revocationCheck"],
        description = ["Revocation Check mode (one of: \${COMPLETION-CANDIDATES})"]
    )
    var revocationCheck = RevocationConfigMode.OFF

    @Option(
        names = ["--maxClientConnections"],
        description = ["The maximal number of client connections"]
    )
    var maxClientConnections = 100L

    @Option(
        names = ["--acquireTimeoutSec"],
        description = ["The client connection acquire timeout in seconds"]
    )
    var acquireTimeoutSec = 10L

    @Option(
        names = ["--connectionIdleTimeoutSec"],
        description = ["The amount of time to keep inactive client connection before closing it in seconds"]
    )
    var connectionIdleTimeoutSec = 60L

    @Option(
        names = ["--responseTimeoutMilliSecs"],
        description = ["Time after which a message delivery is considered failed in milliseconds"]
    )
    var responseTimeoutMilliSecs = 1_000L

    @Option(
        names = ["--retryDelayMilliSecs"],
        description = ["Time after which a message is retried, when previously failed in milliseconds"]
    )
    var retryDelayMilliSecs = 1_000L

    override val configuration by lazy {
        ConfigFactory.empty()
            .withValue(
                "hostAddress",
                ConfigValueFactory.fromAnyRef(hostname)
            )
            .withValue(
                "hostPort",
                ConfigValueFactory.fromAnyRef(port)
            )
            .withValue(
                "sslConfig.keyStorePassword",
                ConfigValueFactory.fromAnyRef(keyStorePassword)
            )
            .withValue(
                "sslConfig.keyStore",
                ConfigValueFactory.fromAnyRef(
                    keyStoreFile.readBytes().toBase64()
                )
            )
            .withValue(
                "sslConfig.trustStorePassword",
                ConfigValueFactory.fromAnyRef(trustStorePassword)
            )
            .withValue(
                "sslConfig.trustStore",
                ConfigValueFactory.fromAnyRef(
                    trustStoreFile.readBytes().toBase64()
                )
            )
            .withValue(
                "sslConfig.revocationCheck.mode",
                ConfigValueFactory.fromAnyRef(revocationCheck.toString())
            )
            .withValue(
                "connectionConfig.maxClientConnections",
                ConfigValueFactory.fromAnyRef(maxClientConnections)
            )
            .withValue(
                "connectionConfig.acquireTimeout",
                ConfigValueFactory.fromAnyRef(Duration.ofSeconds(acquireTimeoutSec))
            )
            .withValue(
                "connectionConfig.connectionIdleTimeout",
                ConfigValueFactory.fromAnyRef(Duration.ofSeconds(connectionIdleTimeoutSec))
            )
            .withValue(
                "connectionConfig.responseTimeout",
                ConfigValueFactory.fromAnyRef(Duration.ofMillis(responseTimeoutMilliSecs))
            )
            .withValue(
                "connectionConfig.retryDelay",
                ConfigValueFactory.fromAnyRef(Duration.ofMillis(retryDelayMilliSecs))
            )
    }

    override val key = CordaConfigurationKey(
        "p2p-gateway",
        CordaConfigurationVersion("p2p", 1, 0),
        CordaConfigurationVersion("gateway", 1, 0)
    )
}
