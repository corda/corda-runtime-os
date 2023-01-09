package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import java.util.Objects
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * An entity representing an allowed certificate client (for mutual TLS).
 */
@Entity
@Table(name = DbSchema.VNODE_ALLOWED_CERTIFICATE_DB_TABLE)
class MutualTlsAllowedClientCertificateEntity(
    @Id
    @Column(name = "subject", nullable = false, updatable = false)
    val subject: String,
) {
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is MutualTlsAllowedClientCertificateEntity) return false
        return other.subject == this.subject
    }

    override fun hashCode(): Int {
        return Objects.hash(subject)
    }
}
