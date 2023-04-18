package net.corda.cli.plugins.preinstall

import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceBuilder
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import net.corda.cli.plugins.preinstall.PreInstallPlugin.PluginContext
import net.corda.cli.plugins.preinstall.PreInstallPlugin.SecretValues
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import picocli.CommandLine


@CommandLine.Command(name = "sub-command", description = ["Example subcommand."])
class CheckSecretsTests: PluginContext() {

    @Nested
    inner class TestSecrets: PluginContext() {
        private lateinit var client: KubernetesClient
        private lateinit var namespace: Namespace

        @AfterEach
        fun cleanUp() {
            client.namespaces().resource(namespace).delete()
        }

        @Test
        fun testSecrets() {
            client = KubernetesClientBuilder().build()
            namespace = NamespaceBuilder()
                .withNewMetadata().withName("corda-preinstall-plugin-test-namespace").endMetadata()
                .build()
            client.namespaces().resource(namespace).create()
            val secret = SecretBuilder()
                .withNewMetadata().withName("secret").endMetadata()
                .addToData("password", "dGVzdC1wYXNzd29yZA==")
                .build()
            client.secrets().inNamespace("corda-preinstall-plugin-test-namespace").resource(secret).create()

            val secretKeyRef = SecretValues(null, null, null, "password", "secret")
            val valueFrom = SecretValues(null, secretKeyRef, null, null, null)
            val values = SecretValues(valueFrom, null, null, null, null)

            val credential = getCredentialOrSecret(values, "corda-preinstall-plugin-test-namespace")

            assertEquals(credential, "test-password")
        }
    }

    @Test
    fun testCredentials() {
        val values = SecretValues(null, null, "test-password", null, null)
        val credential = getCredentialOrSecret(values, "corda-preinstall-plugin-test-namespace")

        assertEquals(credential, "test-password")
    }
}