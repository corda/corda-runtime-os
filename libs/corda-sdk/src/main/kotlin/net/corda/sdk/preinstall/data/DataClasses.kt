package net.corda.sdk.preinstall.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

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
data class Bootstrap(
    @JsonProperty("db")
    val db: BootstrapDb?,
    @JsonProperty("kafka")
    val kafka: BootstrapKafka?,
    @JsonProperty("resources")
    val resources: Resources?
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class PersistentStorage(
    @JsonProperty("storageId")
    val storageId: String,
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkerKafka(
    @JsonProperty("sasl")
    val sasl: ClientSASL?
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
