package net.corda.libs.statemanager.impl.model.v1

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import java.sql.ResultSet

fun ResultSet.resultSetAsStateCollection(objectMapper: ObjectMapper): Collection<State> {
    val result = mutableListOf<State>()

    while (next()) {
        val key = getString(StateEntity.KEY_COLUMN)
        val value = getBytes(StateEntity.VALUE_COLUMN)
        val metadata = getString(StateEntity.METADATA_COLUMN)
        val version = getInt(StateEntity.VERSION_COLUMN)
        val modifiedTime = getTimestamp(StateEntity.MODIFIED_TIME_COLUMN).toInstant()

        result.add(State(key, value, version, Metadata(objectMapper.readValue(metadata)), modifiedTime))
    }

    return result
}
