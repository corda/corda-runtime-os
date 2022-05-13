package net.corda.chunking.datamodel

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "file_upload_props", schema = DbSchema.CONFIG)
data class ChunkPropertyEntity(

    @Id
    @Column(name = "id", nullable = false)
    val id: String,

    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,

    /**
     * A ChunkProperty can be associated with one Chunk.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requestId", nullable = false)
    val requestId: ChunkEntity,

    @Column(name = "key", nullable = false)
    val key: String,

    @Column(name = "value", nullable = false)
    val value: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this.javaClass != other.javaClass) return false
        other as ChunkPropertyEntity

        return id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(id = $id , updateTimestamp = $updateTimestamp , key = $key , value = $value )"
    }
}