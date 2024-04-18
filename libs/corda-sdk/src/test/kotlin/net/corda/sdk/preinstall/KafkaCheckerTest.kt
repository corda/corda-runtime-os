package net.corda.sdk.preinstall

import net.corda.sdk.preinstall.checker.KafkaChecker
import net.corda.sdk.preinstall.data.CordaValues
import net.corda.sdk.preinstall.data.SecretKeyRef
import net.corda.sdk.preinstall.data.SecretValues
import net.corda.sdk.preinstall.data.ValueFrom
import net.corda.sdk.preinstall.kafka.KafkaAdmin
import net.corda.sdk.preinstall.kafka.KafkaProperties
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.Node
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KafkaCheckerTest {

    @Test
    fun testKafkaFileParsing() {
        val path = "./src/test/resources/preinstall/KafkaTestBadConnection.yaml"
        val kafkaChecker = KafkaChecker(path)
        val ret = kafkaChecker.check()

        assertEquals(0, ret)
        assertTrue(kafkaChecker.report.toString().contains("Parse Kafka properties from YAML: PASSED"))
    }

    @Test
    fun testKafkaSaslSslNonPemTruststore() {
        // Test SASL_SSL with non-PEM format truststore
        val path = "./src/test/resources/preinstall/KafkaTestSaslTls.yaml"
        val kafkaChecker = KafkaChecker(path)
        val yaml: CordaValues = kafkaChecker.parseYaml(path)
        val props = KafkaProperties(yaml.kafka.bootstrapServers!!)
        props.saslEnabled = yaml.kafka.sasl?.enabled ?: false
        props.saslUsername = "sasl-user"
        props.saslPassword = "sasl-pass"
        props.saslMechanism = yaml.kafka.sasl?.mechanism
        props.tlsEnabled = yaml.kafka.tls!!.enabled
        props.truststoreFile = "-----BEGIN CERTIFICATE-----"
        props.truststorePassword = "truststore-pass"
        props.truststoreType = yaml.kafka.tls!!.truststore!!.type

        val check = props.getKafkaProperties()

        assertEquals("SASL_SSL", check.getProperty("security.protocol"))
        assertEquals("-----BEGIN CERTIFICATE-----", check.getProperty("ssl.truststore.certificates"))
        assertEquals("PLAIN", check.getProperty("sasl.mechanism"))
        assertEquals(
            "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=\"sasl-user\" password=\"sasl-pass\" ;",
            check.getProperty("sasl.jaas.config")
        )
        assertEquals("localhost:9093", check.getProperty("bootstrap.servers"))
        assertEquals("truststore-pass", check.getProperty("ssl.truststore.password"))
        assertEquals("JKS", check.getProperty("ssl.truststore.type"))
    }

    @Test
    fun testKafkaSaslSslPemTruststore() {
        // Test SASL_SSL with PEM format truststore (i.e. no password required)
        val path = "./src/test/resources/preinstall/KafkaTestSaslTlsPEM.yaml"
        val kafkaChecker = KafkaChecker(path)
        val yaml = kafkaChecker.parseYaml<CordaValues>(path)
        val props = KafkaProperties(yaml.kafka.bootstrapServers!!)
        props.saslEnabled = yaml.kafka.sasl?.enabled ?: false
        props.saslUsername = "sasl-user1"
        props.saslPassword = "sasl-pass2"
        props.saslMechanism = yaml.kafka.sasl?.mechanism
        props.tlsEnabled = yaml.kafka.tls!!.enabled
        props.truststoreFile = "-----BEGIN CERTIFICATE-----"
        props.truststoreType = yaml.kafka.tls!!.truststore!!.type

        val check = props.getKafkaProperties()

        assertEquals("SASL_SSL", check.getProperty("security.protocol"))
        assertEquals("-----BEGIN CERTIFICATE-----", check.getProperty("ssl.truststore.certificates"))
        assertEquals("PLAIN", check.getProperty("sasl.mechanism"))
        assertEquals(
            "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=\"sasl-user1\" password=\"sasl-pass2\" ;",
            check.getProperty("sasl.jaas.config")
        )
        assertEquals("localhost:9093", check.getProperty("bootstrap.servers"))
        assertEquals("PEM", check.getProperty("ssl.truststore.type"))
    }

    @Test
    fun testKafkaSaslPlain() {
        // Test SASL_PLAINTEXT
        val path = "./src/test/resources/preinstall/KafkaTestSaslPlain.yaml"
        val kafkaChecker = KafkaChecker(path)
        val yaml = kafkaChecker.parseYaml<CordaValues>(path)
        val props = KafkaProperties(yaml.kafka.bootstrapServers!!)
        props.saslEnabled = yaml.kafka.sasl?.enabled ?: false
        props.saslUsername = "sasl-user"
        props.saslPassword = "sasl-pass"
        props.saslMechanism = yaml.kafka.sasl?.mechanism

        assertThrows<KafkaProperties.SaslPlainWithoutTlsException> {
            props.getKafkaProperties()
        }
    }

    @Test
    fun testKafkaSaslScram() {
        // Test SASL_PLAINTEXT
        val path = "./src/test/resources/preinstall/KafkaTestSaslScram.yaml"
        val kafkaChecker = KafkaChecker(path)
        val yaml = kafkaChecker.parseYaml<CordaValues>(path)
        val props = KafkaProperties(yaml.kafka.bootstrapServers!!)
        props.saslEnabled = yaml.kafka.sasl?.enabled ?: false
        props.saslUsername = "sasl-user"
        props.saslPassword = "sasl-pass"
        props.saslMechanism = yaml.kafka.sasl?.mechanism

        val check = props.getKafkaProperties()

        assertEquals("SASL_PLAINTEXT", check.getProperty("security.protocol"))
        assertEquals("SCRAM", check.getProperty("sasl.mechanism"))
        assertEquals(
            "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                "username=\"sasl-user\" password=\"sasl-pass\" ;",
            check.getProperty("sasl.jaas.config")
        )
        assertEquals("localhost:9093", check.getProperty("bootstrap.servers"))
    }

    @Test
    fun testKafkaSsl() {
        // Test SSL
        val path = "./src/test/resources/preinstall/KafkaTestTls.yaml"
        val kafkaChecker = KafkaChecker(path)
        val yaml = kafkaChecker.parseYaml<CordaValues>(path)
        val props = KafkaProperties(yaml.kafka.bootstrapServers!!)
        props.tlsEnabled = yaml.kafka.tls!!.enabled
        props.truststoreFile = "-----BEGIN CERTIFICATE-----"
        props.truststorePassword = "truststore-pass"
        props.truststoreType = yaml.kafka.tls!!.truststore!!.type

        val check = props.getKafkaProperties()

        assertEquals("SSL", check.getProperty("security.protocol"))
        assertEquals("-----BEGIN CERTIFICATE-----", check.getProperty("ssl.truststore.certificates"))
        assertEquals("localhost:9093", check.getProperty("bootstrap.servers"))
        assertEquals("truststore-pass", check.getProperty("ssl.truststore.password"))
        assertEquals("PKCS12", check.getProperty("ssl.truststore.type"))
    }

    @Test
    fun testKafkaConnect() {
        val mockAdmin = mock<KafkaAdmin>()
        val nodes: Collection<Node> = listOf(Node(0, "localhost", 9092), Node(1, "localhost", 9093))
        whenever(mockAdmin.getDescriptionID()).thenReturn("ClusterID")
        whenever(mockAdmin.getNodes()).thenReturn(nodes)

        val kafkaChecker = KafkaChecker("")
        kafkaChecker.checkConnectionAndBrokers("foo", mockAdmin, 2)

        assertTrue(kafkaChecker.report.toString().contains("Connect to Kafka cluster using foo client: PASSED"))
    }

    @Test
    fun testKafkaConnectBrokersLessThanReplicas() {
        val mockAdmin = mock<KafkaAdmin>()
        val nodes: Collection<Node> = listOf(Node(0, "localhost", 9092))
        whenever(mockAdmin.getDescriptionID()).thenReturn("ClusterID")
        whenever(mockAdmin.getNodes()).thenReturn(nodes)

        val kafkaChecker = KafkaChecker("")
        kafkaChecker.checkConnectionAndBrokers("foo", mockAdmin, 2)

        assertTrue(kafkaChecker.report.toString().contains("Kafka replica count is less than or equal to the broker count: FAILED"))
    }

    @Test
    fun testKafkaConnectFails() {
        val path = "./src/test/resources/preinstall/KafkaTestBadConnection.yaml"
        val kafkaChecker = KafkaChecker(path)
        val yaml = kafkaChecker.parseYaml<CordaValues>(path)
        val props = KafkaProperties(yaml.kafka.bootstrapServers!!)
        props.saslEnabled = yaml.kafka.sasl?.enabled ?: false
        props.saslUsername = "sasl-user"
        props.saslPassword = "sasl-pass"
        props.saslMechanism = yaml.kafka.sasl?.mechanism
        props.tlsEnabled = yaml.kafka.tls!!.enabled
        props.truststoreFile = "-----BEGIN CERTIFICATE-----"
        props.truststorePassword = "truststore-pass"
        props.truststoreType = yaml.kafka.tls!!.truststore!!.type

        val config = props.getKafkaProperties()
        config["default.api.timeout.ms"] = 1

        assertThrows<KafkaException> {
            val client = AdminClient.create(config)
            client.describeCluster().clusterId().get()
        }
    }

    @Test
    fun testGetCredentialsPrefersWorkerValues() {
        val kafkaChecker = KafkaChecker("")
        val defaultValues = SecretValues(
            ValueFrom(SecretKeyRef("defaultKey", "defaultName")),
            "defaultValue"
        )
        val workerValues = SecretValues(
            ValueFrom(SecretKeyRef("workerKey", "")),
            "workerValue"
        )
        val credential = kafkaChecker.getCredential(defaultValues, workerValues, "namespace")

        assertEquals("workerValue", credential)
    }

    @Test
    fun testKafkaTlsWithNoTruststore() {
        val path = "./src/test/resources/preinstall/KafkaTestTlsWithNoTruststore.yaml"
        val kafkaChecker = KafkaChecker(path)
        val yaml = kafkaChecker.parseYaml<CordaValues>(path)
        kafkaChecker.getKafkaProperties(yaml)

        assertTrue(kafkaChecker.report.testsPassed())
    }
}
