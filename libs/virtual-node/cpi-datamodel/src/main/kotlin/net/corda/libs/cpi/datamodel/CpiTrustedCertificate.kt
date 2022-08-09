package net.corda.libs.cpi.datamodel

import net.corda.db.schema.DbSchema
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * An entity representing a trusted certificate used for package verification.
 */
@Entity
@Table(name = "cpi_trusted_certificate", schema = DbSchema.CONFIG)
data class CpiTrustedCertificate(
    @Id
    @Column(name = "alias", nullable = false, updatable = false)
    val alias: String,

    @Column(name = "raw_certificate", nullable = false, updatable = true)
    val rawCertificate: String
)
