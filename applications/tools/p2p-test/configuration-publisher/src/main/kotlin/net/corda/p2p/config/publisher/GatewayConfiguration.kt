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

@Command(name = "gateway", description = ["Publish the P2P gateway configuration"])
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
        description = ["The name of the HTTP host (default: \${DEFAULT-VALUE})"]
    )
    var hostname = getDefaultHostname()

    @Option(
        names = ["--port"],
        description = ["The HTTP port (default: \${DEFAULT-VALUE})"]
    )
    var port = 80

    @Option(
        names = ["--keyStore"],
        description = ["The key store file (default: keystore.jks)"]
    )
    var keyStoreFile = File("keystore.jks")

    @Option(
        names = ["--keyStorePassword"],
        description = ["The key store password (default: \${DEFAULT-VALUE})"]
    )
    var keyStorePassword = "password"

    @Option(
        names = ["--trustStore"],
        description = ["The trust store file (default: truststore.jks)"]
    )
    var trustStoreFile = File("truststore.jks")

    @Option(
        names = ["--trustStorePassword"],
        description = ["The trust store password (default: \${DEFAULT-VALUE})"]
    )
    var trustStorePassword = "password"

    @Option(
        names = ["--revocationCheck"],
        description = ["Revocation Check mode (one of: \${COMPLETION-CANDIDATES})"]
    )
    var revocationCheck = RevocationConfigMode.OFF

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
                "traceLogging",
                ConfigValueFactory.fromAnyRef(true)
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
    }

    override val key = CordaConfigurationKey(
        "p2p-gateway",
        CordaConfigurationVersion("p2p", 1, 0),
        CordaConfigurationVersion("gateway", 1, 0)
    )
}
