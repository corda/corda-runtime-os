package net.corda.libs.statemanager.impl.model.v1

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Version
import net.corda.db.schema.DbSchema
import javax.persistence.NamedNativeQuery
import javax.persistence.NamedQuery

const val CREATE_STATE_QUERY_NAME = "StateEntity.create"
const val UPDATE_STATE_QUERY_NAME = "StateEntity.update"
const val FILTER_STATES_BY_KEY_QUERY_NAME = "StateEntity.queryByKey"
const val DELETE_STATES_BY_KEY_QUERY_NAME = "StateEntity.deleteByKey"
const val FILTER_STATES_BY_UPDATED_TIMESTAMP_QUERY_NAME = "StateEntity.queryByTimestamp"

const val KEY_ID = "key"
const val VALUE_ID = "value"
const val VERSION_ID = "version"
const val METADATA_ID = "metadata"
const val START_TIMESTAMP_ID = "startTime"
const val FINISH_TIMESTAMP_ID = "finishTime"

// TODO-[CORE-17025]: remove Hibernate
// TODO-[CORE-16663]: Make database provider pluggable.
// Hibernate 5 does not support inserting a String to a jsonb column type out of the box, so we use
// native queries with casting here (also used in the ledger).
@NamedNativeQuery(
    name = CREATE_STATE_QUERY_NAME,
    query = """
        INSERT INTO ${DbSchema.STATE_MANAGER_TABLE}
        VALUES (:$KEY_ID, :$VALUE_ID, :$VERSION_ID, CAST(:$METADATA_ID as JSONB), CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
    """
)

@NamedNativeQuery(
    name = UPDATE_STATE_QUERY_NAME,
    query = """
        UPDATE ${DbSchema.STATE_MANAGER_TABLE} SET
        key = :$KEY_ID, value = :$VALUE_ID, version = version + 1, metadata = CAST(:$METADATA_ID as JSONB), modified_time = CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
        WHERE key = :$KEY_ID
    """
)

@NamedQuery(
    name = FILTER_STATES_BY_KEY_QUERY_NAME,
    query = "FROM StateEntity state WHERE state.key IN :$KEY_ID"
)

@NamedQuery(
    name = FILTER_STATES_BY_UPDATED_TIMESTAMP_QUERY_NAME,
    query = "FROM StateEntity state WHERE state.modifiedTime BETWEEN :$START_TIMESTAMP_ID AND :$FINISH_TIMESTAMP_ID"
)

@NamedQuery(
    name = DELETE_STATES_BY_KEY_QUERY_NAME,
    query = "DELETE FROM StateEntity state WHERE state.key IN :$KEY_ID"
)

@Entity
@Table(name = DbSchema.STATE_MANAGER_TABLE)
class StateEntity(
    @Id
    @Column(name = "key", length = 255)
    val key: String,

    @Column(name = "value", columnDefinition = "BLOB", nullable = false)
    val value: ByteArray,

    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    var metadata: String,

    @Version
    @Column(name = "version", nullable = false)
    var version: Int = 0,

    @Column(name = "modified_time", insertable = false, updatable = false)
    var modifiedTime: Instant = Instant.MIN,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StateEntity

        return key == other.key
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }
}
