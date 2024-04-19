package net.corda.sdk.preinstall.checker

import net.corda.sdk.preinstall.data.ClientSASL
import net.corda.sdk.preinstall.data.CordaValues
import net.corda.sdk.preinstall.data.SASL
import net.corda.sdk.preinstall.data.SecretValues
import net.corda.sdk.preinstall.kafka.KafkaAdmin
import net.corda.sdk.preinstall.kafka.KafkaProperties
import net.corda.sdk.preinstall.report.ReportEntry
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.Node
import java.util.Properties
import java.util.concurrent.ExecutionException

class KafkaChecker(
    yamlFilePath: String,
    private val namespace: String? = null,
    private val timeout: Int = 3000
) : BasePreinstallChecker(yamlFilePath) {

    // Connect to kafka using the properties assembled earlier
    fun checkConnectionAndBrokers(component: String, client: KafkaAdmin, replicas: Int?) {
        val nodes: Collection<Node> = client.getNodes()
        val clusterID = client.getDescriptionID()

        report.addEntry(ReportEntry("Connect to Kafka cluster using $component client", true))

        logger.info("Kafka $component client connected to cluster with ID $clusterID.")
        logger.info("Number of brokers: ${nodes.size}")
        replicas?.let {
            if (nodes.size < it) {
                report.addEntry(ReportEntry("Kafka replica count is less than or equal to the broker count", false))
                return
            }
            report.addEntry(ReportEntry("Kafka replica count is less than or equal to the broker count", true))
        }
    }

    private fun checkKafka(
        kafkaProperties: KafkaProperties,
        component: String,
        defaultSasl: SASL?,
        clientSasl: ClientSASL?,
        replicas: Int
    ) {
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

    override fun check(): Int {
        val yaml: CordaValues
        try {
            yaml = parseYaml(yamlFilePath)
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

    fun getKafkaProperties(yaml: CordaValues): KafkaProperties? {
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
        !(
            password.valueFrom?.secretKeyRef?.name.isNullOrEmpty() &&
                password.value.isNullOrEmpty()
            )
}
