package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import java.io.Serializable
import java.util.Objects
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

/**
 * An entity representing the session key and certificate information for locally-hosted identities.
 */
@Entity
@Table(name = DbSchema.HOSTED_IDENTITY_SESSION_KEY_INFO)
@IdClass(HostedIdentitySessionKeyInfoEntityId::class)
class HostedIdentitySessionKeyInfoEntity(
    @Id
    @Column(name = "holding_identity_id", nullable = false, updatable = false)
    var holdingIdentityShortHash: String,

    @Id
    @Column(name = "session_key_id", nullable = false, updatable = false)
    var sessionKeyId: String,

    @Column(name = "session_certificate_alias", nullable = true)
    var sessionCertificateAlias: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is HostedIdentitySessionKeyInfoEntity) return false
        return other.holdingIdentityShortHash == this.holdingIdentityShortHash &&
            other.sessionKeyId == this.sessionKeyId
    }

    override fun hashCode(): Int {
        return Objects.hash(holdingIdentityShortHash, sessionKeyId)
    }
}

@Embeddable
data class HostedIdentitySessionKeyInfoEntityId(
    var holdingIdentityShortHash: String,
    var sessionKeyId: String
) : Serializable
