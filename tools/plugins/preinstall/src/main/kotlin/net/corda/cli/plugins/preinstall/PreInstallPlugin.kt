package net.corda.cli.plugins.preinstall

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileNotFoundException
import java.util.Base64

class PreInstallPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

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
        description = ["Preinstall checks for corda."])
    class PreInstallPluginEntry : CordaCliPlugin

    // Common class for plugins to inherit methods from
    open class PluginContext {
        private var verbose = false
        private var debug = false

        var report = Report()
        class SecretException: Exception {
            constructor (message: String?) : super(message)
            constructor (message: String?, cause: Throwable?) : super(message, cause)
        }

        companion object LogLevel {
            const val ERROR: Int = 0
            const val INFO: Int = 1
            const val DEBUG: Int = 2
            const val WARN: Int = 3
        }

        fun register(verbose: Boolean, debug: Boolean) {
            this.verbose = verbose
            this.debug = debug
        }

        // used for logging
        fun log(s: String, level: Int) {
            when (level) {
                ERROR -> println("[ERROR] $s")
                INFO -> if (verbose) println("[INFO] $s")
                DEBUG -> if (debug) println("[DEBUG] $s")
                WARN -> println("[WARN] $s")
            }
        }

        // parse a yaml file, and return an object of type T or null if there was an error
        inline fun <reified T> parseYaml(path: String): T {
            log("Working Directory = ${System.getProperty("user.dir")}\n", INFO)

            val file = File(path)

            if (!file.isFile) {
                throw FileNotFoundException("Could not read file at $path.")
            }

            val mapper: ObjectMapper = YAMLMapper()
            return mapper.readValue(file, T::class.java)
        }

        // get the credentials (.value) or credentials from a secret (.valueFrom.secretKeyRef...) from a SecretValues
        // object, and a namespace (if the credential is in a secret)
        fun getCredentialOrSecret(values: SecretValues, namespace: String?, url: String?): String {
            val secretKey: String? = values.valueFrom?.secretKeyRef?.key
            val secretName: String? = values.valueFrom?.secretKeyRef?.name
            var credential: String? = values.value

            credential = credential ?: run {
                if (secretKey == null || secretName == null)  {
                    throw SecretException("Credential secret $secretName with key $secretKey could not be parsed.")
                }
                val encoded = getSecret(secretName, secretKey, namespace, url) ?: run {
                    throw SecretException("Secret $secretName has no key $secretKey.")
                }
                String(Base64.getDecoder().decode(encoded))
            }
            return credential
        }

        private fun getSecret(secretName: String, secretKey: String, namespace: String?, url: String?): String? {
            return try {
                val client: KubernetesClient = if (url != null) {
                    val kubeConfig: Config = ConfigBuilder()
                        .withMasterUrl(url)
                        .build()
                    KubernetesClientBuilder().withConfig(kubeConfig).build()
                } else {
                    KubernetesClientBuilder().build()
                }

                val secret: Secret = if (namespace != null) {
                    val names = client.namespaces().list().items.map { item -> item.metadata.name}
                    if (!names.contains(namespace)) {
                        throw SecretException("Namespace $namespace does not exist.")
                    }
                    client.secrets().inNamespace(namespace).withName(secretName).get()
                } else {
                    client.secrets().withName(secretName).get()
                }
                secret.data[secretKey]
            } catch (e: KubernetesClientException) {
                throw SecretException("Could not read secret $secretName with key $secretKey.", e)
            }
        }
    }

    class ReportEntry(private var check: String, var result: Boolean, var reason: Exception? = null) {
        override fun toString(): String {
            return "$check: ${if (result) "PASSED" else "FAILED"}"
        }
    }

    class Report (private var report: MutableList<ReportEntry> = mutableListOf()) {
        private fun getEntries(): MutableList<ReportEntry> {
            return report
        }

        fun addEntry(entry: ReportEntry) {
            report.add(entry)
            if (!entry.result) { this.failingTests() }
        }

        fun addEntries(entries: List<ReportEntry>) {
            report.addAll(entries)
        }

        fun addEntries(reportEntries: Report) {
            report.addAll(reportEntries.getEntries())
        }

        // Fails (returns 1) if any of the report entries have failed. If no entries are found, the report passes.
        fun testsPassed(): Int {
            val result = report.reduceOrNull { acc, entry -> ReportEntry("", acc.result && entry.result) } ?: return 0

            return if (result.result) 0 else 1
        }

        fun failingTests(): String {
            var acc = ""
            for(entry in report) {
                if (!entry.result) {
                    val message = entry.reason?.message ?: "(No message provided)"
                    val cause = entry.reason?.cause ?: "(No cause provided)"
                    acc += "$entry \n\t - $message \n\t - $cause \n"
                }
            }
            return acc
        }

        override fun toString(): String {
            var acc = ""
            for (entry in report) {
                acc += "$entry\n"
            }
            return acc
        }
    }

    /* Jackson classes for yaml parsing */

    //Kafka
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Kafka(
        @JsonProperty("kafka")
        val kafka: KafkaConfiguration
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class KafkaConfiguration(
        @JsonProperty("bootstrapServers")
        val bootstrapServers: String,
        @JsonProperty("tls")
        val tls: TLS,
        @JsonProperty("sasl")
        val sasl: SASL
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
        val valueFrom: SecretValues?,
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
    data class Bootstrap(
        @JsonProperty("bootstrap")
        val bootstrap: KafkaBootstrap?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class KafkaBootstrap(
        @JsonProperty("kafka")
        val kafka: KafkaBootstrapConfiguration?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class KafkaBootstrapConfiguration(
        @JsonProperty("replicas")
        val replicas: Int?
    )

    //DB Secrets
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DB(
        @JsonProperty("db")
        val db: Cluster
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
        val port: Int? = 5432
    )

    data class SecretValues(
        @JsonProperty("valueFrom")
        val valueFrom: SecretValues?,
        @JsonProperty("secretKeyRef")
        val secretKeyRef: SecretValues?,
        @JsonProperty("value")
        val value: String?,
        @JsonProperty("key")
        val key: String?,
        @JsonProperty("name")
        val name: String?
    )

    //Resource secrets
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Configurations(
        @JsonProperty("bootstrap")
        val bootstrap: Resources?,
        @JsonProperty("workers")
        val workers: Workers?,
        @JsonProperty("resources")
        val resources: ResourceConfig
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Workers(
        @JsonProperty("db")
        val db: Resources?,
        @JsonProperty("flow")
        val flow: Resources?,
        @JsonProperty("membership")
        val membership: Resources?,
        @JsonProperty("rest")
        val rest: Resources?,
        @JsonProperty("p2pLinkManager")
        val p2pLinkManager: Resources?,
        @JsonProperty("p2pGateway")
        val p2pGateway: Resources?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Resources(
        @JsonProperty("resources")
        val resources: ResourceConfig
    )

    data class ResourceConfig(
        @JsonProperty("requests")
        val requests: ResourceValues,
        @JsonProperty("limits")
        val limits: ResourceValues
    )

    data class ResourceValues(
        @JsonProperty("memory")
        val memory: String,
        @JsonProperty("cpu")
        val cpu: String
    )
}