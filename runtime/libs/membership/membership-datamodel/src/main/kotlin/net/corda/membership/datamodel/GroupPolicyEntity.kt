package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import java.time.Instant
import java.util.Objects
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * An entity representing a member info as visible by the current VNode.
 */
@Entity
@Table(name = DbSchema.VNODE_GROUP_POLICY)
@Suppress("LongParameterList")
class GroupPolicyEntity(
    @Id
    @Column(name = "version", nullable = false, updatable = false)
    var version: Long,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "properties", nullable = false, updatable = false)
    val properties: ByteArray,
) {

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is GroupPolicyEntity) return false
        return other.version == this.version
    }

    override fun hashCode(): Int {
        return Objects.hash(version)
    }
}
