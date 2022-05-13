package net.corda.chunking.datamodel

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@IdClass(ChunkPropertyEntityPrimaryKey::class)
@Table(name = "file_upload_props", schema = DbSchema.CONFIG)
data class ChunkPropertyEntity(

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,

    @Id
    @Column(name = "request_id", nullable = false)
    val requestId: String,

    /**
     * A ChunkProperty can be associated with one Chunk.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    val chunk: ChunkEntity,

    @Id
    @Column(name = "key", nullable = false)
    val key: String,

    @Column(name = "value", nullable = true)
    val value: String?
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