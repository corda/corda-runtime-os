package net.corda.gateway

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.v5.base.util.toBase64
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.random.Random

internal class CliArguments {
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

        fun parse(args: Array<String>): CliArguments {
            val arguments = CliArguments()
            val commandLine = CommandLine(arguments)
            commandLine.isCaseInsensitiveEnumValuesAllowed = true
            @Suppress("SpreadOperator")
            commandLine.parseArgs(*args)

            if (arguments.helpRequested) {
                commandLine.usage(System.out)
            }

            return arguments
        }
    }
    @Option(
        names = ["-h", "--help"],
        usageHelp = true,
        description = ["Display help and exit"]
    )
    var helpRequested = false

    @Option(
        names = ["-k", "--kafka-servers"],
        description = ["The kafka servers (default: \${DEFAULT-VALUE})"]
    )
    var kafkaServers = "localhost:9092"

    @Option(
        names = ["--config-topic-name"],
        description = ["The config topic name (default: \${DEFAULT-VALUE})"]
    )
    var configTopicName = "ConfigTopic"

    @Option(
        names = ["--topic-prefix"],
        description = ["The topic prefix (default: \${DEFAULT-VALUE})"]
    )
    var topicPrefix = "gateway"

    @Option(
        names = ["-i", "--instance-id"],
        description = ["The unique instance ID (default to random number)"]
    )
    var instanceId = Random.nextInt()

    val kafkaNodeConfiguration: Config by lazy {
        ConfigFactory.empty()
            .withValue(
                "messaging.kafka.common.bootstrap.servers",
                ConfigValueFactory.fromAnyRef(kafkaServers)
            )
            .withValue(
                "config.topic.name",
                ConfigValueFactory.fromAnyRef(configTopicName)
            ).withValue(
                "messaging.topic.prefix",
                ConfigValueFactory.fromAnyRef(topicPrefix)
            )
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

    val gatewayConfiguration: Config by lazy {
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
}
