package net.corda.cli.plugins.preinstall

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import net.corda.cli.api.AbstractCordaCliVersionProvider
import java.io.File
import java.io.FileNotFoundException
import java.util.Base64
import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class VersionProvider : AbstractCordaCliVersionProvider()

class PreInstallPlugin : Plugin() {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        logger.debug("starting preinstall plugin")
    }

    override fun stop() {
        logger.debug("stopping preinstall plugin")
    }

    @Extension
    @CommandLine.Command(name = "preinstall",
        subcommands = [CheckLimits::class, CheckPostgres::class, CheckKafka::class, RunAll::class],
        mixinStandardHelpOptions = true,
        description = ["Preinstall checks for Corda."],
        versionProvider = VersionProvider::class)
    class PreInstallPluginEntry : CordaCliPlugin

    // Common class for plugins to inherit methods from
    open class PluginContext {
        var report = Report()
        private var client: KubernetesClient = KubernetesClientBuilder().build()
        var logger = PreInstallPlugin.logger

        class SecretException: Exception {
            constructor (message: String?) : super(message)
            constructor (message: String?, cause: Throwable?) : super(message, cause)
        }

        // parse a yaml file, and return an object of type T or null if there was an error
        inline fun <reified T> parseYaml(path: String): T {
            val file = File(path)

            if (!file.isFile) {
                throw FileNotFoundException("Could not read file at $path.")
            }

            val mapper: ObjectMapper = YAMLMapper()
            return mapper.readValue(file, T::class.java)
        }

        // get the credentials (.value) or credentials from a secret (.valueFrom.secretKeyRef...) from a SecretValues
        // object (or default values), and a namespace (if the credential is in a secret)
        @Suppress("ThrowsCount")
        fun getCredential(defaultValues: SecretValues?, values: SecretValues?, namespace: String?): String {
            if (!values?.valueFrom?.secretKeyRef?.name.isNullOrEmpty()) {
                return getSecret(values?.valueFrom?.secretKeyRef?.name!!, values.valueFrom.secretKeyRef.key, namespace)
            }
            if (!values?.value.isNullOrEmpty()) {
                return values?.value!!
            }
            return getCredential(defaultValues, namespace)
        }

        // get the credentials (.value) or credentials from a secret (.valueFrom.secretKeyRef...) from a SecretValues
        // object, and a namespace (if the credential is in a secret)
        @Suppress("ThrowsCount")
        fun getCredential(values: SecretValues?, namespace: String?): String {
            val secretName = values?.valueFrom?.secretKeyRef?.name
            if (secretName.isNullOrEmpty())  {
                val credential = values?.value
                if (credential.isNullOrEmpty()) {
                    throw SecretException("No secretKeyRef name or value provided.")
                }
                return credential
            }
            return getSecret(secretName, values.valueFrom.secretKeyRef.key, namespace)
        }

        @Suppress("ThrowsCount")
        private fun getSecret(secretName: String, secretKey: String?, namespace: String?): String {
            if (secretKey.isNullOrEmpty()) {
                throw SecretException("No secret key provided with secret name $secretName.")
            }
            return try {
                val secret: Secret? = if (namespace != null) {
                    checkNamespace(namespace)
                    client.secrets().inNamespace(namespace).withName(secretName).get()
                } else {
                    client.secrets().withName(secretName).get()
                }
                if (secret == null) {
                    throw SecretException("Secret $secretName not found.")
                }
                val encoded = secret.data[secretKey] ?: throw SecretException("Secret $secretName has no key $secretKey.")
                String(Base64.getDecoder().decode(encoded))
            } catch (e: KubernetesClientException) {
                throw SecretException("Could not read secret $secretName with key $secretKey.", e)
            }
        }

        private fun checkNamespace(namespace: String) {
            val names = client.namespaces().list().items.map { item -> item.metadata.name}
            if (!names.contains(namespace)) {
                throw SecretException("Namespace $namespace does not exist.")
            }
        }
    }

    class ReportEntry(private var check: String, var result: Boolean, var reason: Exception? = null) {
        override fun toString(): String {
            var entry = "$check: ${if (result) "PASSED" else "FAILED"}"
            if (!result) {
                reason?.message?.let { entry += "\n\t - $it" }
                reason?.cause?.let { entry += "\n\t - $it" }
            }
            return entry
        }
    }

    class Report (private var entries: MutableList<ReportEntry> = mutableListOf()) {
        private fun getEntries(): MutableList<ReportEntry> {
            return entries
        }

        fun addEntry(entry: ReportEntry) {
            entries.add(entry)
        }

        fun addEntries(newEntries: List<ReportEntry>) {
            entries.addAll(newEntries)
        }

        fun addEntries(reportEntries: Report) {
            entries.addAll(reportEntries.getEntries())
        }

        // Fails if any of the report entries have failed. If no entries are found, the report passes.
        fun testsPassed(): Boolean {
            return entries.all { it.result }
        }

        fun failingTests(): String {
            return entries.filter { !it.result }.joinToString(separator = "\n")
        }

        override fun toString(): String {
            return entries.joinToString(separator = "\n")
        }
    }

    /* Jackson classes for yaml parsing */

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class WorkerKafka(
        @JsonProperty("sasl")
        val sasl: ClientSASL?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Kafka(
        @JsonProperty("bootstrapServers")
        val bootstrapServers: String?,
        @JsonProperty("tls")
        val tls: TLS?,
        @JsonProperty("sasl")
        val sasl: SASL?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TLS(
        @JsonProperty("enabled")
        val enabled: Boolean,
        @JsonProperty("truststore")
        val truststore: Truststore?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Truststore(
        @JsonProperty("valueFrom")
        val valueFrom: ValueFrom?,
        @JsonProperty("type")
        val type: String,
        @JsonProperty("password")
        val password: SecretValues?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SASL(
        @JsonProperty("enabled")
        val enabled: Boolean,
        @JsonProperty("mechanism")
        val mechanism: String?,
        @JsonProperty("username")
        val username: SecretValues?,
        @JsonProperty("password")
        val password: SecretValues?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ClientSASL(
        @JsonProperty("username")
        val username: SecretValues?,
        @JsonProperty("password")
        val password: SecretValues?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BootstrapKafka(
        @JsonProperty("enabled")
        val enabled: Boolean,
        @JsonProperty("replicas")
        val replicas: Int?,
        @JsonProperty("sasl")
        val sasl: ClientSASL
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CordaValues(
        @JsonProperty("bootstrap")
        val bootstrap: Bootstrap,
        @JsonProperty("config")
        val config: PersistentStorage,
        @JsonProperty("databases")
        val databases: List<Database>,
        @JsonProperty("kafka")
        val kafka: Kafka,
        @JsonProperty("resources")
        val resources: Resources?,
        @JsonProperty("stateManager")
        val stateManager: Map<String, PersistentStorage>,
        @JsonProperty("workers")
        val workers: Map<String, Worker>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Database(
        @JsonProperty("id")
        val id: String,
        @JsonProperty("host")
        val host: String,
        @JsonProperty("port")
        val port: Int? = 5432,
        @JsonProperty("name")
        val name: String? = "cordacluster"
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Bootstrap(
        @JsonProperty("db")
        val db: BootstrapDb?,
        @JsonProperty("kafka")
        val kafka: BootstrapKafka?,
        @JsonProperty("resources")
        val resources: Resources?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PersistentStorage(
        @JsonProperty("storageId")
        val storageId: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BootstrapDb(
        @JsonProperty("enabled")
        val enabled: Boolean,
        @JsonProperty("databases")
        val databases: List<BootstrapDatabase>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BootstrapDatabase(
        @JsonProperty("id")
        val id: String,
        @JsonProperty("username")
        val username: SecretValues?,
        @JsonProperty("password")
        val password: SecretValues?
    )

    data class SecretValues(
        @JsonProperty("valueFrom")
        val valueFrom: ValueFrom?,
        @JsonProperty("value")
        val value: String?,
    )

    data class ValueFrom(
        @JsonProperty("secretKeyRef")
        val secretKeyRef: SecretKeyRef?
    )

    data class SecretKeyRef(
        @JsonProperty("key")
        val key: String?,
        @JsonProperty("name")
        val name: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Worker(
        @JsonProperty("config")
        val config: Credentials?,
        @JsonProperty("resources")
        val resources: Resources?,
        @JsonProperty("kafka")
        val kafka: WorkerKafka?,
        @JsonProperty("stateManager")
        val stateManager: Map<String, Credentials>?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Credentials(
        @JsonProperty("username")
        val username: SecretValues?,
        @JsonProperty("password")
        val password: SecretValues?
    )

    data class Resources(
        @JsonProperty("requests")
        val requests: ResourceValues?,
        @JsonProperty("limits")
        val limits: ResourceValues?
    )

    data class ResourceValues(
        @JsonProperty("memory")
        var memory: String?,
        @JsonProperty("cpu")
        var cpu: String?
    )
}
