package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = DbSchema.VNODE_PRE_AUTH_TOKENS)
class PreAuthTokenEntity (
    /**
     * A unique ID for the pre auth token.
     */
    @Id
    @Column(name = "token_id", nullable = false, updatable = false)
    val tokenId: String,

    @Column(name = "owner_x500_name", nullable = false, updatable = false)
    val ownerX500Name: String,

    @Column(name = "ttl", nullable = false, updatable = false)
    val ttl: Instant,

    @Column(name = "status", nullable = false)
    var status: String,

    @Column(name = "remark")
    var remark: String
)  {

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is PreAuthTokenEntity) return false
        return other.tokenId == this.tokenId && other.status == this.status
    }

    override fun hashCode(): Int {
        return this.tokenId.hashCode()
    }
}