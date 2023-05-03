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
        names = ["-f", "--file"],
        description = ["The file location of the truststore for Kafka"]
    )
    var truststoreLocation: String? = null

    @Option(
        names = ["-t", "--timeout"],
        description = ["The timeout in milliseconds for testing the kafka connection - defaults to 3000"]
    )
    var timeout: Int = 3000

    class SASLCredentialException(message: String) : Exception(message)
    class BrokerException(message: String) : Exception(message)
    private val logger = getLogger()

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

    class KafkaProperties(private val bootstrapServers: String) {
        var saslUsername: String? = null
        var saslPassword: String? = null
        var saslMechanism: String? = null
        var truststorePassword: String? = null
        var truststoreLocation: String? = null
        var truststoreFile: String? = null
        var truststoreType: String? = null
        var timeout: Int = 3000

        class TruststoreNotFoundException(message: String) : Exception(message)
        class SaslPlainWithoutTlsException(message: String) : Exception(message)

        fun getKafkaProperties(): Properties {
            val props = Properties()
            props["bootstrap.servers"] = bootstrapServers
            props["request.timeout.ms"] = timeout
            props["connections.max.idle.ms"] = 5000

            val tlsEnabled = !(truststoreFile == null && truststorePassword == null)
            val saslEnabled = !(saslUsername == null && saslPassword == null)

            // Assembles the properties in the same way as the helm charts:
            // https://github.com/corda/corda-runtime-os/blob/release/os/5.0/charts/corda-lib/templates/_bootstrap.tpl#L333
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
        val nodes: Collection<Node>? = client.getNodes()
        val clusterID = client.getDescriptionID()

        report.addEntry(ReportEntry("Connect to Kafka cluster using client", true))

        if (nodes.isNullOrEmpty()) {
            report.addEntry(ReportEntry("Kafka cluster has brokers", false))
            return
        }

        logger.info("Kafka client connected to cluster with ID ${clusterID}.")
        logger.info("Number of brokers: ${nodes.size}")
        replicas?.let {
            if (nodes.size < it) {
                throw BrokerException("Number of brokers (${nodes.size}) is less than replica count.")
            }
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
            if (yaml.kafka.sasl.username == null || yaml.kafka.sasl.password == null || yaml.kafka.sasl.mechanism == null) {
                report.addEntry(ReportEntry("Create Kafka client properties",
                    false,
                    SASLCredentialException("If SASL is enabled, you must provide a mechanism, a username, and a password.")))
                logger.error(report.failingTests())
                return 1
            }

            saslMechanism = yaml.kafka.sasl.mechanism

            try {
                saslUsername = getCredential(yaml.kafka.sasl.username, namespace)
                saslPassword = getCredential(yaml.kafka.sasl.password, namespace)
            } catch (e: Exception) {
                report.addEntry(ReportEntry("Get SASL credentials", false, e))
                logger.error(report.failingTests())
                return 1
            }
        }

        if (yaml.kafka.tls.enabled) {
            if (yaml.kafka.tls.truststore == null) {
                report.addEntry(ReportEntry("Parse Kafka properties from YAML",
                    false,
                    TruststoreNotFoundException("If SSL is enabled, you must provide entries for kafka.tls.truststore.")))
                logger.error(report.failingTests())
                return 1
            }

            truststoreType = yaml.kafka.tls.truststore.type

            if (truststoreType != "PEM" && yaml.kafka.tls.truststore.password != null) {
                try {
                    truststorePassword = getCredential(yaml.kafka.tls.truststore.password, namespace)
                } catch (e: Exception) {
                    report.addEntry(ReportEntry("Get TLS truststore password", false, e))
                    logger.error(report.failingTests())
                    return 1
                }
            } else if (yaml.kafka.tls.truststore.valueFrom?.secretKeyRef?.name != null ) {
                val secret = PreInstallPlugin.SecretValues(yaml.kafka.tls.truststore.valueFrom, null, null, null, null)
                try {
                    truststoreFile = getCredential(secret, namespace)
                } catch (e: Exception) {
                    report.addEntry(ReportEntry("Get TLS truststore certificate", false, e))
                    logger.error(report.failingTests())
                    return 1
                }
            }
        }
        val creds = KafkaProperties(yaml.kafka.bootstrapServers)
        saslUsername?.let{ creds.saslUsername = it }
        saslPassword?.let{ creds.saslPassword = it }
        saslMechanism?.let{ creds.saslMechanism = it }
        truststoreLocation?.let{ creds.truststoreLocation = it }
        truststorePassword?.let{ creds.truststorePassword = it }
        truststoreFile?.let{ creds.truststoreFile = it }
        truststoreType?.let{ creds.truststoreType = it }
        creds.timeout = timeout

        val props: Properties
        try {
            props = creds.getKafkaProperties()
        } catch (e: Exception) {
            report.addEntry(ReportEntry("Create Kafka client properties", false, e))
            logger.error(report.failingTests())
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
            logger.error(report.toString())
        } else {
            logger.error(report.failingTests())
        }

        return report.testsPassed()
    }
}