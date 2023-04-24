package net.corda.cli.plugins.preinstall

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized
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
    fun testNoFile() {
        val limitsCMD = CommandLine(CheckLimits())
        var outText = tapSystemOutNormalized { limitsCMD.execute("this-file-does-not-exist", "-v") }
        assertTrue( outText.contains("[ERROR] File does not exist") )

        val postgresCMD = CommandLine(CheckPostgres())
        outText = tapSystemOutNormalized { postgresCMD.execute("this-file-does-not-exist", "-nnamespace") }
        assertTrue( outText.contains("[ERROR] File does not exist") )

        val kafkaCMD = CommandLine(CheckKafka())
        outText = tapSystemOutNormalized { kafkaCMD.execute("this-file-does-not-exist", "-nnamespace", "-ftruststore.jks") }
        assertTrue( outText.contains("[ERROR] File does not exist") )
    }

    @Test
    fun testLimitsParser() {
        var path = "./src/test/resources/LimitsTest0.yaml"
        val limitsCMD = CommandLine(CheckLimits())

        var outText = tapSystemOutNormalized { limitsCMD.execute(path) }
        assertTrue( outText.contains("[INFO] All resource requests are appropriate and are under the set limits.") )

        path = "./src/test/resources/LimitsTest1.yaml"

        outText = tapSystemOutNormalized { limitsCMD.execute(path) }
        assertTrue( outText.contains("[ERROR] Resource requests for resources have been exceeded!") )

        path = "./src/test/resources/LimitsTest2.yaml"

        outText = tapSystemOutNormalized { limitsCMD.execute(path) }
        assertTrue( outText.contains("[ERROR] Invalid memory string format:") )

    }

    @Nested
    inner class TestKafka : PreInstallPlugin.PluginContext() {
        @Test
        fun testKafkaProperties() {
            var path = "./src/test/resources/KafkaTest0.yaml"
            var yaml: PreInstallPlugin.Kafka = parseYaml<PreInstallPlugin.Kafka>(path)!!
            var check = CheckKafka().getKafkaProperties(yaml, "sasl-user", "sasl-pass",
                "truststore-pass", "/test/location")!!

            assertEquals("SASL_SSL", check.getProperty("security.protocol"))
            assertEquals("/test/location", check.getProperty("ssl.truststore.location"))
            assertEquals("PLAIN", check.getProperty("sasl.mechanism"))
            assertEquals("org.apache.kafka.common.security.plain.PlainLoginModule required " +
                    "username=\"sasl-user\" password=\"sasl-pass\" ;", check.getProperty("sasl.jaas.config"))
            assertEquals("localhost:9093", check.getProperty("bootstrap.servers"))
            assertEquals("truststore-pass", check.getProperty("ssl.truststore.password"))
            assertEquals("JKS", check.getProperty("ssl.truststore.type"))

            path = "./src/test/resources/KafkaTest1.yaml"
            yaml = parseYaml<PreInstallPlugin.Kafka>(path)!!
            check = CheckKafka().getKafkaProperties(yaml, "sasl-user", "sasl-pass",
                "truststore-pass", "/test/location")!!

            assertEquals("SASL_PLAINTEXT", check.getProperty("security.protocol"))
            assertEquals("SCRAM", check.getProperty("sasl.mechanism"))
            assertEquals("org.apache.kafka.common.security.scram.ScramLoginModule required " +
                    "username=\"sasl-user\" password=\"sasl-pass\" ;", check.getProperty("sasl.jaas.config"))
            assertEquals("localhost:9093", check.getProperty("bootstrap.servers"))

            path = "./src/test/resources/KafkaTest2.yaml"
            yaml = parseYaml<PreInstallPlugin.Kafka>(path)!!
            check = CheckKafka().getKafkaProperties(yaml, "", "",
                "truststore-pass", "/test/location")!!

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
            val outText = tapSystemOutNormalized { ck.connect(mockAdmin) }
            assertTrue( outText.contains("[INFO] Kafka client connected to cluster with ID ClusterID.") )
            assertTrue( outText.contains("[INFO] Number of brokers: 2") )
        }
    }
}