package net.corda.libs.configuration.write.impl.entities

import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.write.impl.CONFIG_AUDIT_GENERATOR
import net.corda.libs.configuration.write.impl.DB_TABLE_CONFIG_AUDIT
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType.SEQUENCE
import javax.persistence.Id
import javax.persistence.PrePersist
import javax.persistence.SequenceGenerator
import javax.persistence.Table

/**
 * The entity for the audit log of the cluster configuration in the cluster database.
 *
 * @param changeNumber The sequence number of the audit event.
 * @param section The section of the configuration.
 * @param config The configuration in JSON or HOCON format.
 * @param configVersion The version of the configuration.
 * @param updateTimestamp When this configuration update occurred.
 * @param updateActor The ID of the user that last updated this section of the configuration.
 */
@Entity
@Table(name = DB_TABLE_CONFIG_AUDIT, schema = DbSchema.CONFIG)
internal data class ConfigAuditEntity(
    @Id
    @SequenceGenerator(name = CONFIG_AUDIT_GENERATOR, sequenceName = "config_audit_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = SEQUENCE, generator = CONFIG_AUDIT_GENERATOR)
    @Column(name = "change_number", nullable = false)
    val changeNumber: Int,
    @Column(name = "section", nullable = false)
    val section: String,
    @Column(name = "config", nullable = false)
    val config: String,
    @Column(name = "config_version", nullable = false)
    val configVersion: Int,
    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,
    @Column(name = "update_actor", nullable = false)
    val updateActor: String
) {
    constructor(section: String, config: String, configVersion: Int, updateActor: String) :
            this(0, section, config, configVersion, Instant.MIN, updateActor)

    /** Sets [updateTimestamp] to the current time. */
    @Suppress("Unused")
    @PrePersist
    private fun onCreate() {
        updateTimestamp = Instant.now()
    }
}
