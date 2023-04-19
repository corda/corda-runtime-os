package net.corda.cli.plugins.preinstall

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceBuilder
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import picocli.CommandLine


class CheckSubCommands {
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
    inner class TestPostgresKafka {
        private lateinit var client: KubernetesClient
        private lateinit var namespace: Namespace

        @AfterEach
        fun cleanUp() {
            client.namespaces().resource(namespace).delete()
        }

        @Test
        fun testPostgresParseCredentials() {
            val name = "corda-preinstall-plugin-test-namespace-postgres"
            client = KubernetesClientBuilder().build()
            namespace = NamespaceBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .build()
            client.namespaces().resource(namespace).create()
            val secret = SecretBuilder()
                .withNewMetadata().withName("postgres").endMetadata()
                .addToData("postgres-password", "dGVzdC1wYXNzd29yZA==")
                .build()
            client.secrets().inNamespace(name).resource(secret).create()

            val path = "./src/test/resources/PostgresTest.yaml"
            val postgresCMD = CommandLine(CheckPostgres())
            val outText = tapSystemOutNormalized { postgresCMD.execute(path, "-n$name") }

            // if we reach this point, it means we have thrown an SQL exception, and all else before has passed.
            assertTrue(outText.contains("[ERROR] Postgres DB connection unsuccessful: "))
        }

        @Test
        fun testKafkaParseCredentials() {
            val name = "corda-preinstall-plugin-test-namespace-kafka"
            client = KubernetesClientBuilder().build()
            namespace = NamespaceBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .build()
            client.namespaces().resource(namespace).create()
            val secret1 = SecretBuilder()
                .withNewMetadata().withName("kafka-sasl").endMetadata()
                .addToData("kafka-sasl-password", "dGVzdC1wYXNzd29yZA==")
                .build()
            val secret2 = SecretBuilder()
                .withNewMetadata().withName("kafka-truststore-password").endMetadata()
                .addToData("kafka-truststore-password", "dGVzdC1wYXNzd29yZA==")
                .build()
            client.secrets().inNamespace(name).resource(secret1).create()
            client.secrets().inNamespace(name).resource(secret2).create()

            val path = "./src/test/resources/KafkaTest.yaml"
            val kafkaCMD = CommandLine(CheckKafka())

            val outText = tapSystemOutNormalized { kafkaCMD.execute(path, "-n$name", "-fdummy-teststore.jks") }
            println(outText)
            assertTrue(outText.contains("Failed to load SSL keystore dummy-teststore.jks"))
        }
    }
}