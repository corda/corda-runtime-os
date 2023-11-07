package net.corda.libs.statemanager.impl.model.v1

import java.time.Instant

class StateEntity(
    val key: String,
    val value: ByteArray,
    var metadata: String,
    var version: Int = 0,
    var modifiedTime: Instant = Instant.MIN,
) {
    // Database Column Names (should this be moved to net.corda.db.schema.DbSchema?)
    companion object {
        const val KEY_COLUMN = "key"
        const val VALUE_COLUMN = "value"
        const val METADATA_COLUMN = "metadata"
        const val VERSION_COLUMN = "version"
        const val MODIFIED_TIME_COLUMN = "modified_time"
    }

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
