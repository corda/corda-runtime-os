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
import net.corda.virtualnode.VirtualNodeInfo
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class VirtualNodeNotFoundException(holdingIdShortHash: String) :
    Exception("Could not find a virtual node with Id of $holdingIdShortHash")

/** Reads and writes CPIs, holding identities and virtual nodes to and from the cluster database. */
internal class VirtualNodeEntityRepository(private val entityManagerFactory: EntityManagerFactory) {

    private companion object {
        val log = contextLogger()
        private const val SHORT_HASH_LENGTH: Int = 12
    }

    /** Reads CPI metadata from the database. */
    internal fun getCPIMetadata(cpiFileChecksum: String): CpiMetadataLite? {
        if (cpiFileChecksum.isBlank()) {
            log.warn("CPI file checksum cannot be empty")
            return null
        }

        if (cpiFileChecksum.length < SHORT_HASH_LENGTH) {
            log.warn("CPI file checksum must be at least $SHORT_HASH_LENGTH characters")
            return null
        }

        val cpiMetadataEntity = entityManagerFactory.transaction {
            val foundCpi = it.createQuery(
                "SELECT cpi FROM CpiMetadataEntity cpi " +
                    "WHERE upper(cpi.fileChecksum) like :cpiFileChecksum ",
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
     * @param holdingIdentityShortHash Holding identity ID (short hash)
     * @return Holding identity for a given ID (short hash) or null if not found
     */
    internal fun getHoldingIdentity(holdingIdentityShortHash: String): HoldingIdentity? {
        return entityManagerFactory
            .transaction { entityManager ->
                val hidEntity = entityManager.find(HoldingIdentityEntity::class.java, holdingIdentityShortHash) ?: return null
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
        val entity = entityManager.find(HoldingIdentityEntity::class.java, holdingIdentity.shortHash)?.apply {
            update(
                connections.vaultDdlConnectionId,
                connections.vaultDmlConnectionId,
                connections.cryptoDdlConnectionId,
                connections.cryptoDmlConnectionId
            )
        } ?: HoldingIdentityEntity(
            holdingIdentity.shortHash,
            holdingIdentity.fullHash,
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
                val hie = it.find(HoldingIdentityEntity::class.java, holdingId.shortHash) ?: return false // TODO throw?
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
        val hie = entityManager.find(HoldingIdentityEntity::class.java, holdingId.shortHash)
            ?: throw CordaRuntimeException("Could not find holding identity") // TODO throw?

        val virtualNodeEntityKey = VirtualNodeEntityKey(hie, cpiId.name, cpiId.version, signerSummaryHash)
        val foundVNode = entityManager.find(VirtualNodeEntity::class.java, virtualNodeEntityKey)
        if (foundVNode == null) {
            entityManager.persist(
                VirtualNodeEntity(
                    hie,
                    cpiId.name,
                    cpiId.version,
                    signerSummaryHash,
                    VirtualNodeInfo.DEFAULT_INITIAL_STATE
                )
            )
        } else {
            log.debug { "vNode for key already exists: $virtualNodeEntityKey" }
        }
    }

    internal fun setVirtualNodeState(entityManager: EntityManager, holdingIdShortHash: String, cpiId: String, newState: String) {
        entityManager.transaction {
            val latestVirtualNodeInstance = it.createQuery(
                "SELECT vnode_instance FROM VirtualNodeEntity vnode_instance " +
                    "WHERE vnode_instance.holdingIdentity.holdingIdentityId = :shortVNodeId " +
                    "AND vnode_instance.cpiId = :cpiId " +
                    "ORDER BY vnode_instance.cpiVersion DESC ",
                VirtualNodeEntity::class.java
            )
                .setParameter("shortVNodeId", holdingIdShortHash)
                .setParameter("shortVNodeId", cpiId)
                .setMaxResults(1)
                .resultList.singleOrNull() ?: throw VirtualNodeNotFoundException(holdingIdShortHash)
            val updatedVirtualNodeInstance = latestVirtualNodeInstance.apply {
                update(
                    latestVirtualNodeInstance.copy(
                        virtualNodeState = newState
                    )
                )
            }
            it.merge(updatedVirtualNodeInstance)
        }
    }

    fun HoldingIdentity.toEntity(connections: VirtualNodeDbConnections?) = HoldingIdentityEntity(
        this.shortHash,
        this.fullHash,
        this.x500Name,
        this.groupId,
        connections?.vaultDdlConnectionId,
        connections?.vaultDmlConnectionId,
        connections?.cryptoDdlConnectionId,
        connections?.cryptoDmlConnectionId,
        null
    )
}
