package net.corda.cli.plugins.preinstall

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import io.fabric8.kubernetes.api.model.Secret
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
import java.util.Base64

class PreInstallPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        logger.info("starting preinstall plugin")
    }

    override fun stop() {
        logger.info("stopping preinstall plugin")
    }

    @Extension
    @CommandLine.Command(name = "preinstall",
        subcommands = [CheckLimits::class, CheckPostgres::class, CheckKafka::class, RunAll::class],
        description = ["Preinstall checks for corda."])
    class PreInstallPluginEntry : CordaCliPlugin

    open class PluginContext {
        private var verbose = false
        private var debug = false

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

        inline fun <reified T> parseYaml(path: String): T? {
            log("Working Directory = ${System.getProperty("user.dir")}\n", INFO)

            val file = File(path)

            if(!file.isFile) {
                log("File does not exist", ERROR)
                return null
            }

            return try {
                val mapper: ObjectMapper = YAMLMapper()
                mapper.readValue(file, T::class.java)
            } catch ( e: ValueInstantiationException) {
                log("Could not parse the YAML file at $path: ${e.message}", ERROR)
                null
            }
        }


        fun getCredentialOrSecret(values: SecretValues, namespace: String?): String? {
            val secretKey: String? = values.valueFrom?.secretKeyRef?.key
            val secretName: String? = values.valueFrom?.secretKeyRef?.name
            var credential: String? = values.value

            credential = credential ?: run {
                if (namespace == null) {
                    log("No namespace has been specified. If the username is supposed to be in a secret, " +
                            "specify a namespace with -n or --namespace.", ERROR)
                    return null
                }
                if (secretKey == null)  {
                    log("Username secret key could not be parsed.", ERROR)
                    return null
                }
                if (secretName == null) {
                    log("Username secret name could not be parsed.", ERROR)
                    return null
                }
                val encoded = getSecret(secretName, secretKey, namespace) ?: run{
                    log("Username secret could not be found in namespace $namespace.", ERROR)
                    return null
                }
                String(Base64.getDecoder().decode(encoded))
            }
            return credential
        }

        private fun getSecret(secretName: String, secretKey: String, namespace: String): String? {
            return try {
                val client: KubernetesClient = KubernetesClientBuilder().build()
                val secret: Secret = client.secrets().inNamespace(namespace).withName(secretName).get()
                secret.data[secretKey]
            } catch (e: KubernetesClientException) {
                log("Could not read secret $secretName with key $secretKey.", ERROR)
                null
            } catch (e: NullPointerException) {
                log("Could not read secret $secretName with key $secretKey.", ERROR)
                null
            }
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
        val valueFrom: SecretValues,
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