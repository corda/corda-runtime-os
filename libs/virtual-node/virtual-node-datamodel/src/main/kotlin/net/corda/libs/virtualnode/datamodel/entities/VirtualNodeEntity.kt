package net.corda.libs.virtualnode.datamodel.entities

import net.corda.db.schema.DbSchema.CONFIG
import net.corda.db.schema.DbSchema.VIRTUAL_NODE_DB_TABLE
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.OperationalStatus
import java.io.Serializable
import java.time.Instant
import java.util.Objects
import java.util.UUID
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Enumerated
import javax.persistence.EnumType
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
 * @param vaultDDLConnectionId A pointer to the virtual node's vault DDL details in the DB connection table.
 * @param vaultDMLConnectionId A pointer to the virtual node's vault DML details in the DB connection table.
 * @param cryptoDDLConnectionId A pointer to the virtual node's crypto DDL details in the DB connection table.
 * @param cryptoDMLConnectionId A pointer to the virtual node's crypto DML details in the DB connection table.
 * @param uniquenessDDLConnectionId A pointer to the virtual node's crypto DDL details in the DB connection table.
 * @param flowP2pOperationalStatus  The virtual node's ability to communicate with peers, both inbound and outbound.
 * @param flowStartOperationalStatus The virtual node's ability to start new flows.
 * @param flowOperationalStatus The virtual node's ability to run flows, to have checkpoints, to continue in-progress flows.
 * @param vaultDbOperationalStatus The virtual node's ability to perform persistence operations on the virtual node's vault.
 * @param operationInProgress Details of the current operation in progress.
 */
@Entity
@Table(name = VIRTUAL_NODE_DB_TABLE, schema = CONFIG)
@Suppress("LongParameterList")
internal class VirtualNodeEntity(
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

    @OneToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinColumn(name = "operation_in_progress")
    var operationInProgress: VirtualNodeOperationEntity? = null,

    @Column(name = "insert_ts", insertable = false)
    var insertTimestamp: Instant? = null,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,

    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0,
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VirtualNodeEntity

        return Objects.equals(holdingIdentity, other.holdingIdentity)
    }

    override fun hashCode(): Int {
        return holdingIdentity.hashCode()
    }

    fun toVirtualNodeInfo(): VirtualNodeInfo {
        return VirtualNodeInfo(
            holdingIdentity.toHoldingIdentity(),
            CpiIdentifier(cpiName, cpiVersion, SecureHash.parse(cpiSignerSummaryHash)),
            vaultDDLConnectionId,
            vaultDMLConnectionId!!,
            cryptoDDLConnectionId,
            cryptoDMLConnectionId!!,
            uniquenessDDLConnectionId,
            uniquenessDMLConnectionId!!,
            holdingIdentity.hsmConnectionId,
            flowP2pOperationalStatus,
            flowStartOperationalStatus,
            flowOperationalStatus,
            vaultDbOperationalStatus,
            operationInProgress?.requestId,
            entityVersion,
            insertTimestamp!!,
            isDeleted
        )
    }

    fun update(
        flowP2pOperationalStatus: OperationalStatus = this.flowP2pOperationalStatus,
        flowStartOperationalStatus: OperationalStatus = this.flowStartOperationalStatus,
        flowOperationalStatus: OperationalStatus = this.flowOperationalStatus,
        vaultDbOperationalStatus: OperationalStatus = this.vaultDbOperationalStatus,
        vaultDDLConnectionId: UUID? = this.vaultDDLConnectionId,
        vaultDMLConnectionId: UUID? = this.vaultDMLConnectionId,
        cryptoDDLConnectionId: UUID? = this.vaultDDLConnectionId,
        cryptoDMLConnectionId: UUID? = this.cryptoDMLConnectionId,
        uniquenessDDLConnectionId: UUID? = this.uniquenessDDLConnectionId,
        uniquenessDMLConnectionId: UUID? = this.uniquenessDMLConnectionId
    ) {
        this.flowP2pOperationalStatus = flowP2pOperationalStatus
        this.flowStartOperationalStatus = flowStartOperationalStatus
        this.flowOperationalStatus = flowOperationalStatus
        this.vaultDbOperationalStatus = vaultDbOperationalStatus
        this.vaultDDLConnectionId = vaultDDLConnectionId
        this.vaultDMLConnectionId = vaultDMLConnectionId
        this.cryptoDDLConnectionId = cryptoDDLConnectionId
        this.cryptoDMLConnectionId = cryptoDMLConnectionId
        this.uniquenessDDLConnectionId = uniquenessDDLConnectionId
        this.uniquenessDMLConnectionId = uniquenessDMLConnectionId
    }
}
