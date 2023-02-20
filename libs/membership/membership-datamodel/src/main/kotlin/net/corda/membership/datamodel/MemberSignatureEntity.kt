package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import net.corda.v5.base.types.MemberX500Name
import java.time.Instant
import java.util.Objects
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

/**
 * An entity representing a member info as visible by the current VNode.
 */
@Entity
@Table(name = DbSchema.VNODE_MEMBER_SIGNATURE)
@IdClass(MemberInfoEntityPrimaryKey::class)
class MemberSignatureEntity(
    @Id
    @Column(name = "group_id", nullable = false, updatable = false)
    val groupId: String,

    @Id
    @Column(name = "member_name", nullable = false, updatable = false)
    val memberX500Name: String,

    @Column(name = "public_key", nullable = false, updatable = false, columnDefinition = "BLOB")
    val publicKey: ByteArray,

    @Column(name = "context", nullable = false, updatable = false, columnDefinition = "BLOB")
    val context: ByteArray,

    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "BLOB")
    val content: ByteArray,

    @Column(name = "timestamp", nullable = false)
    var timestamp: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is MemberSignatureEntity) return false
        return other.groupId == this.groupId &&
            MemberX500Name.parse(other.memberX500Name) == MemberX500Name.parse(this.memberX500Name)
    }

    override fun hashCode(): Int {
        return Objects.hash(groupId, memberX500Name)
    }
}
