package net.corda.cli.plugins.preinstall

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.Node
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import picocli.CommandLine


class CheckKafkaTest {

    @Test
    fun testKafkaFileParsing() {
        val path = "./src/test/resources/KafkaTestBadConnection.yaml"
        val kafka = CheckKafka()
        CommandLine(kafka).execute(path)

        assertTrue(kafka.report.toString().contains("Parse Kafka properties from YAML: PASSED"))
    }

    @Test
    fun testKafkaSaslSslNonPemTruststore() {
        // Test SASL_SSL with non-PEM format truststore
        val kafka = CheckKafka()
        val path = "./src/test/resources/KafkaTestSaslTls.yaml"
        val yaml: PreInstallPlugin.CordaValues = kafka.parseYaml(path)
        val props = CheckKafka.KafkaProperties(yaml.kafka.bootstrapServers!!)
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
        assertEquals("org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=\"sasl-user\" password=\"sasl-pass\" ;", check.getProperty("sasl.jaas.config"))
        assertEquals("localhost:9093", check.getProperty("bootstrap.servers"))
        assertEquals("truststore-pass", check.getProperty("ssl.truststore.password"))
        assertEquals("JKS", check.getProperty("ssl.truststore.type"))
    }

    @Test
    fun testKafkaSaslSslPemTruststore() {
        // Test SASL_SSL with PEM format truststore (i.e. no password required)
        val kafka = CheckKafka()
        val path = "./src/test/resources/KafkaTestSaslTlsPEM.yaml"
        val yaml = kafka.parseYaml<PreInstallPlugin.CordaValues>(path)
        val props = CheckKafka.KafkaProperties(yaml.kafka.bootstrapServers!!)
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
        assertEquals("org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=\"sasl-user1\" password=\"sasl-pass2\" ;", check.getProperty("sasl.jaas.config"))
        assertEquals("localhost:9093", check.getProperty("bootstrap.servers"))
        assertEquals("PEM", check.getProperty("ssl.truststore.type"))
    }

    @Test
    fun testKafkaSaslPlain() {
        // Test SASL_PLAINTEXT
        val kafka = CheckKafka()
        val path = "./src/test/resources/KafkaTestSaslPlain.yaml"
        val yaml = kafka.parseYaml<PreInstallPlugin.CordaValues>(path)
        val props = CheckKafka.KafkaProperties(yaml.kafka.bootstrapServers!!)
        props.saslEnabled = yaml.kafka.sasl?.enabled ?: false
        props.saslUsername = "sasl-user"
        props.saslPassword = "sasl-pass"
        props.saslMechanism = yaml.kafka.sasl?.mechanism

        assertThrows<CheckKafka.KafkaProperties.SaslPlainWithoutTlsException> {
            props.getKafkaProperties()
        }
    }

    @Test
    fun testKafkaSaslScram() {
        // Test SASL_PLAINTEXT
        val kafka = CheckKafka()
        val path = "./src/test/resources/KafkaTestSaslScram.yaml"
        val yaml = kafka.parseYaml<PreInstallPlugin.CordaValues>(path)
        val props = CheckKafka.KafkaProperties(yaml.kafka.bootstrapServers!!)
        props.saslEnabled = yaml.kafka.sasl?.enabled ?: false
        props.saslUsername = "sasl-user"
        props.saslPassword = "sasl-pass"
        props.saslMechanism = yaml.kafka.sasl?.mechanism

        val check = props.getKafkaProperties()

        assertEquals("SASL_PLAINTEXT", check.getProperty("security.protocol"))
        assertEquals("SCRAM", check.getProperty("sasl.mechanism"))
        assertEquals("org.apache.kafka.common.security.scram.ScramLoginModule required " +
                "username=\"sasl-user\" password=\"sasl-pass\" ;", check.getProperty("sasl.jaas.config"))
        assertEquals("localhost:9093", check.getProperty("bootstrap.servers"))
    }

    @Test
    fun testKafkaSsl() {
        // Test SSL
        val kafka = CheckKafka()
        val path = "./src/test/resources/KafkaTestTls.yaml"
        val yaml = kafka.parseYaml<PreInstallPlugin.CordaValues>(path)
        val props = CheckKafka.KafkaProperties(yaml.kafka.bootstrapServers!!)
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
        val mockAdmin = mock<CheckKafka.KafkaAdmin>()
        val nodes: Collection<Node> = listOf(Node(0, "localhost", 9092), Node(1, "localhost", 9093))
        whenever(mockAdmin.getDescriptionID()).thenReturn("ClusterID")
        whenever(mockAdmin.getNodes()).thenReturn(nodes)

        val ck = CheckKafka()
        ck.checkConnectionAndBrokers("foo", mockAdmin, 2)

        assertTrue( ck.report.toString().contains("Connect to Kafka cluster using foo client: PASSED") )
    }

    @Test
    fun testKafkaConnectBrokersLessThanReplicas() {
        val mockAdmin = mock<CheckKafka.KafkaAdmin>()
        val nodes: Collection<Node> = listOf(Node(0, "localhost", 9092))
        whenever(mockAdmin.getDescriptionID()).thenReturn("ClusterID")
        whenever(mockAdmin.getNodes()).thenReturn(nodes)

        val ck = CheckKafka()
        ck.checkConnectionAndBrokers("foo", mockAdmin, 2)

        assertTrue( ck.report.toString().contains("Kafka replica count is less than or equal to the broker count: FAILED") )
    }

    @Test
    fun testKafkaConnectFails() {
        val kafka = CheckKafka()
        val path = "./src/test/resources/KafkaTestBadConnection.yaml"
        val yaml = kafka.parseYaml<PreInstallPlugin.CordaValues>(path)
        val props = CheckKafka.KafkaProperties(yaml.kafka.bootstrapServers!!)
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
        val kafka = CheckKafka()
        val defaultValues = PreInstallPlugin.SecretValues(
            PreInstallPlugin.ValueFrom(PreInstallPlugin.SecretKeyRef("defaultKey", "defaultName")),
            "defaultValue"
        )
        val workerValues = PreInstallPlugin.SecretValues(
            PreInstallPlugin.ValueFrom(PreInstallPlugin.SecretKeyRef("workerKey", "")),
            "workerValue"
        )
        val credential = kafka.getCredential(defaultValues, workerValues, "namespace")

        assertEquals("workerValue", credential)
    }

    @Test
    fun testKafkaTlsWithNoTruststore() {
        val kafka = CheckKafka()
        val yaml = kafka.parseYaml<PreInstallPlugin.CordaValues>("./src/test/resources/KafkaTestTlsWithNoTruststore.yaml")
        kafka.getKafkaProperties(yaml)

        assertTrue(kafka.report.testsPassed())
    }

}