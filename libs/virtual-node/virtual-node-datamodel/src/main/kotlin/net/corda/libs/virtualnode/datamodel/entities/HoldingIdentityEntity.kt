package net.corda.libs.virtualnode.datamodel.entities

import net.corda.db.schema.DbSchema.HOLDING_IDENTITY_DB_TABLE
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import java.util.Objects
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * The entity for a holding identity in the cluster database.
 *
 * @param holdingIdentityShortHash The short 12-character hash of the holding identity.
 * @param holdingIdentityFullHash The full hash of the holding identity.
 * @param x500Name The X.500 name of the holding identity.
 * @param mgmGroupId The MGM group of the holding identity.
 * @param hsmConnectionId A pointer to the holding identity's entry in the HSM connection table.
 */
@Entity
@Table(name = HOLDING_IDENTITY_DB_TABLE)
@Suppress("LongParameterList")
internal class HoldingIdentityEntity(
    @Id
    @Column(name = "holding_identity_id", nullable = false)
    var holdingIdentityShortHash: String,

    @Column(name = "holding_identity_full_hash", nullable = false)
    var holdingIdentityFullHash: String,

    @Column(name = "x500_name", nullable = false)
    var x500Name: String,

    @Column(name = "mgm_group_id", nullable = false)
    var mgmGroupId: String,

    @Column(name = "hsm_connection_id", nullable = true)
    var hsmConnectionId: UUID?
) {

    fun toHoldingIdentity(): HoldingIdentity {
        return HoldingIdentity(MemberX500Name.parse(x500Name), mgmGroupId)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HoldingIdentityEntity

        return Objects.equals(holdingIdentityShortHash, other.holdingIdentityShortHash)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(holdingIdentityShortHash)
    }
}
