package net.corda.libs.virtualnode.datamodel

import net.corda.db.schema.DbSchema.CONFIG
import net.corda.db.schema.DbSchema.VNODE_INSTANCE_DB_TABLE
import java.io.Serializable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table
import javax.persistence.Column
import javax.persistence.Embeddable

/**
 * The entity for a virtual node instance in the cluster database.
 *
 * @param holdingIdentityId The short 12-character hash of the virtual node's holding identity.
 * @param cpiName The name of the CPI the virtual node is created for.
 * @param cpiVersion The version of the CPI the virtual node is created for.
 * @param cpiSignerSummaryHash The signer summary hash of the CPI the virtual node is created for.
 */
@Entity
@Table(name = VNODE_INSTANCE_DB_TABLE, schema = CONFIG)
@IdClass(VirtualNodeEntityKey::class)
data class VirtualNodeEntity(
    @Id
    @Column(name = "holding_identity_id", nullable = false)
    val holdingIdentityId: String,
    @Id
    @Column(name = "cpi_name", nullable = false)
    var cpiName: String,
    @Id
    @Column(name = "cpi_version", nullable = false)
    var cpiVersion: String,
    @Id
    @Column(name = "cpi_signer_summary_hash", nullable = false)
    var cpiSignerSummaryHash: String
)

/** The composite primary key for a virtual node instance. */
@Embeddable // Set so that kotlin-jpa creates no-argument constructor required by JPA
@Suppress("Unused")
class VirtualNodeEntityKey(
    private val holdingIdentityId: String,
    private val cpiName: String,
    private val cpiVersion: String,
    private val cpiSignerSummaryHash: String
) : Serializable