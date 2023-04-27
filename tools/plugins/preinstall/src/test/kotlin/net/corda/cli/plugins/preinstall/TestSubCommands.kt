package net.corda.cli.plugins.preinstall

import net.corda.cli.plugins.preinstall.PreInstallPlugin.ReportEntry
import net.corda.cli.plugins.preinstall.PreInstallPlugin.Report
import org.apache.kafka.common.Node
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import picocli.CommandLine


class TestSubCommands {
    @Test
    fun testFileParsing() {
        var path = "./src/test/resources/LimitsTestUnderLimits.yaml"
        val limits = CheckLimits()
        CommandLine(limits).execute(path)

        println(limits.report)

        assertTrue(limits.report.toString().contains("Parse resource properties from YAML: PASSED"))

        path = "./src/test/resources/PostgresTest.yaml"
        val postgres = CheckPostgres()
        CommandLine(postgres).execute(path)

        println(postgres.report)

        assertTrue(postgres.report.toString().contains("Parse PostgreSQL properties from YAML: PASSED"))

        path = "./src/test/resources/KafkaTestSasl.yaml"
        val kafka = CheckKafka()
        CommandLine(kafka).execute(path)

        println(kafka.report)

        assertTrue(kafka.report.toString().contains("Parse Kafka properties from YAML: PASSED"))
    }

    @Test
    fun testLimitsParser() {
        var path = "./src/test/resources/LimitsTestUnderLimits.yaml"
        var limits = CheckLimits()
        var result: Int = CommandLine(limits).execute(path)

        assertTrue(limits.report.toString().contains("bootstrap requests do not exceed limits: PASSED"))
        assertEquals(0, result)

        path = "./src/test/resources/LimitsTestOverLimits.yaml"
        limits = CheckLimits()
        result = CommandLine(limits).execute(path)

        assertTrue(limits.report.toString().contains("resources requests do not exceed limits: FAILED"))
        assertEquals(1, result)

        path = "./src/test/resources/LimitsTestBadValues.yaml"
        limits = CheckLimits()
        result = CommandLine(limits).execute(path)

        assertTrue(limits.report.toString().contains("Parse resource strings: FAILED"))
        assertEquals(1, result)
    }

    @Nested
    inner class TestKafka : PreInstallPlugin.PluginContext() {
        @Test
        fun testKafkaProperties() {
            // Test SASL_SSL with non-PEM format truststore
            var path = "./src/test/resources/KafkaTestSaslTls.yaml"
            var yaml: PreInstallPlugin.Kafka = parseYaml<PreInstallPlugin.Kafka>(path)
            var props = CheckKafka.KafkaProperties(yaml)
            props.saslUsername = "sasl-user"
            props.saslPassword = "sasl-pass"
            props.truststorePassword = "truststore-pass"
            props.truststoreLocation = "/test/location"

            var check = props.getKafkaProperties()

            assertEquals("SASL_SSL", check.getProperty("security.protocol"))
            assertEquals("/test/location", check.getProperty("ssl.truststore.location"))
            assertEquals("PLAIN", check.getProperty("sasl.mechanism"))
            assertEquals("org.apache.kafka.common.security.plain.PlainLoginModule required " +
                    "username=\"sasl-user\" password=\"sasl-pass\" ;", check.getProperty("sasl.jaas.config"))
            assertEquals("localhost:9093", check.getProperty("bootstrap.servers"))
            assertEquals("truststore-pass", check.getProperty("ssl.truststore.password"))
            assertEquals("JKS", check.getProperty("ssl.truststore.type"))

            // Test SASL_SSL with PEM format truststore (i.e. no password required)
            path = "./src/test/resources/KafkaTestSaslTlsPEM.yaml"
            yaml = parseYaml<PreInstallPlugin.Kafka>(path)
            props = CheckKafka.KafkaProperties(yaml)
            props.saslUsername = "sasl-user1"
            props.saslPassword = "sasl-pass2"
            props.truststoreFile = "-----BEGIN CERTIFICATE-----"

            check = props.getKafkaProperties()

            assertEquals("SASL_SSL", check.getProperty("security.protocol"))
            assertEquals("-----BEGIN CERTIFICATE-----", check.getProperty("ssl.truststore.certificates"))
            assertEquals("PLAIN", check.getProperty("sasl.mechanism"))
            assertEquals("org.apache.kafka.common.security.plain.PlainLoginModule required " +
                    "username=\"sasl-user1\" password=\"sasl-pass2\" ;", check.getProperty("sasl.jaas.config"))
            assertEquals("localhost:9093", check.getProperty("bootstrap.servers"))
            assertEquals("PEM", check.getProperty("ssl.truststore.type"))

            // Test SASL_PLAINTEXT
            path = "./src/test/resources/KafkaTestSasl.yaml"
            yaml = parseYaml<PreInstallPlugin.Kafka>(path)
            props = CheckKafka.KafkaProperties(yaml)
            props.saslUsername = "sasl-user"
            props.saslPassword = "sasl-pass"

            check = props.getKafkaProperties()

            assertEquals("SASL_PLAINTEXT", check.getProperty("security.protocol"))
            assertEquals("SCRAM", check.getProperty("sasl.mechanism"))
            assertEquals("org.apache.kafka.common.security.scram.ScramLoginModule required " +
                    "username=\"sasl-user\" password=\"sasl-pass\" ;", check.getProperty("sasl.jaas.config"))
            assertEquals("localhost:9093", check.getProperty("bootstrap.servers"))

            // Test SSL
            path = "./src/test/resources/KafkaTestTls.yaml"
            yaml = parseYaml<PreInstallPlugin.Kafka>(path)
            props = CheckKafka.KafkaProperties(yaml)
            props.truststorePassword = "truststore-pass"
            props.truststoreLocation = "/test/location"

            check = props.getKafkaProperties()

            assertEquals("SSL", check.getProperty("security.protocol"))
            assertEquals("/test/location", check.getProperty("ssl.truststore.location"))
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
            ck.register(verbose=true, debug=false)
            ck.connect(mockAdmin, 2)

            assertTrue( ck.report.toString().contains("Connect to Kafka cluster using client: PASSED") )
        }
    }

    @Test
    fun testReports() {
        val report = Report()

        report.addEntries(mutableListOf(ReportEntry("Doesn't crash", true), ReportEntry("No bugs", true)))
        assertEquals(0, report.testsPassed())

        val anotherReport = Report(mutableListOf(ReportEntry("Can combine with other reports", true)))
        report.addEntries(anotherReport)
        assertEquals(0, report.testsPassed())

        report.addEntry(ReportEntry("Is magical", false, Exception("Not magic")))
        assertEquals(1, report.testsPassed())

        println(report)

        if (report.testsPassed() == 1) {
            println(report.failingTests())
        }
    }
}