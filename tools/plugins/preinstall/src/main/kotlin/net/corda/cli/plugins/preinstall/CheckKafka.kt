package net.corda.cli.plugins.preinstall

import net.corda.cli.plugins.preinstall.PreInstallPlugin.PluginContext
import net.corda.cli.plugins.preinstall.PreInstallPlugin.Kafka
import net.corda.cli.plugins.preinstall.PreInstallPlugin.ReportEntry
import net.corda.cli.plugins.preinstall.CheckKafka.KafkaProperties.TruststoreNotFoundException
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.Node
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.util.Properties
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

@CommandLine.Command(name = "check-kafka", description = ["Check that Kafka is up and that the credentials work."])
class CheckKafka : Callable<Int>, PluginContext() {

    @Parameters(
        index = "0",
        description = ["YAML file containing the Kafka, SASL, and TLS configurations"]
    )
    lateinit var path: String

    @Option(
        names = ["-n", "--namespace"],
        description = ["The namespace in which to look for both the Postgres and Kafka secrets"]
    )
    var namespace: String? = null

    @Option(
        names = ["-u", "--url"],
        description = ["The kubernetes cluster URL (if the preinstall is being called from outside the cluster)"]
    )
    var url: String? = null

    @Option(
        names = ["-f", "--file"],
        description = ["The file location of the truststore for Kafka"]
    )
    var truststoreLocation: String? = null

    @Option(
        names = ["-t", "--timeout"],
        description = ["The timeout in milliseconds for testing the kafka connection - defaults to 3000"]
    )
    var timeout: Int = 3000

    @Option(
        names = ["-m", "--max-idle"],
        description = ["The maximum ms a connection can be idle for while testing the kafka connection - defaults to 5000"]
    )
    var maxIdleMs: Int = 5000

    @Option(
        names = ["-v", "--verbose"],
        description = ["Display additional information about the configuration provided"]
    )
    var verbose: Boolean = false

    @Option(
        names = ["-d", "--debug"],
        description = ["Show information for debugging purposes"]
    )
    var debug: Boolean = false

    class SASLCredentialException(message: String) : Exception(message)
    class BrokerException(message: String) : Exception(message)

    open class KafkaAdmin(props: Properties) {
        private val admin: AdminClient?

        init {
            admin = AdminClient.create(props)
        }

        open fun getNodes(): Collection<Node>? {
            return admin?.describeCluster()
                ?.nodes()
                ?.get()
        }

        open fun getDescriptionID(): String? {
            return admin?.describeCluster()?.clusterId()?.get()
        }
    }

    class KafkaProperties(private val yaml: Kafka) {
        var saslUsername: String? = null
        var saslPassword: String? = null
        var truststorePassword: String? = null
        var truststoreLocation: String? = null
        var truststoreFile: String? = null
        var timeout: Int = 3000
        var connectionsMaxIdleMs: Int = 5000

        class TruststoreNotFoundException(message: String) : Exception(message)

        fun getKafkaProperties(): Properties {
            val props = Properties()
            props["bootstrap.servers"] = yaml.kafka.bootstrapServers
            props["request.timeout.ms"] = timeout
            props["connections.max.idle.ms"] = connectionsMaxIdleMs

            // Assembles the properties in the same way as the helm charts:
            // https://github.com/corda/corda-runtime-os/blob/release/os/5.0/charts/corda-lib/templates/_bootstrap.tpl#L333
            if (yaml.kafka.tls.enabled) {
                if (yaml.kafka.sasl.enabled) {
                    props["security.protocol"] = "SASL_SSL"
                    props["sasl.mechanism"] = yaml.kafka.sasl.mechanism
                    if (yaml.kafka.sasl.mechanism == "PLAIN") {
                        props["sasl.jaas.config"] =
                            "org.apache.kafka.common.security.plain.PlainLoginModule required username=" +
                                    "\"${saslUsername}\" password=\"${saslPassword}\" ;"
                    } else {
                        props["sasl.jaas.config"] =
                            "org.apache.kafka.common.security.scram.ScramLoginModule required username=" +
                                    "\"${saslUsername}\" password=\"${saslPassword}\" ;"
                    }
                } else {
                    props["security.protocol"] = "SSL"
                }

                props["ssl.truststore.type"] = yaml.kafka.tls.truststore!!.type.uppercase()
                if (truststorePassword != null) {
                    props["ssl.truststore.password"] = truststorePassword
                }

                if (truststoreLocation != null) {
                    props["ssl.truststore.location"] = truststoreLocation
                } else if (truststoreFile != null) {
                    props["ssl.truststore.certificates"] = truststoreFile
                } else {
                    throw TruststoreNotFoundException(
                        "If SSL is enabled, you must provide either a truststore.valueFrom.secretKeyRef entry, " +
                                "or specify the location of a truststore file with -f."
                    )
                }

            } else if (yaml.kafka.sasl.enabled) {
                props["security.protocol"] = "SASL_PLAINTEXT"
                props["sasl.mechanism"] = yaml.kafka.sasl.mechanism
                props["sasl.jaas.config"] =
                    "org.apache.kafka.common.security.scram.ScramLoginModule required username=" +
                            "\"${saslUsername}\" password=\"${saslPassword}\" ;"
            }
            return props
        }
    }

    // Connect to kafka using the properties assembled earlier
    fun connect(client: KafkaAdmin, replicas: Int?) {
        val nodes: Collection<Node>? = client.getNodes()
        val clusterID = client.getDescriptionID()

        report.addEntry(ReportEntry("Connect to Kafka cluster using client", true))

        if (nodes.isNullOrEmpty()) {
            report.addEntry(ReportEntry("Kafka cluster has brokers", false))
            return
        }

        log("Kafka client connected to cluster with ID ${clusterID}.", INFO)
        log("Number of brokers: ${nodes.size}", INFO)
        replicas?.let {
            if (nodes.size < it) {
                throw BrokerException("Number of brokers (${nodes.size}) is less than replica count.")
            }
        }
    }

    override fun call(): Int {
        register(verbose, debug)

        val yaml: Kafka
        try {
            yaml = parseYaml<Kafka>(path)
            report.addEntry(ReportEntry("Parse Kafka properties from YAML", true))
        } catch (e: Exception) {
            report.addEntry(ReportEntry("Parse Kafka properties from YAML", false, e))
            log(report.failingTests(), ERROR)
            return 1
        }

        var saslUsername: String? = null
        var saslPassword: String? = null
        var truststorePassword: String? = null
        var truststoreFile: String? = null

        if (yaml.kafka.sasl.enabled) {
            if (yaml.kafka.sasl.username == null || yaml.kafka.sasl.password == null || yaml.kafka.sasl.mechanism == null) {
                report.addEntry(ReportEntry("Create Kafka client properties",
                    false,
                    SASLCredentialException("If SASL is enabled, you must provide a mechanism, a username, and a password.")))
                log(report.failingTests(), ERROR)
                return 1
            }
            try {
                saslUsername = getCredentialOrSecret(yaml.kafka.sasl.username, namespace, url)
                saslPassword = getCredentialOrSecret(yaml.kafka.sasl.password, namespace, url)
            } catch (e: Exception) {
                report.addEntry(ReportEntry("Get SASL credentials", false, e))
                log(report.failingTests(), ERROR)
                return 1
            }
        }

        if (yaml.kafka.tls.enabled) {
            if (yaml.kafka.tls.truststore == null) {
                report.addEntry(ReportEntry("Parse Kafka properties from YAML",
                    false,
                    TruststoreNotFoundException("If SSL is enabled, you must provide entries for kafka.tls.truststore.")))
                log(report.failingTests(), ERROR)
                return 1
            }

            if (yaml.kafka.tls.truststore.type != "PEM" && yaml.kafka.tls.truststore.password != null) {
                try {
                    truststorePassword = getCredentialOrSecret(yaml.kafka.tls.truststore.password, namespace, url)
                } catch (e: Exception) {
                    report.addEntry(ReportEntry("Get TLS truststore password", false, e))
                    log(report.failingTests(), ERROR)
                    return 1
                }
            } else if (yaml.kafka.tls.truststore.valueFrom?.secretKeyRef?.name != null ) {
                val secret = PreInstallPlugin.SecretValues(yaml.kafka.tls.truststore.valueFrom, null, null, null, null)
                try {
                    truststoreFile = getCredentialOrSecret(secret, namespace, url)
                } catch (e: Exception) {
                    report.addEntry(ReportEntry("Get TLS truststore certificate", false, e))
                    log(report.failingTests(), ERROR)
                    return 1
                }
            }
        }
        val creds = KafkaProperties(yaml)
        saslUsername?.let{ creds.saslUsername = it }
        saslPassword?.let{ creds.saslPassword = it }
        truststoreLocation?.let{ creds.truststoreLocation = it }
        truststorePassword?.let{ creds.truststorePassword = it }
        truststoreFile?.let{ creds.truststoreFile = it }
        creds.timeout = timeout
        creds.connectionsMaxIdleMs = maxIdleMs

        val props: Properties
        try {
            props = creds.getKafkaProperties()
        } catch (e: TruststoreNotFoundException) {
            report.addEntry(ReportEntry("Create Kafka client properties", false, e))
            log(report.failingTests(), ERROR)
            return 1
        }

        try {
            connect(KafkaAdmin(props), yaml.bootstrap?.kafka?.replicas)
        } catch (e: KafkaException) {
            report.addEntry(ReportEntry("Create Kafka client", false, e))
        } catch (e: ExecutionException) {
            report.addEntry(ReportEntry("Connect to Kafka cluster using client", false, e))
        } catch (e: BrokerException) {
            report.addEntry(ReportEntry("Kafka replica count is less than the broker count", false, e))
        }

        if (report.testsPassed() == 0) {
            log(report.toString(), INFO)
        } else {
            log(report.failingTests(), ERROR)
        }

        return report.testsPassed()
    }
}