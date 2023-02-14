package net.corda.libs.configuration.datamodel

import net.corda.db.core.DbPrivilege
import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.datamodel.internal.DB_CONNECTION_AUDIT_GENERATOR
import java.time.Instant
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Enumerated
import javax.persistence.EnumType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType.SEQUENCE
import javax.persistence.Id
import javax.persistence.SequenceGenerator
import javax.persistence.Table

/**
 * Db Connection Audit data class
 *
 * @property changeNumber The sequence number of the audit event.
 * @property id
 * @property name
 * @property privilege DB privilege associated with the connection details (DDL/DML)
 * @property updateTimestamp
 * @property updateActor
 * @property description (optional)
 * @property config DB configuration section that can be parsed as SmartConfig.
 */
@Entity
@Table(name = DbSchema.DB_CONNECTION_AUDIT_TABLE)
data class DbConnectionAudit (
    @Id
    @SequenceGenerator(
        name = DB_CONNECTION_AUDIT_GENERATOR,
        sequenceName = DbSchema.DB_CONNECTION_AUDIT_ID_SEQUENCE,
        allocationSize = DbSchema.DB_CONNECTION_AUDIT_ID_SEQUENCE_ALLOC_SIZE
    )
    @GeneratedValue(strategy = SEQUENCE, generator = DB_CONNECTION_AUDIT_GENERATOR)
    @Column(name = "change_number", nullable = false)
    val changeNumber: Int,

    @Column(name = "connection_id", nullable = false)
    val id: UUID,
    @Column(name = "connection_name", nullable = false)
    val name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "privilege", nullable = false)
    val privilege: DbPrivilege,
    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,
    @Column(name = "update_actor", nullable = false)
    var updateActor: String,
    @Column(name = "description")
    var description: String?,
    @Column(name = "config", nullable = false)
    var config: String,
) {
    constructor(dbEntity: DbConnectionConfig) : this(
        0,
        dbEntity.id,
        dbEntity.name,
        dbEntity.privilege,
        dbEntity.updateTimestamp,
        dbEntity.updateActor,
        dbEntity.description,
        dbEntity.config
    )
}
