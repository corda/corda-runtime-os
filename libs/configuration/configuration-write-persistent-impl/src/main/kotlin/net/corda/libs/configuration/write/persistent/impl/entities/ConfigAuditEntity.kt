package net.corda.libs.configuration.write.persistent.impl.entities

import net.corda.libs.configuration.write.persistent.impl.CONFIG_AUDIT_GENERATOR
import net.corda.libs.configuration.write.persistent.impl.DB_TABLE_CONFIG_AUDIT
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType.SEQUENCE
import javax.persistence.Id
import javax.persistence.SequenceGenerator
import javax.persistence.Table

// TODO - Joel - Describe.
@Entity
@Table(name = DB_TABLE_CONFIG_AUDIT)
data class ConfigAuditEntity(
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
    val updateTimestamp: Instant,
    @Column(name = "update_actor", nullable = false)
    val updateActor: String
) {
    constructor(section: String, version: Int, configuration: String, updateTimestamp: Instant, updateActor: String) :
            this(0, section, version, configuration, updateTimestamp, updateActor)
}
