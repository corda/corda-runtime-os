package net.corda.libs.virtualnode.datamodel

import net.corda.db.schema.DbSchema.CONFIG
import net.corda.db.schema.DbSchema.VNODE_INSTANCE_DB_TABLE
import java.io.Serializable
import java.time.Instant
import java.util.stream.Stream
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.EntityManager
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.Version

/**
 * The entity for a virtual node instance in the cluster database.
 *
 * @param holdingIdentity The virtual node's holding identity.
 * @param cpiName The name of the CPI the virtual node is created for.
 * @param cpiVersion The version of the CPI the virtual node is created for.
 * @param cpiSignerSummaryHash The signer summary hash of the CPI the virtual node is created for.
 */
@Entity
@Table(name = VNODE_INSTANCE_DB_TABLE, schema = CONFIG)
@IdClass(VirtualNodeEntityKey::class)
data class VirtualNodeEntity(
    @ManyToOne(
        fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE]
    )
    @JoinColumn(name = "holding_identity_id")
    val holdingIdentity: HoldingIdentityEntity,
    @Id
    @Column(name = "cpi_name", nullable = false)
    var cpiName: String,
    @Id
    @Column(name = "cpi_version", nullable = false)
    var cpiVersion: String,
    @Id
    @Column(name = "cpi_signer_summary_hash", nullable = false)
    var cpiSignerSummaryHash: String,

    @Column(name = "state", nullable = false)
    var virtualNodeState: String,

    @Column(name = "insert_ts", insertable = false, updatable = true)
    var insertTimestamp: Instant? = null,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,

    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VirtualNodeEntity

        if (holdingIdentity != other.holdingIdentity) return false
        if (cpiName != other.cpiName) return false
        if (cpiVersion != other.cpiVersion) return false
        if (cpiSignerSummaryHash != other.cpiSignerSummaryHash) return false

        return true
    }

    override fun hashCode(): Int {
        var result = holdingIdentity.hashCode()
        result = 31 * result + cpiName.hashCode()
        result = 31 * result + cpiVersion.hashCode()
        result = 31 * result + cpiSignerSummaryHash.hashCode()
        return result
    }

    fun update(newState: String) {
        virtualNodeState = newState
    }
}

/** The composite primary key for a virtual node instance. */
@Embeddable
@Suppress("Unused")
class VirtualNodeEntityKey(
    private val holdingIdentity: HoldingIdentityEntity,
    private val cpiName: String,
    private val cpiVersion: String,
    private val cpiSignerSummaryHash: String
) : Serializable

/**
 * If you change this function ensure that you check the generated SQL from
 * hibnernate in the "virtual node entity query test" in
 * [net.corda.libs.configuration.datamodel.tests.VirtualNodeEntitiesIntegrationTest]
 */
fun EntityManager.findAllVirtualNodes(): Stream<VirtualNodeEntity> {
    val query = criteriaBuilder!!.createQuery(VirtualNodeEntity::class.java)!!
    val root = query.from(VirtualNodeEntity::class.java)
    root.fetch<Any, Any>("holdingIdentity")
    query.select(root)

    return createQuery(query).resultStream
}
