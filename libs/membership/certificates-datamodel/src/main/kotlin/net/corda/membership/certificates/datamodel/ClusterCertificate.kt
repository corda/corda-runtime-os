package net.corda.membership.certificates.datamodel

import net.corda.db.schema.DbSchema.CLUSTER_CERTIFICATE_DB_TABLE
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * An entity representing a single certificate.
 */
@Entity
@Table(name = CLUSTER_CERTIFICATE_DB_TABLE)
data class ClusterCertificate(
    @Id
    @Column(name = "alias", nullable = false, updatable = false)
    override var alias: String,

    @Column(name = "usage", nullable = false, updatable = false)
    override var usage: String,

    @Column(name = "raw_certificate", nullable = false, updatable = true)
    override var rawCertificate: String,
) : CertificateEntity
