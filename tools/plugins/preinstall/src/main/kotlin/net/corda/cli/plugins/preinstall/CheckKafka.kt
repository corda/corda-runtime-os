package net.corda.cli.plugins.preinstall

import java.util.Properties
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import net.corda.cli.plugins.preinstall.PreInstallPlugin.ClientSASL
import net.corda.cli.plugins.preinstall.PreInstallPlugin.CordaValues
import net.corda.cli.plugins.preinstall.PreInstallPlugin.PluginContext
import net.corda.cli.plugins.preinstall.PreInstallPlugin.Report
import net.corda.cli.plugins.preinstall.PreInstallPlugin.ReportEntry
import net.corda.cli.plugins.preinstall.PreInstallPlugin.SASL
import net.corda.cli.plugins.preinstall.PreInstallPlugin.SecretValues
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.Node
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@CommandLine.Command(
    name = "check-kafka",
    description = ["Check that Kafka is up and that the credentials work."],
    mixinStandardHelpOptions = true
)
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

    open class KafkaAdmin(props: Properties, report: Report) {
        private val admin: AdminClient?

        init {
            // Switch ClassLoader so LoginModules can be found
            val contextCL = Thread.currentThread().contextClassLoader
            try {
                Thread.currentThread().contextClassLoader = this::class.java.classLoader

                admin = AdminClient.create(props)
                report.addEntry(ReportEntry("Created admin client successfully", true))
            } finally {
                Thread.currentThread().contextClassLoader = contextCL
            }
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

    data class KafkaProperties(private val bootstrapServers: String, var saslUsername: String? = null,
                               var saslPassword: String? = null, var saslMechanism: String? = null,
                               var truststorePassword: String? = null, var truststoreFile: String? = null,
                               var truststoreType: String? = null, var timeout: Int = 3000,
                               var tlsEnabled: Boolean = false, var saslEnabled: Boolean = false) {

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
    fun checkConnectionAndBrokers(component: String, client: KafkaAdmin, replicas: Int?) {
        val nodes: Collection<Node> = client.getNodes()
        val clusterID = client.getDescriptionID()

        report.addEntry(ReportEntry("Connect to Kafka cluster using $component client", true))

        logger.info("Kafka $component client connected to cluster with ID ${clusterID}.")
        logger.info("Number of brokers: ${nodes.size}")
        replicas?.let {
            if (nodes.size < it) {
                report.addEntry(ReportEntry("Kafka replica count is less than or equal to the broker count", false))
                return
            }
            report.addEntry(ReportEntry("Kafka replica count is less than or equal to the broker count", true))
        }
    }

    private fun checkKafka(kafkaProperties : KafkaProperties, component: String, defaultSasl: SASL?,
                           clientSasl: ClientSASL?, replicas: Int) {

        val kafkaPropertiesWithCredentials = kafkaProperties.copy()
        if (kafkaProperties.saslEnabled) {
            try {
                kafkaPropertiesWithCredentials.saslUsername = getCredential(defaultSasl?.username, clientSasl?.username, namespace)
                kafkaPropertiesWithCredentials.saslPassword = getCredential(defaultSasl?.password, clientSasl?.password, namespace)
                report.addEntry(ReportEntry("Get $component SASL credentials", true))
            } catch (e: Exception) {
                report.addEntry(ReportEntry("Get $component SASL credentials", false, e))
                return
            }
        }

        val props: Properties
        try {
            props = kafkaPropertiesWithCredentials.getKafkaProperties()
            report.addEntry(ReportEntry("Create $component Kafka client properties", true))
        } catch (e: Exception) {
            report.addEntry(ReportEntry("Create $component Kafka client properties", false, e))
            return
        }

        try {
            checkConnectionAndBrokers(component, KafkaAdmin(props, report), replicas)
        } catch (e: KafkaException) {
            report.addEntry(ReportEntry("Connect to Kafka cluster using $component client", false, e))
        } catch (e: ExecutionException) {
            report.addEntry(ReportEntry("Connect to Kafka cluster using $component client", false, e))
        }
    }

    override fun call(): Int {
        val yaml: CordaValues
        try {
            yaml = parseYaml(path)
            report.addEntry(ReportEntry("Parse Kafka properties from YAML", true))
        } catch (e: Exception) {
            report.addEntry(ReportEntry("Parse Kafka properties from YAML", false, e))
            logger.error(report.failingTests())
            return 1
        }

        val kafkaProperties = getKafkaProperties(yaml)
        if (!report.testsPassed() || kafkaProperties == null) {
            logger.error(report.failingTests())
            return 1
        }

        val replicas = yaml.bootstrap.kafka?.replicas ?: 3
        if (yaml.bootstrap.kafka?.enabled == true) {
            checkKafka(kafkaProperties, "bootstrap", yaml.kafka.sasl, yaml.bootstrap.kafka.sasl, replicas)
        }

        yaml.workers.forEach {
            checkKafka(kafkaProperties, it.key, yaml.kafka.sasl, it.value.kafka?.sasl, replicas)
        }

        return if (report.testsPassed()) {
            logger.info(report.toString())
            0
        } else {
            logger.error(report.failingTests())
            1
        }
    }

    fun getKafkaProperties(yaml: CordaValues) : KafkaProperties? {
        if (yaml.kafka.bootstrapServers.isNullOrEmpty()) {
            report.addEntry(ReportEntry("Bootstrap servers have not been defined under Kafka", false))
            return null
        }

        val kafkaProperties = KafkaProperties(yaml.kafka.bootstrapServers)
        kafkaProperties.timeout = timeout

        if (yaml.kafka.tls?.enabled == true) {
            kafkaProperties.tlsEnabled = true
            kafkaProperties.truststoreType = yaml.kafka.tls.truststore?.type

            if (kafkaProperties.truststoreType == "JKS" &&
                yaml.kafka.tls.truststore?.password != null &&
                providesValueOrSecret(yaml.kafka.tls.truststore.password)
            ) {
                try {
                    kafkaProperties.truststorePassword = getCredential(yaml.kafka.tls.truststore.password, namespace)
                    report.addEntry(ReportEntry("Get TLS truststore password", true))
                } catch (e: Exception) {
                    report.addEntry(ReportEntry("Get TLS truststore password", false, e))
                }
            }
            if (!yaml.kafka.tls.truststore?.valueFrom?.secretKeyRef?.name.isNullOrEmpty()) {
                val secret = SecretValues(yaml.kafka.tls.truststore?.valueFrom, null)
                try {
                    kafkaProperties.truststoreFile = getCredential(secret, namespace)
                    report.addEntry(ReportEntry("Get TLS truststore certificate", true))
                } catch (e: Exception) {
                    report.addEntry(ReportEntry("Get TLS truststore certificate", false, e))
                }
            }
        }

        if (yaml.kafka.sasl?.enabled == true) {
            if (yaml.kafka.sasl.mechanism.isNullOrEmpty()) {
                report.addEntry(ReportEntry("SASL mechanism provided", false))
            }
            kafkaProperties.saslEnabled = true
            kafkaProperties.saslMechanism = yaml.kafka.sasl.mechanism
        }

        return kafkaProperties
    }

    private fun providesValueOrSecret(password: SecretValues) =
        !(password.valueFrom?.secretKeyRef?.name.isNullOrEmpty() &&
                password.value.isNullOrEmpty())
}