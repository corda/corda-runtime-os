package net.corda.chunking.datamodel

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

/**
 * Note: There is no association from this entity to [ChunkEntity], this is due to the fact that [ChunkEntity] has
 * `partNumber` whereas [ChunkPropertyEntity] does not. Therefore, it is possible to say that a set of properties applies
 * to multiple ChunkEntities.
 */
@Entity
@IdClass(ChunkPropertyEntityPrimaryKey::class)
@Table(name = "file_upload_props")
data class ChunkPropertyEntity(

    @Id
    @Column(name = "request_id", nullable = false)
    val requestId: String,

    @Id
    @Column(name = "key", nullable = false)
    val key: String,

    @Column(name = "value", nullable = true)
    val value: String?,

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this.javaClass != other.javaClass) return false
        other as ChunkPropertyEntity

        return requestId == other.requestId && key == other.key && value == other.value
    }

    override fun hashCode(): Int = javaClass.hashCode()

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(updateTimestamp = $updateTimestamp , key = $key , value = $value )"
    }
}