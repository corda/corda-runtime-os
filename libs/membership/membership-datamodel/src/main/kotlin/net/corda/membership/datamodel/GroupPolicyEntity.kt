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
    @Column(name = "schema_version", nullable = false, updatable = false)
    var version: Int? = null,

    @Column(name = "effective_from", nullable = false, updatable = false)
    val effectiveFrom: Instant,

    @Column(name = "properties", nullable = false, updatable = false)
    val properties: ByteArray,
) {

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is GroupPolicyEntity) return false
        return other.effectiveFrom == this.effectiveFrom && Arrays.equals(other.properties, this.properties)
    }

    override fun hashCode(): Int {
        return Objects.hash(effectiveFrom, ByteBuffer.wrap(properties))
    }
}
