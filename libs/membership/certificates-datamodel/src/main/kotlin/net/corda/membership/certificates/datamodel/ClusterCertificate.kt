package net.corda.membership.certificates.datamodel

import net.corda.db.schema.DbSchema.CLUSTER_CERTIFICATE_DB_TABLE
import net.corda.db.schema.DbSchema.CONFIG
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

/**
 * An entity representing a single certificate.
 */
@Entity
@IdClass(ClusterCertificatePrimaryKey::class)
@Table(name = CLUSTER_CERTIFICATE_DB_TABLE, schema = CONFIG)
data class ClusterCertificate(
    @Id
    @Column(name = "type", nullable = false, updatable = false)
    val type: String,

    @Id
    @Column(name = "alias", nullable = false, updatable = false)
    val alias: String,

    @Column(name = "raw_certificate", nullable = false, updatable = true)
    val rawCertificate: String,
)

@Embeddable
data class ClusterCertificatePrimaryKey(
    private val type: String,
    private val alias: String,
) : Serializable
