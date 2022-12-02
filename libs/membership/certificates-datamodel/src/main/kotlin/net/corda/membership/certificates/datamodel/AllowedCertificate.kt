package net.corda.membership.certificates.datamodel

import net.corda.db.schema.DbSchema
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = DbSchema.VNODE_CERTIFICATE_ALLOWED_TABLE)
data class AllowedCertificate(
    @Id
    @Column(name = "subject", nullable = false, updatable = false)
    val subject: String,
)
