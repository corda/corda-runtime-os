package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Objects
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

/**
 * An entity representing group parameters as visible by the current VNode.
 */
@Entity
@Table(name = DbSchema.VNODE_GROUP_PARAMETERS)
class GroupParametersEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "epoch", nullable = false, updatable = false)
    val epoch: Int? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val lastModified: Instant,

    @Column(name = "parameters", nullable = false, updatable = false)
    val parameters: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is GroupParametersEntity) return false
        return other.lastModified == this.lastModified && other.parameters.contentEquals(this.parameters)
    }

    override fun hashCode(): Int {
        return Objects.hash(lastModified, ByteBuffer.wrap(parameters))
    }

}
