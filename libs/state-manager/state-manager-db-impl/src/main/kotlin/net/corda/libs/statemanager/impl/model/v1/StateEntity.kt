package net.corda.libs.statemanager.impl.model.v1

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Version
import net.corda.db.schema.DbSchema

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
