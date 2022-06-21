package net.corda.virtualnode.write.db.impl.writer

import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntityKey
import net.corda.orm.utils.transaction
import net.corda.v5.base.exceptions.CordaRuntimeException
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
    internal fun getCPIMetadata(cpiFileChecksum: String): CpiMetadataLite? {
        val cpiMetadataEntity = entityManagerFactory.transaction {
            val foundCpi = it.createQuery(
                "SELECT cpi FROM CpiMetadataEntity cpi " +
                        "WHERE upper(cpi.fileChecksum) like :cpiFileChecksum",
                CpiMetadataEntity::class.java
            )
                .setParameter("cpiFileChecksum", "%${cpiFileChecksum.uppercase()}%")
                .resultList
            if (foundCpi.isNotEmpty()) foundCpi[0] else null
        } ?: return null

        val signerSummaryHash = cpiMetadataEntity.signerSummaryHash.let {
            if (it == "") null else SecureHash.create(it)
        }
        val cpiId = CpiIdentifier(cpiMetadataEntity.name, cpiMetadataEntity.version, signerSummaryHash)
        val fileChecksum = SecureHash.create(cpiMetadataEntity.fileChecksum).toHexString()
        return CpiMetadataLite(cpiId, fileChecksum, cpiMetadataEntity.groupId, cpiMetadataEntity.groupPolicy)
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
    internal fun putHoldingIdentity(
        entityManager: EntityManager,
        holdingIdentity: HoldingIdentity,
        connections: VirtualNodeDbConnections
    ) {
        val entity = entityManager.find(HoldingIdentityEntity::class.java, holdingIdentity.id)?.apply {
            update(
                connections.vaultDdlConnectionId,
                connections.vaultDmlConnectionId,
                connections.cryptoDdlConnectionId,
                connections.cryptoDmlConnectionId
            )
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
    internal fun virtualNodeExists(holdingId: HoldingIdentity, cpiId: CpiIdentifier): Boolean {
        return entityManagerFactory
            .transaction {
                val signerSummaryHash = if (cpiId.signerSummaryHash != null) cpiId.signerSummaryHash.toString() else ""
                val hie = it.find(HoldingIdentityEntity::class.java, holdingId.id) ?: return false // TODO throw?
                val key = VirtualNodeEntityKey(hie, cpiId.name, cpiId.version, signerSummaryHash)
                it.find(VirtualNodeEntity::class.java, key) != null
            }
    }

    /**
     * Writes a virtual node to the database.
     * @param holdingId Holding identity
     * @param cpiId CPI identifier
     */
    internal fun putVirtualNode(entityManager: EntityManager, holdingId: HoldingIdentity, cpiId: CpiIdentifier) {
        val signerSummaryHash = cpiId.signerSummaryHash?.toString() ?: ""
        val hie = entityManager.find(HoldingIdentityEntity::class.java, holdingId.id)
            ?: throw CordaRuntimeException("Could not find holding identity") // TODO throw?

        val virtualNodeEntityKey = VirtualNodeEntityKey(hie, cpiId.name, cpiId.version, signerSummaryHash)
        val foundVNode = entityManager.find(VirtualNodeEntity::class.java, virtualNodeEntityKey)
        if (foundVNode == null) {
            entityManager.persist(VirtualNodeEntity(hie, cpiId.name, cpiId.version, signerSummaryHash))
        } else {
            log.debug { "vNode for key already exists: $virtualNodeEntityKey" }
        }
    }


    fun HoldingIdentity.toEntity(connections: VirtualNodeDbConnections?) = HoldingIdentityEntity(
        this.id,
        this.hash,
        this.x500Name,
        this.groupId,
        connections?.vaultDdlConnectionId,
        connections?.vaultDmlConnectionId,
        connections?.cryptoDdlConnectionId,
        connections?.cryptoDmlConnectionId,
        null
    )
}
