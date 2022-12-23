package net.corda.libs.virtualnode.datamodel

import net.corda.db.schema.DbSchema.CONFIG
import net.corda.db.schema.DbSchema.VNODE_INSTANCE_DB_TABLE
import java.io.Serializable
import java.time.Instant
import java.util.UUID
import java.util.stream.Stream
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EntityManager
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.MapsId
import javax.persistence.OneToOne
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
class VirtualNodeEntity(
    @Id
    @Column(name = "holding_identity_id")
    val holdingIdentityId: String,

    @MapsId
    @OneToOne(cascade = [CascadeType.ALL])
    @JoinColumn(name = "holding_identity_id")
    val holdingIdentity: HoldingIdentityEntity,

    @Column(name = "cpi_name", nullable = false)
    var cpiName: String,

    @Column(name = "cpi_version", nullable = false)
    var cpiVersion: String,

    @Column(name = "cpi_signer_summary_hash", nullable = false)
    var cpiSignerSummaryHash: String,

    @Enumerated(value = EnumType.STRING)
    @Column(name = "flow_p2p_operational_status", nullable = false)
    var flowP2pOperationalStatus: OperationalStatus = OperationalStatus.ACTIVE,

    @Enumerated(value = EnumType.STRING)
    @Column(name = "flow_start_operational_status", nullable = false)
    var flowStartOperationalStatus: OperationalStatus = OperationalStatus.ACTIVE,

    @Enumerated(value = EnumType.STRING)
    @Column(name = "flow_operational_status", nullable = false)
    var flowOperationalStatus: OperationalStatus = OperationalStatus.ACTIVE,

    @Enumerated(value = EnumType.STRING)
    @Column(name = "vault_db_operational_status", nullable = false)
    var vaultDbOperationalStatus: OperationalStatus = OperationalStatus.ACTIVE,

    @Column(name = "vault_ddl_connection_id")
    var vaultDDLConnectionId: UUID? = null,

    @Column(name = "vault_dml_connection_id")
    var vaultDMLConnectionId: UUID? = null,

    @Column(name = "crypto_ddl_connection_id")
    var cryptoDDLConnectionId: UUID? = null,

    @Column(name = "crypto_dml_connection_id")
    var cryptoDMLConnectionId: UUID? = null,

    @Column(name = "uniqueness_ddl_connection_id")
    var uniquenessDDLConnectionId: UUID? = null,

    @Column(name = "uniqueness_dml_connection_id")
    var uniquenessDMLConnectionId: UUID? = null,

    @OneToOne(cascade = [CascadeType.MERGE], fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_in_progress")
    var operationInProgress: VirtualNodeOperationEntity? = null,

    @Column(name = "insert_ts", insertable = false)
    var insertTimestamp: Instant? = null,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,

    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0,
): Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VirtualNodeEntity

        if (holdingIdentity != other.holdingIdentity) return false

        return true
    }

    override fun hashCode(): Int {
        return holdingIdentity.hashCode()
    }
}

enum class OperationalStatus {
    ACTIVE, INACTIVE
}

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

fun EntityManager.findVirtualNode(holdingIdentityShortHash: String): VirtualNodeEntity? {
    val queryBuilder = with(criteriaBuilder!!) {
        val queryBuilder = createQuery(VirtualNodeEntity::class.java)!!
        val root = queryBuilder.from(VirtualNodeEntity::class.java)
        root.fetch<Any, Any>("holdingIdentity")
        queryBuilder.where(
            equal(
                root.get<HoldingIdentityEntity>("holdingIdentity").get<String>("holdingIdentityShortHash"),
                parameter(String::class.java, "shortId")
            )
        ).orderBy(desc(root.get<String>("cpiVersion")))
        queryBuilder
    }

    return createQuery(queryBuilder)
        .setParameter("shortId", holdingIdentityShortHash.uppercase())
        .setMaxResults(1)
        .resultList
        .singleOrNull()
}
