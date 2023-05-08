package net.corda.cli.plugins.preinstall

import net.corda.cli.plugins.preinstall.PreInstallPlugin.PluginContext
import net.corda.cli.plugins.preinstall.PreInstallPlugin.Kafka
import net.corda.cli.plugins.preinstall.PreInstallPlugin.ReportEntry
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
        description = ["The namespace in which to look for both the Kafka secrets"]
    )
    var namespace: String? = null

    @Option(
        names = ["-t", "--timeout"],
        description = ["The timeout in milliseconds for testing the Kafka connection - defaults to 3000"]
    )
    var timeout: Int = 3000

    class SASLCredentialException(message: String) : Exception(message)
    private val logger = getLogger()

    open class KafkaAdmin(props: Properties) {
        private val admin: AdminClient?

        init {
            admin = AdminClient.create(props)
        }

        open fun getNodes(): Collection<Node> {
            return admin?.describeCluster()
                ?.nodes()
                ?.get()
                ?: listOf()
        }

        open fun getDescriptionID(): String? {
            return admin?.describeCluster()?.clusterId()?.get()
        }
    }

    data class KafkaProperties(private val bootstrapServers: String) {
        var saslUsername: String? = null
        var saslPassword: String? = null
        var saslMechanism: String? = null
        var truststorePassword: String? = null
        var truststoreFile: String? = null
        var truststoreType: String? = null
        var timeout: Int = 3000
        var tlsEnabled = false
        var saslEnabled = false

        class SaslPlainWithoutTlsException(message: String) : Exception(message)

        fun getKafkaProperties(): Properties {
            val props = Properties()
            props["bootstrap.servers"] = bootstrapServers
            props["request.timeout.ms"] = timeout
            props["connections.max.idle.ms"] = 5000

            // Assembles the properties in the same way as the helm charts:
            // https://github.com/corda/corda-runtime-os/blob/release/os/5.0/charts/corda-lib/templates/_bootstrap.tpl#L185
            if (tlsEnabled) {
                if (saslEnabled) {
                    props["security.protocol"] = "SASL_SSL"
                    props["sasl.mechanism"] = saslMechanism
                    if (saslMechanism == "PLAIN") {
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

                props["ssl.truststore.type"] = truststoreType?.uppercase()
                if (truststorePassword != null) {
                    props["ssl.truststore.password"] = truststorePassword
                }

                if (truststoreFile != null) {
                    props["ssl.truststore.certificates"] = truststoreFile
                }

            } else if (saslEnabled) {
                if (saslMechanism == "PLAIN") {
                    throw SaslPlainWithoutTlsException("If TLS is not enabled, SASL should only be used with SCRAM authentication.")
                }
                props["security.protocol"] = "SASL_PLAINTEXT"
                props["sasl.mechanism"] = saslMechanism
                props["sasl.jaas.config"] =
                    "org.apache.kafka.common.security.scram.ScramLoginModule required username=" +
                            "\"${saslUsername}\" password=\"${saslPassword}\" ;"
            }
            return props
        }
    }

    // Connect to kafka using the properties assembled earlier
    fun connect(client: KafkaAdmin, replicas: Int?) {
        val nodes: Collection<Node> = client.getNodes()
        val clusterID = client.getDescriptionID()

        report.addEntry(ReportEntry("Connect to Kafka cluster using client", true))

        if (nodes.isEmpty()) {
            report.addEntry(ReportEntry("Kafka cluster has brokers", false))
            return
        }
        report.addEntry(ReportEntry("Kafka cluster has brokers", true))

        logger.info("Kafka client connected to cluster with ID ${clusterID}.")
        logger.info("Number of brokers: ${nodes.size}")
        replicas?.let {
            if (nodes.size < it) {
                report.addEntry(ReportEntry("Kafka replica count is less than the broker count", false))
                return
            }
            report.addEntry(ReportEntry("Kafka replica count is less than the broker count", true))
        }
    }

    private fun checkCredential(value: Any?, e: Exception) {
        if (value == null) {
            report.addEntry(ReportEntry("Create Kafka client properties", false, e))
        }
    }

    override fun call(): Int {
        val yaml: Kafka
        try {
            yaml = parseYaml<Kafka>(path)
            report.addEntry(ReportEntry("Parse Kafka properties from YAML", true))
        } catch (e: Exception) {
            report.addEntry(ReportEntry("Parse Kafka properties from YAML", false, e))
            logger.error(report.failingTests())
            return 1
        }

        var saslUsername: String? = null
        var saslPassword: String? = null
        var saslMechanism: String? = null

        var truststorePassword: String? = null
        var truststoreFile: String? = null
        var truststoreType: String? = null

        if (yaml.kafka.sasl.enabled) {
            checkCredential(yaml.kafka.sasl.username, SASLCredentialException("If SASL is enabled, you must provide a username."))
            checkCredential(yaml.kafka.sasl.password, SASLCredentialException("If SASL is enabled, you must provide a password."))
            checkCredential(yaml.kafka.sasl.mechanism, SASLCredentialException("If SASL is enabled, you must provide a mechanism."))

            saslMechanism = yaml.kafka.sasl.mechanism

            try {
                saslUsername = getCredential(yaml.kafka.sasl.username!!, namespace)
                saslPassword = getCredential(yaml.kafka.sasl.password!!, namespace)
                report.addEntry(ReportEntry("Get SASL credentials", true))
            } catch (e: Exception) {
                report.addEntry(ReportEntry("Get SASL credentials", false, e))
                logger.error(report.failingTests())
                return 1
            }
        }

        if (yaml.kafka.tls.enabled) {
            truststoreType = yaml.kafka.tls.truststore?.type

            if (truststoreType != "PEM" && yaml.kafka.tls.truststore?.password != null) {
                try {
                    truststorePassword = getCredential(yaml.kafka.tls.truststore.password, namespace)
                    report.addEntry(ReportEntry("Get TLS truststore password", true))
                } catch (e: Exception) {
                    report.addEntry(ReportEntry("Get TLS truststore password", false, e))
                    logger.error(report.failingTests())
                    return 1
                }
            } else if (yaml.kafka.tls.truststore?.valueFrom?.secretKeyRef?.name != null ) {
                val secret = PreInstallPlugin.SecretValues(yaml.kafka.tls.truststore.valueFrom, null, null, null, null)
                try {
                    truststoreFile = getCredential(secret, namespace)
                    report.addEntry(ReportEntry("Get TLS truststore certificate", true))
                } catch (e: Exception) {
                    report.addEntry(ReportEntry("Get TLS truststore certificate", false, e))
                    logger.error(report.failingTests())
                    return 1
                }
            }
        }

        val creds = KafkaProperties(yaml.kafka.bootstrapServers)
        creds.saslEnabled = yaml.kafka.sasl.enabled
        creds.saslUsername = saslUsername
        creds.saslPassword = saslPassword
        creds.saslMechanism = saslMechanism
        creds.tlsEnabled = yaml.kafka.tls.enabled
        creds.truststorePassword = truststorePassword
        creds.truststoreFile = truststoreFile
        creds.truststoreType = truststoreType
        creds.timeout = timeout

        val props: Properties
        try {
            props = creds.getKafkaProperties()
            report.addEntry(ReportEntry("Create Kafka client properties", true))
        } catch (e: Exception) {
            report.addEntry(ReportEntry("Create Kafka client properties", false, e))
            logger.error(report.failingTests())
            return 1
        }

        try {
            connect(KafkaAdmin(props), yaml.bootstrap?.kafka?.replicas)
        } catch (e: KafkaException) {
            report.addEntry(ReportEntry("Connect to Kafka cluster using client", false, e))
        } catch (e: ExecutionException) {
            report.addEntry(ReportEntry("Connect to Kafka cluster using client", false, e))
        }

        return if (report.testsPassed()) {
            logger.info(report.toString())
            0
        } else {
            logger.error(report.failingTests())
            1
        }
    }
}