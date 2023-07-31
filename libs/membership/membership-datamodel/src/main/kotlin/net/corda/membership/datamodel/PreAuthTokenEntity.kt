package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = DbSchema.VNODE_PRE_AUTH_TOKENS)
@Suppress("LongParameterList")
class PreAuthTokenEntity (
    /**
     * A unique ID for the pre auth token.
     */
    @Id
    @Column(name = "token_id", nullable = false, updatable = false)
    var tokenId: String,

    @Column(name = "owner_x500_name", nullable = false, updatable = false)
    var ownerX500Name: String,

    @Column(name = "ttl", updatable = false)
    var ttl: Instant?,

    @Column(name = "status", nullable = false)
    var status: String,

    @Column(name = "creation_remark", updatable = false)
    var creationRemark: String?,

    @Column(name = "removal_remark")
    var removalRemark: String?
)  {

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is PreAuthTokenEntity) return false
        return other.tokenId == this.tokenId
    }

    override fun hashCode(): Int {
        return this.tokenId.hashCode()
    }
}