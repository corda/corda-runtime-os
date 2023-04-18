package net.corda.cli.plugins.preinstall

import net.corda.cli.plugins.preinstall.PreInstallPlugin.PluginContext
import net.corda.cli.plugins.preinstall.PreInstallPlugin.Kafka
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.Node
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.util.Properties
import java.util.concurrent.ExecutionException

@CommandLine.Command(name = "check-kafka", description = ["Check that Kafka is up and that the credentials work."])
class CheckKafka : Runnable, PluginContext() {

    @Parameters(index = "0", description = ["The yaml file containing either the username and password value, " +
            "or valueFrom.secretKeyRef.key fields for Kafka."])
    lateinit var path: String

    @Option(names = ["-n", "--namespace"], description = ["The namespace in which to look for the secrets"])
    var namespace: String? = null

    @Option(names = ["-f", "--file"], description = ["The file location of the truststore."])
    var truststoreLocation: String? = null

    @Option(names = ["-t", "--timeout"], description = ["The timeout in milliseconds for testing the kafka connection. Defaults to 3000."])
    var timeout: Int = 3000

    @Option(names = ["-v", "--verbose"], description = ["Display additional information when checking resources"])
    var verbose: Boolean = false

    @Option(names = ["-d", "--debug"], description = ["Show information about limit calculation for debugging purposes"])
    var debug: Boolean = false

    private fun getKafkaProperties(yaml: Kafka, saslUsername: String, saslPassword: String, truststorePassword: String): Properties? {
        val props = Properties()
        props["bootstrap.servers"] = yaml.kafka.bootstrapServers
        props["request.timeout.ms"] = timeout
        props["connections.max.idle.ms"] = 5000

        // Assembles the properties in the same way as the helm charts:
        // https://github.com/corda/corda-runtime-os/blob/release/os/5.0/charts/corda-lib/templates/_bootstrap.tpl#L333
        if (yaml.kafka.tls.enabled) {
            if (yaml.kafka.sasl.enabled) {
                props["security.protocol"] = "SASL_SSL"
                props["sasl.mechanism"] = yaml.kafka.sasl.mechanism
                if (yaml.kafka.sasl.mechanism == "PLAIN") {
                    props["sasl.jaas.config"] =
                        "org.apache.kafka.common.security.plain.PlainLoginModule required username=" +
                                "\"$saslUsername\" password=\"$saslPassword\" ;"
                } else {
                    props["sasl.jaas.config"] =
                        "org.apache.kafka.common.security.plain.ScramLoginModule required username=" +
                                "\"$saslUsername\" password=\"$saslPassword\" ;"
                }
            } else {
                props["security.protocol"] = "SSL"
            }
            yaml.kafka.tls.truststore?.valueFrom?.secretKeyRef?.name?.let {
                props["ssl.truststore.location"] = truststoreLocation ?: run {
                    log("If SSL is enabled, you must provide the location of the truststore file with -f.", ERROR)
                    return null
                }
                props["ssl.truststore.type"] = yaml.kafka.tls.truststore.type.uppercase()
                if (yaml.kafka.tls.truststore.password?.value != null
                    || yaml.kafka.tls.truststore.password?.valueFrom?.secretKeyRef?.name != null
                ) {
                    props["ssl.truststore.password"] = truststorePassword
                }
            }
        } else if (yaml.kafka.sasl.enabled) {
            props["security.protocol"] = "SASL_PLAINTEXT"
            props["sasl.mechanism"] = yaml.kafka.sasl.mechanism
            props["sasl.jaas.config"] = "org.apache.kafka.common.security.scram.ScramLoginModule required username=" +
                    "\"$saslUsername\" password=\"$saslPassword\" ;"
        }
        return props
    }

    // Connect to kafka using the properties assembled earlier
    private fun connect(props: Properties) {
        try {
            val admin = AdminClient.create(props)
            val nodes: Collection<Node>? = admin.describeCluster()
                .nodes()
                .get()
            if (!nodes.isNullOrEmpty()) { println("[INFO] Kafka client connected correctly.") }

            val clusterDescription = admin.describeCluster()
            println("Cluster ID: ${clusterDescription.clusterId().get()}") // prints the cluster ID
        }
        catch (e: KafkaException){
            log("Failed to create kafka client. ${e.cause?.message}", ERROR)
        }
        catch (e: ExecutionException){
            log("Connection to cluster timed out. ${e.cause?.message}", ERROR)
        }
    }

    override fun run() {
        register(verbose, debug)

        val yaml: Kafka = parseYaml<Kafka>(path) ?: return

        var saslUsername = ""
        var saslPassword = ""
        var truststorePassword = ""

        if (yaml.kafka.sasl.enabled) {
            try {
                    saslUsername = getCredentialOrSecret(yaml.kafka.sasl.username!!, namespace) ?: return
                    saslPassword = getCredentialOrSecret(yaml.kafka.sasl.password!!, namespace) ?: return

            } catch (_: NullPointerException) {
                log("If SASL is enabled, you must provide a mechanism, a username, and a password.", ERROR)
                return
            }
        }
        if (yaml.kafka.tls.enabled && yaml.kafka.tls.truststore?.password != null) {
            try {
                truststorePassword = getCredentialOrSecret(yaml.kafka.tls.truststore.password, namespace) ?: return

            } catch (_: NullPointerException) {
                log(
                    "If TLS is enabled, you must provide a truststore with a secret and a type, and if a password is provided " +
                            "- there must be a corresponding secret.", ERROR
                )
                return
            }
        }
        val props = getKafkaProperties(yaml, saslUsername,saslPassword,truststorePassword) ?: return

        connect(props)
    }
}