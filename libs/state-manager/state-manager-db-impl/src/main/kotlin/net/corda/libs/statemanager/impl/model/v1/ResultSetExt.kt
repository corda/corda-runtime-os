package net.corda.libs.statemanager.impl.model.v1

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.impl.model.v1.StateColumns.KEY_COLUMN
import net.corda.libs.statemanager.impl.model.v1.StateColumns.METADATA_COLUMN
import net.corda.libs.statemanager.impl.model.v1.StateColumns.MODIFIED_TIME_COLUMN
import net.corda.libs.statemanager.impl.model.v1.StateColumns.VALUE_COLUMN
import net.corda.libs.statemanager.impl.model.v1.StateColumns.VERSION_COLUMN
import java.sql.ResultSet

fun ResultSet.resultSetAsStateCollection(objectMapper: ObjectMapper): Collection<State> {
    val result = mutableListOf<State>()

    while (next()) {
        val key = getString(KEY_COLUMN)
        val value = getBytes(VALUE_COLUMN)
        val metadata = getString(METADATA_COLUMN)
        val version = getInt(VERSION_COLUMN)
        val modifiedTime = getTimestamp(MODIFIED_TIME_COLUMN).toInstant()

        result.add(State(key, value, version, Metadata(objectMapper.readValue(metadata)), modifiedTime))
    }

    return result
}
