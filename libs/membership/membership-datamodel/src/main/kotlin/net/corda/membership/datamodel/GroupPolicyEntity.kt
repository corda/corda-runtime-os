package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Arrays
import java.util.Objects
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version", nullable = false, updatable = false)
    var version: Int? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "properties", nullable = false, updatable = false)
    val properties: ByteArray,
) {

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is GroupPolicyEntity) return false
        return other.createdAt == this.createdAt && Arrays.equals(other.properties, this.properties)
    }

    override fun hashCode(): Int {
        return Objects.hash(createdAt, ByteBuffer.wrap(properties))
    }
}
