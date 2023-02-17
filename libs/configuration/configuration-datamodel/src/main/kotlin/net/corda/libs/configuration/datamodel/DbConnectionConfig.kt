package net.corda.libs.configuration.datamodel

import net.corda.db.core.DbPrivilege
import net.corda.db.schema.DbSchema
import java.time.Instant
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EntityManager
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.NamedQuery
import javax.persistence.Table
import javax.persistence.Version

internal const val QUERY_FIND_BY_NAME_AND_PRIVILEGE = "DbConnectionConfig.findByNameAndPrivilege"
internal const val QUERY_PARAM_NAME = "name"
internal const val QUERY_PARAM_PRIVILEGE = "privilege"

/**
 * Db Connection Config data class
 *
 * @property id
 * @property name
 * @property privilege DB privilege associated with the connection details (DDL/DML)
 * @property updateTimestamp
 * @property updateActor
 * @property description (optional)
 * @property config DB configuration section that can be parsed as SmartConfig.
 *
 * @property version The version number used for optimistic locking.
 */
@Entity
@Table(name = DbSchema.DB_CONNECTION_TABLE)
@NamedQuery(
    name = QUERY_FIND_BY_NAME_AND_PRIVILEGE,
    query = "SELECT c FROM DbConnectionConfig c WHERE c.name=:$QUERY_PARAM_NAME AND c.privilege=:$QUERY_PARAM_PRIVILEGE")
data class DbConnectionConfig (
    @Id
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
    @Column(name = "timestamp", nullable = false)
    var timestamp: Instant
) {
    fun update(config: String, description: String?, updateActor: String) {
        this.config = config
        this.description = description
        this.updateActor = updateActor
    }

    @Version
    @Column(name = "version", nullable = false)
    var version: Int = -1
}

fun EntityManager.findDbConnectionByNameAndPrivilege(name: String, privilege: DbPrivilege) : DbConnectionConfig? {
    val q = this.createNamedQuery(QUERY_FIND_BY_NAME_AND_PRIVILEGE)
    q.setParameter(QUERY_PARAM_NAME, name)
    q.setParameter(QUERY_PARAM_PRIVILEGE, privilege)
    // NOTE: need to use resultList here rather than singleResult as we need to return null when none is found
    val obj = q.resultList
    if (obj.isEmpty())
        return null
    return obj.first() as DbConnectionConfig
}
