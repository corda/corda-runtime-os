package net.corda.membership.certificates.datamodel

import net.corda.db.schema.DbSchema
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
@Table(name = DbSchema.CLUSTER_CERTIFICATES_DB_TABLE, schema = DbSchema.CERTIFICATES_SCHEME)
data class ClusterCertificate(
    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: String,

    @Id
    @Column(name = "alias", nullable = false, updatable = false)
    val alias: String,

    @Column(name = "raw_certificate", nullable = false, updatable = true)
    val rawCertificate: String,
)
@Embeddable
data class ClusterCertificatePrimaryKey(
    private val tenantId: String,
    private val alias: String,
) : Serializable
