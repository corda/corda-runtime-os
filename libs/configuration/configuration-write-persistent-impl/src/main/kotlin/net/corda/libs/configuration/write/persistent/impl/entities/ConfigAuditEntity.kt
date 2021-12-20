package net.corda.libs.configuration.write.persistent.impl.entities

import net.corda.libs.configuration.write.persistent.impl.CONFIG_AUDIT_GENERATOR
import net.corda.libs.configuration.write.persistent.impl.DB_TABLE_CONFIG_AUDIT
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
 * @param version The version of the configuration.
 * @param configuration The configuration in JSON or HOCON format.
 * @param updateTimestamp When this configuration update occurred.
 * @param updateActor The ID of the user that last updated this section of the configuration.
 */
@Entity
@Table(name = DB_TABLE_CONFIG_AUDIT)
internal class ConfigAuditEntity(
    @Id
    @SequenceGenerator(name = CONFIG_AUDIT_GENERATOR, sequenceName = "config_audit_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = SEQUENCE, generator = CONFIG_AUDIT_GENERATOR)
    @Column(name = "change_number", nullable = false)
    val changeNumber: Int,
    @Column(name = "section", nullable = false)
    val section: String,
    @Column(name = "version", nullable = false)
    val version: Int,
    @Column(name = "config", nullable = false)
    val configuration: String,
    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,
    @Column(name = "update_actor", nullable = false)
    val updateActor: String
) {
    constructor(section: String, version: Int, configuration: String, updateActor: String) :
            this(0, section, version, configuration, Instant.MIN, updateActor)

    /** Sets [updateTimestamp] to the current time. */
    @Suppress("Unused")
    @PrePersist
    private fun onCreate() {
        updateTimestamp = Instant.now()
    }
}
