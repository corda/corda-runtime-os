package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.virtualnode.datamodel.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntityKey
import net.corda.orm.utils.transaction
import net.corda.packaging.CPI
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import javax.persistence.EntityManager

/** Reads and writes CPIs, holding identities and virtual nodes to and from the cluster database. */
internal class VirtualNodeEntityRepository(dbConnectionManager: DbConnectionManager) {

    private val entityManagerFactory = dbConnectionManager.getClusterEntityManagerFactory()

    /** Stub for reading CPI metadata from the database that returns a dummy value. */
    @Suppress("Unused_parameter", "RedundantNullableReturnType")
    internal fun getCPIMetadata(cpiIdShortHash: String): CPIMetadata? {
        val cpis = entityManagerFactory.transaction { em ->
            em.createQuery(
                // match either full hash or partial
                "SELECT cpi FROM CpiMetadataEntity cpi WHERE fileChecksum LIKE '$cpiIdShortHash%'",
                CpiMetadataEntity::class.java)
                .resultList
        }

        if(cpis.size != 1)
            return null

        val signerSummaryHash = SecureHash.create(cpis[0].signerSummaryHash)
        val cpiId = CPI.Identifier.newInstance(cpis[0].name, cpis[0].version, signerSummaryHash)
        return CPIMetadata(cpiId, cpiIdShortHash, cpis[0].groupId)
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
     *
     * @param entityManager [EntityManager]
     * @param holdingIdentity Holding identity
     */
    internal fun putHoldingIdentity(entityManager: EntityManager, holdingIdentity: HoldingIdentity, connections: VirtualNodeDbConnections) {
            val entity = entityManager.find(HoldingIdentityEntity::class.java, holdingIdentity.id)?.apply {
                update(connections.vaultDdlConnectionId,
                    connections.vaultDmlConnectionId,
                    connections.cryptoDdlConnectionId,
                    connections.cryptoDmlConnectionId)
            } ?: HoldingIdentityEntity(
                holdingIdentity.id,
                holdingIdentity.hash,
                holdingIdentity.x500Name,
                holdingIdentity.groupId,
                connections.vaultDdlConnectionId,
                connections.vaultDmlConnectionId,
                connections.cryptoDdlConnectionId,
                connections.cryptoDmlConnectionId,
                null
            )
        entityManager.persist(entity)
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
    internal fun putVirtualNode(entityManager: EntityManager, holdingId: HoldingIdentity, cpiId: CPI.Identifier) {
        val key = VirtualNodeEntityKey(holdingId.id, cpiId.name, cpiId.version, cpiId.signerSummaryHash.toString())
        entityManager.find(VirtualNodeEntity::class.java, key) ?:
        entityManager.persist(VirtualNodeEntity(holdingId.id, cpiId.name, cpiId.version, cpiId.signerSummaryHash.toString()))
    }
}