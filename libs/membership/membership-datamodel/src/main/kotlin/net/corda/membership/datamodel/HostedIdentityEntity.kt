package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import java.util.Objects
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * An entity representing a locally-hosted identity.
 */
@Entity
@Table(name = DbSchema.HOSTED_IDENTITY)
class HostedIdentityEntity(
    @Id
    @Column(name = "holding_identity_id", nullable = false, updatable = false)
    var holdingIdentityShortHash: String,

    @Column(name = "preferred_session_key_id", nullable = false)
    var preferredSessionKeyAndCertificate: String,

    @Column(name = "tls_certificate_alias", nullable = false)
    var tlsCertificateChainAlias: String,

    @Column(name = "use_cluster_level_tls", nullable = false)
    var useClusterLevelTlsCertificateAndKey: Boolean,

    @Column(name = "version", nullable = false)
    var version: Int
) {
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is HostedIdentityEntity) return false
        return other.holdingIdentityShortHash == this.holdingIdentityShortHash &&
            other.preferredSessionKeyAndCertificate == this.preferredSessionKeyAndCertificate
    }

    override fun hashCode(): Int {
        return Objects.hash(holdingIdentityShortHash, preferredSessionKeyAndCertificate)
    }
}
