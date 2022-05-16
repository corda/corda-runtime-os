package net.corda.virtualnode.write.db.impl.writer

import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.virtualnode.datamodel.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntityKey
import net.corda.orm.utils.transaction
import net.corda.libs.packaging.Cpi
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/** Reads and writes CPIs, holding identities and virtual nodes to and from the cluster database. */
internal class VirtualNodeEntityRepository(private val entityManagerFactory: EntityManagerFactory) {

    private companion object {
        val log = contextLogger()
    }

    /** Reads CPI metadata from the database. */
    internal fun getCPIMetadata(cpiFileChecksum: String): CPIMetadata? {
        val cpiMetadataEntity = entityManagerFactory.transaction {
            val foundCpi = it.createQuery(
                "SELECT cpi FROM CpiMetadataEntity cpi " +
                        "WHERE upper(cpi.fileChecksum) like :cpiFileChecksum",
                CpiMetadataEntity::class.java)
                .setParameter("cpiFileChecksum", "%${cpiFileChecksum.uppercase()}%")
                .resultList
            if (foundCpi.isNotEmpty()) foundCpi[0] else null
        } ?: return null

        val signerSummaryHash = cpiMetadataEntity.signerSummaryHash.let {
            if (it == "") null else SecureHash.create(it)
        }
        val cpiId = Cpi.Identifier.newInstance(cpiMetadataEntity.name, cpiMetadataEntity.version, signerSummaryHash)
        val fileChecksum = SecureHash.create(cpiMetadataEntity.fileChecksum).toHexString()
        return CPIMetadata(cpiId, fileChecksum, cpiMetadataEntity.groupId)
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
    internal fun virtualNodeExists(holdingId: HoldingIdentity, cpiId: Cpi.Identifier): Boolean {
        return entityManagerFactory
            .transaction {
                val signerSummaryHash = if (cpiId.signerSummaryHash != null)  cpiId.signerSummaryHash.toString() else ""
                val key = VirtualNodeEntityKey(holdingId.id, cpiId.name, cpiId.version, signerSummaryHash)
                it.find(VirtualNodeEntity::class.java, key) != null
            }
    }

    /**
     * Writes a virtual node to the database.
     * @param holdingId Holding identity
     * @param cpiId CPI identifier
     */
    internal fun putVirtualNode(entityManager: EntityManager, holdingId: HoldingIdentity, cpiId: Cpi.Identifier) {
        val signerSummaryHash = cpiId.signerSummaryHash?.toString() ?: ""
        val key = VirtualNodeEntityKey(holdingId.id, cpiId.name, cpiId.version, signerSummaryHash)
        val foundVNode = entityManager.find(VirtualNodeEntity::class.java, key)
        if (foundVNode == null) {
            entityManager.persist(VirtualNodeEntity(holdingId.id, cpiId.name, cpiId.version, signerSummaryHash))
        } else {
            log.debug { "vNode for key already exists: $key" }
        }
    }
}
