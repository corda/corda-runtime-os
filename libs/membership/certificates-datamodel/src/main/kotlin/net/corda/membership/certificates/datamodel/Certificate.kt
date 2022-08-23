package net.corda.membership.certificates.datamodel

import net.corda.db.schema.DbSchema
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.NamedQuery
import javax.persistence.Table

/**
 * An entity representing a single certificate.
 */
@NamedQuery(
    name = "Certificate.findAll",
    query = "from Certificate"
)
@Entity
@Table(name = DbSchema.VNODE_CERTIFICATE_DB_TABLE)
data class Certificate(
    @Id
    @Column(name = "alias", nullable = false, updatable = false)
    val alias: String,

    @Column(name = "raw_certificate", nullable = false, updatable = true)
    val rawCertificate: String,
)
