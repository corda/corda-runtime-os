package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntity
import net.corda.packaging.CPI
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity

// TODO - Replace these stubs with implementations that interact with the database.
/** Reads and writes CPIs, holding identities and virtual nodes to and from the cluster database. */
internal class VirtualNodeEntityRepository(private val dbConnectionManager: DbConnectionManager) {
    /** Stub for reading CPI metadata from the database that returns a dummy value. */
    @Suppress("Unused_parameter", "RedundantNullableReturnType")
    internal fun getCPIMetadata(cpiIdShortHash: String): CPIMetadata? {
        val summaryHash = SecureHash.create("SHA-256:0000000000000000")
        val cpiId = CPI.Identifier.newInstance("dummy_name", "dummy_version", summaryHash)
        return CPIMetadata(cpiId, "dummy_cpi_id_short_hash", "dummy_mgm_group_id")
    }

    /** Null-returning stub for reading a holding identity from the database. */
    @Suppress("Unused_parameter")
    internal fun getHoldingIdentity(holdingIdShortHash: String): HoldingIdentity? {
        return null
    }

    /** No-op stub for writing a holding identity to the database. */
    @Suppress("Unused_parameter")
    internal fun putHoldingIdentity(x500Name: String, mgmGroupId: String) = Unit

    /** No-op stub for writing a virtual node to the database. */
    @Suppress("Unused_parameter")
    internal fun putVirtualNode(holdingId: HoldingIdentity, cpiId: CPI.Identifier) {
        // TODO - Store entity in the database.
        VirtualNodeEntity(holdingId.id, cpiId.name, cpiId.version, cpiId.signerSummaryHash.toString())
    }
}