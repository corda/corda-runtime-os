package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import net.corda.v5.base.types.MemberX500Name
import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

/**
 * An entity representing a member info as visible by the current VNode.
 */
@Entity
@Table(name = DbSchema.VNODE_MEMBER_INFO)
@IdClass(MemberInfoEntityPrimaryKey::class)
@Suppress("LongParameterList")
class MemberInfoEntity(
    @Id
    @Column(name = "group_id", nullable = false, updatable = false)
    var groupId: String,

    @Id
    @Column(name = "member_name", nullable = false, updatable = false)
    var memberX500Name: String,

    @Id
    @Column(name = "is_pending", nullable = false, updatable = false)
    var isPending: Boolean,

    @Column(nullable = false)
    var status: String,

    @Column(name = "modified_time", nullable = false)
    var modifiedTime: Instant,

    @Column(name = "member_context", nullable = false)
    var memberContext: ByteArray,

    @Column(name = "member_signature_key", nullable = false, columnDefinition = "BLOB")
    var memberSignatureKey: ByteArray,

    @Column(name = "member_signature_content", nullable = false, columnDefinition = "BLOB")
    var memberSignatureContent: ByteArray,

    // TODO Are we going to be storing `ParameterizedSignatureSpec` here?
    //  If so need to consider saving extra signature spec parameters as recorded in https://r3-cev.atlassian.net/browse/CORE-11685
    @Column(name = "member_signature_spec", nullable = false)
    var memberSignatureSpec: String,

    @Column(name = "mgm_context", nullable = false)
    var mgmContext: ByteArray,

    @Column(name = "serial_number", nullable = false)
    var serialNumber: Long,

    @Column(name = "is_deleted", nullable = false, updatable = true)
    var isDeleted: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is MemberInfoEntity) return false
        return other.groupId == this.groupId &&
            MemberX500Name.parse(other.memberX500Name) == MemberX500Name.parse(this.memberX500Name) &&
            other.isPending == this.isPending
    }

    override fun hashCode(): Int {
        var result = groupId.hashCode()
        result = 31 * result + memberX500Name.hashCode()
        result = 31 * result + isPending.hashCode()
        return result
    }
}

@Embeddable
data class MemberInfoEntityPrimaryKey(
    var groupId: String,
    var memberX500Name: String,
    var isPending: Boolean,
) : Serializable
