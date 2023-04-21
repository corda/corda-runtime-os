package net.corda.libs.configuration.datamodel

import net.corda.db.schema.DbSchema
import net.corda.db.schema.DbSchema.CONFIG_AUDIT_ID_SEQUENCE
import net.corda.db.schema.DbSchema.CONFIG_AUDIT_ID_SEQUENCE_ALLOC_SIZE
import net.corda.libs.configuration.datamodel.internal.CONFIG_AUDIT_GENERATOR
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType.SEQUENCE
import javax.persistence.Id
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
@Table(name = DbSchema.CONFIG_AUDIT_TABLE)
data class ConfigAuditEntity(
    @Id
    @SequenceGenerator(
        name = CONFIG_AUDIT_GENERATOR,
        sequenceName = CONFIG_AUDIT_ID_SEQUENCE,
        allocationSize = CONFIG_AUDIT_ID_SEQUENCE_ALLOC_SIZE
    )
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
    constructor(configEntity: ConfigEntity) : this(
        0,
        configEntity.section,
        configEntity.config,
        configEntity.version,
        configEntity.updateTimestamp,
        configEntity.updateActor
    )
}
