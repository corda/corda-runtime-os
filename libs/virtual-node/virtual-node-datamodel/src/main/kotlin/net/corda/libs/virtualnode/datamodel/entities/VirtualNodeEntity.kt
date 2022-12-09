package net.corda.libs.virtualnode.datamodel.entities

import net.corda.db.schema.DbSchema.CONFIG
import net.corda.db.schema.DbSchema.VNODE_INSTANCE_DB_TABLE
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.VirtualNodeState
import java.io.Serializable
import java.time.Instant
import java.util.Objects
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
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
@Suppress("LongParameterList")
internal class VirtualNodeEntity(
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

        return Objects.equals(holdingIdentity, other.holdingIdentity)
                && Objects.equals(cpiName, other.cpiName)
                && Objects.equals(cpiVersion, other.cpiVersion)
                && Objects.equals(cpiSignerSummaryHash, other.cpiSignerSummaryHash)
    }

    override fun hashCode(): Int {
        return Objects.hash(
            holdingIdentity,
            cpiName,
            cpiVersion,
            cpiSignerSummaryHash)
    }

    fun update(newState: String) {
        virtualNodeState = newState
    }

    fun toVirtualNodeInfo(): VirtualNodeInfo {
        return VirtualNodeInfo(
            holdingIdentity.toHoldingIdentity(),
            CpiIdentifier(cpiName, cpiVersion, SecureHash.parse(cpiSignerSummaryHash)),
            holdingIdentity.vaultDDLConnectionId,
            holdingIdentity.vaultDMLConnectionId!!,
            holdingIdentity.cryptoDDLConnectionId,
            holdingIdentity.cryptoDMLConnectionId!!,
            holdingIdentity.uniquenessDDLConnectionId,
            holdingIdentity.uniquenessDMLConnectionId!!,
            holdingIdentity.hsmConnectionId,
            VirtualNodeState.valueOf(virtualNodeState),
            entityVersion,
            insertTimestamp!!,
            isDeleted
        )
    }
}

/** The composite primary key for a virtual node instance. */
@Embeddable
@Suppress("Unused")
internal class VirtualNodeEntityKey(
    private val holdingIdentity: HoldingIdentityEntity,
    private val cpiName: String,
    private val cpiVersion: String,
    private val cpiSignerSummaryHash: String
) : Serializable
