package net.corda.cli.plugins.preinstall

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import java.io.File
import java.io.FileNotFoundException
import java.util.Base64
import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

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
        description = ["Preinstall checks for Corda."])
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
            if (!defaultValues?.valueFrom?.secretKeyRef?.name.isNullOrEmpty()) {
                return getSecret(defaultValues?.valueFrom?.secretKeyRef?.name!!, defaultValues.valueFrom.secretKeyRef.key, namespace)
            }
            if (!defaultValues?.value.isNullOrEmpty()) {
                return defaultValues?.value!!
            }
            throw SecretException("No secretKeyRef name or value provided.")
        }

        // get the credentials (.value) or credentials from a secret (.valueFrom.secretKeyRef...) from a SecretValues
        // object, and a namespace (if the credential is in a secret)
        @Suppress("ThrowsCount")
        fun getCredential(values: SecretValues, namespace: String?): String {
            val secretName = values.valueFrom?.secretKeyRef?.name
            if (secretName.isNullOrEmpty())  {
                val credential = values.value
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

    //Kafka
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Kafka(
        @JsonProperty("kafka")
        val kafka: KafkaConfiguration,
        @JsonProperty("bootstrap")
        val bootstrap: KafkaBootstrap?,
        @JsonProperty("workers")
        val workers: KafkaWorkers?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class KafkaWorker(
        @JsonProperty("kafka")
        val kafka: KafkaWorkerKafka?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class KafkaWorkerKafka(
        @JsonProperty("sasl")
        val sasl: ClientSASL?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class KafkaConfiguration(
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
    data class KafkaWorkers(
        @JsonProperty("crypto")
        val crypto: KafkaWorker?,
        @JsonProperty("db")
        val db: KafkaWorker?,
        @JsonProperty("evm")
        val evm: KafkaWorker?,
        @JsonProperty("flow")
        val flow: KafkaWorker?,
        @JsonProperty("flowMapper")
        val flowMapper: KafkaWorker?,
        @JsonProperty("verification")
        val verification: KafkaWorker?,
        @JsonProperty("membership")
        val membership: KafkaWorker?,
        @JsonProperty("rest")
        val rest: KafkaWorker?,
        @JsonProperty("p2pLinkManager")
        val p2pLinkManager: KafkaWorker?,
        @JsonProperty("p2pGateway")
        val p2pGateway: KafkaWorker?,
        @JsonProperty("persistence")
        val persistence: KafkaWorker?,
        @JsonProperty("uniqueness")
        val uniqueness: KafkaWorker?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class KafkaBootstrap(
        @JsonProperty("kafka")
        val kafka: KafkaBootstrapConfiguration?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class KafkaBootstrapConfiguration(
        @JsonProperty("enabled")
        val enabled: Boolean,
        @JsonProperty("replicas")
        val replicas: Int?,
        @JsonProperty("sasl")
        val sasl: ClientSASL
    )

    //DB
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DB(
        @JsonProperty("db")
        val db: Cluster,
        @JsonProperty("bootstrap")
        val bootstrap: BootstrapDB?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Cluster(
        @JsonProperty("cluster")
        val cluster: Credentials
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Credentials(
        @JsonProperty("username")
        val username: SecretValues,
        @JsonProperty("password")
        val password: SecretValues,
        @JsonProperty("host")
        val host: String,
        @JsonProperty("port")
        val port: Int? = 5432,
        @JsonProperty("database")
        val database: String? = "cordacluster"
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BootstrapDB(
        @JsonProperty("db")
        val db: BootstrapCluster?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BootstrapCluster(
        @JsonProperty("enabled")
        val enabled: Boolean,
        @JsonProperty("cluster")
        val cluster: BootstrapCredentials?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BootstrapCredentials(
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

    //Resource
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Configurations(
        @JsonProperty("bootstrap")
        val bootstrap: Resources?,
        @JsonProperty("workers")
        val workers: Workers?,
        @JsonProperty("resources")
        val resources: ResourceConfig?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Workers(
        @JsonProperty("crypto")
        val crypto: Resources?,
        @JsonProperty("db")
        val db: Resources?,
        @JsonProperty("flow")
        val flow: Resources?,
        @JsonProperty("flowMapper")
        val flowMapper: Resources?,
        @JsonProperty("verification")
        val verification: Resources?,
        @JsonProperty("membership")
        val membership: Resources?,
        @JsonProperty("rest")
        val rest: Resources?,
        @JsonProperty("p2pLinkManager")
        val p2pLinkManager: Resources?,
        @JsonProperty("p2pGateway")
        val p2pGateway: Resources?,
        @JsonProperty("persistence")
        val persistence: Resources?,
        @JsonProperty("uniqueness")
        val uniqueness: Resources?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Resources(
        @JsonProperty("resources")
        val resources: ResourceConfig?
    )

    data class ResourceConfig(
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
