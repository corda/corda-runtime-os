package net.corda.crypto.persistence.db.model

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.Table
import javax.persistence.Version

/**
 * An entity representing an HSM instance configuration.
 * If an HSM supports multiple partitions which should be configured then each such partition must be represented as
 * a new record.
 *
 * The records are mutable.
 */
@Entity
@Table(name = DbSchema.CRYPTO_HSM_CONFIG_TABLE)
class HSMConfigEntity(
    /**
     * The configuration id.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    val id: String,

    /**
     * When the configuration was created or updated.
     */
    @Column(name = "timestamp", nullable = false)
    var timestamp: Instant,

    /**
     * A label (topic name suffix) which overrides the defaults in order to be able to route to HSM instances which
     * has a limit of a single login per machine.
     */
    @Column(name = "worker_label", nullable = true, length = 512)
    var workerLabel: String?,

    /**
     * Human-readable description. Note that the field is required so the users will be able to distinguish between
     * instances as the information which allows to it is actually encrypted in the [serviceConfig] field.
     */
    @Column(name = "description", nullable = false, length = 1024)
    var description: String,

    /**
     * Defines how wrapping key should be used for each tenant.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "master_key_policy", nullable = false, length = 16)
    var masterKeyPolicy: MasterKeyPolicy,

    /**
     * If the [masterKeyPolicy] == SHARED then this filed must contain the wrapping key name to be used.
     */
    @Column(name = "master_key_alias", nullable = true, length = 30)
    var masterKeyAlias: String?,

    /**
     * Comma delimited list of supported key scheme codes.
     */
    @Lob
    @Column(name = "supported_schemes", nullable = false)
    var supportedSchemes: String,

    /**
     * Number of retries (if the value is set to 0 then only call to the HSM will be attempted) after which give up.
     */
    @Column(name = "retries", nullable = false)
    var retries: Int,

    /**
     * Number of milliseconds before receiving a reply and considering a call failed.
     */
    @Column(name = "timeout_mills", nullable = false)
    var timeoutMills: Long,

    /**
     * The name of the provider (see CryptoServiceProvider) which supplies integration with the given HSM.
     */
    @Column(name = "service_name", nullable = false, length = 512)
    var serviceName: String,

    /**
     * The service configuration. It's a JSON string serialized as a byte array and encrypted by the system key.
     * The data in the public API will be passed as a plain string and then encrypted. The API will never provide
     * that information back.
     */
    @Lob
    @Column(name = "service_config", nullable = false, columnDefinition="BLOB")
    var serviceConfig: ByteArray
) {
    @Version
    @Column(name = "version", nullable = false)
    var version: Int = -1
}
