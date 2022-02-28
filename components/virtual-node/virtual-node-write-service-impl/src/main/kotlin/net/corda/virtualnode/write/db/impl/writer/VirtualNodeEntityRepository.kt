package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.virtualnode.datamodel.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntityKey
import net.corda.orm.utils.transaction
import net.corda.packaging.CPI
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity

/** Reads and writes CPIs, holding identities and virtual nodes to and from the cluster database. */
internal class VirtualNodeEntityRepository(dbConnectionManager: DbConnectionManager) {

    private val entityManagerFactory = dbConnectionManager.getClusterEntityManagerFactory()

    /** Stub for reading CPI metadata from the database that returns a dummy value. */
    @Suppress("Unused_parameter", "RedundantNullableReturnType")
    internal fun getCPIMetadata(cpiIdShortHash: String): CPIMetadata? {
        // TODO - Use `dbConnectionManager` to read from DB.
        val summaryHash = SecureHash.create("SHA-256:0000000000000000")
        val cpiId = CPI.Identifier.newInstance("dummy_name", "dummy_version", summaryHash)
        return CPIMetadata(cpiId, "dummy_cpi_id_short_hash", "dummy_mgm_group_id")
    }

    /**
     * Reads a holding identity from the database.
     * @param holdingIdShortHash Holding identity ID (short hash)
     * @return Holding identity for a given ID (short hash) or null if not found
     */
    internal fun getHoldingIdentity(holdingIdShortHash: String): HoldingIdentity? {
        return entityManagerFactory
            .transaction { entityManager ->
                val hidEntity = entityManager.find(HoldingIdentityEntity::class.java, holdingIdShortHash) ?: return null
                HoldingIdentity(hidEntity.x500Name, hidEntity.mgmGroupId)
            }
    }

    /**
     * Writes a holding identity to the database.
     * @param holdingIdentity Holding identity
     */
    internal fun putHoldingIdentity(holdingIdentity: HoldingIdentity) {
        entityManagerFactory
            .transaction {
                val entity = it.find(HoldingIdentityEntity::class.java, holdingIdentity.id)?.apply {
                    update(holdingIdentity.vaultDdlConnectionId,
                        holdingIdentity.vaultDmlConnectionId,
                        holdingIdentity.cryptoDdlConnectionId,
                        holdingIdentity.cryptoDmlConnectionId)
                } ?: HoldingIdentityEntity(
                    holdingIdentity.id,
                    holdingIdentity.hash,
                    holdingIdentity.x500Name,
                    holdingIdentity.groupId,
                    holdingIdentity.vaultDdlConnectionId,
                    holdingIdentity.vaultDmlConnectionId,
                    holdingIdentity.cryptoDdlConnectionId,
                    holdingIdentity.cryptoDmlConnectionId,
                    null
                )
                it.persist(entity)
            }
    }

    /**
     * Checks whether virtual node exists in database.
     * @param holdingId Holding identity
     * @param cpiId CPI identifier
     * @return true if virtual node exists in database, false otherwise
     */
    internal fun virtualNodeExists(holdingId: HoldingIdentity, cpiId: CPI.Identifier): Boolean {
        return entityManagerFactory
            .transaction {
                val key = VirtualNodeEntityKey(holdingId.id, cpiId.name, cpiId.version, cpiId.signerSummaryHash.toString())
                it.find(VirtualNodeEntity::class.java, key) != null
            }
    }

    /**
     * Writes a virtual node to the database.
     * @param holdingId Holding identity
     * @param cpiId CPI identifier
     */
    @Suppress("Unused_parameter")
    internal fun putVirtualNode(holdingId: HoldingIdentity, cpiId: CPI.Identifier) {
        entityManagerFactory
            .transaction {
                val key = VirtualNodeEntityKey(holdingId.id, cpiId.name, cpiId.version, cpiId.signerSummaryHash.toString())
                it.find(VirtualNodeEntity::class.java, key) ?:
                    it.persist(VirtualNodeEntity(holdingId.id, cpiId.name, cpiId.version, cpiId.signerSummaryHash.toString()))
            }
    }
}