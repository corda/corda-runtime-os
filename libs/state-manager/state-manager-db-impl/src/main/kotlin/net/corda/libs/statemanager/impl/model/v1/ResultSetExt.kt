package net.corda.libs.statemanager.impl.model.v1

import java.sql.ResultSet

fun ResultSet.resultSetAsStateEntityCollection(): Collection<StateEntity> {
    val result = mutableListOf<StateEntity>()

    while (next()) {
        val key = getString(StateEntity.KEY_COLUMN)
        val value = getBytes(StateEntity.VALUE_COLUMN)
        val metadata = getString(StateEntity.METADATA_COLUMN)
        val version = getInt(StateEntity.VERSION_COLUMN)
        val modifiedTime = getTimestamp(StateEntity.MODIFIED_TIME_COLUMN).toInstant()

        result.add(StateEntity(key, value, metadata, version, modifiedTime))
    }

    return result
}
