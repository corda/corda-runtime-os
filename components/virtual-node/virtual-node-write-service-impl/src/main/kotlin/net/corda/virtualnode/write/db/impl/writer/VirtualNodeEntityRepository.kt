package net.corda.virtualnode.write.db.impl.writer

import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntityKey
import net.corda.libs.virtualnode.datamodel.findVirtualNode
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class VirtualNodeNotFoundException(holdingIdentityShortHash: String) :
    Exception("Could not find a virtual node with Id of $holdingIdentityShortHash")

/** Reads and writes CPIs, holding identities and virtual nodes to and from the cluster database. */
internal class VirtualNodeEntityRepository(private val entityManagerFactory: EntityManagerFactory) {

    private companion object {
        val log = contextLogger()
        private const val SHORT_HASH_LENGTH: Int = 12
    }

    /** Reads CPI metadata from the database. */
    internal fun getCpiMetadataByChecksum(cpiFileChecksum: String): CpiMetadataLite? {
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
            if (it == "") null else SecureHash.parse(it)
        }
        val cpiId = CpiIdentifier(cpiMetadataEntity.name, cpiMetadataEntity.version, signerSummaryHash)
        val fileChecksum = SecureHash.parse(cpiMetadataEntity.fileChecksum).toHexString()
        return CpiMetadataLite(cpiId, fileChecksum, cpiMetadataEntity.groupId, cpiMetadataEntity.groupPolicy)
    }

    /** Reads CPI metadata from the database. */
    internal fun getCPIMetadataByNameAndVersion(name: String, version: String): CpiMetadataLite? {
        val cpiMetadataEntity = entityManagerFactory.use {
            it.transaction {
                it.createQuery(
                    "SELECT cpi FROM CpiMetadataEntity cpi " +
                            "WHERE cpi.name = :cpiName "+
                            "AND cpi.version = :cpiVersion ",
                    CpiMetadataEntity::class.java
                )
                    .setParameter("cpiName", name)
                    .setParameter("cpiVersion", version)
                    .singleResult
            }
        }

        val signerSummaryHash = cpiMetadataEntity.signerSummaryHash.let {
            if (it.isBlank()) null else SecureHash.parse(it)
        }
        val cpiId = CpiIdentifier(cpiMetadataEntity.name, cpiMetadataEntity.version, signerSummaryHash)
        val fileChecksum = SecureHash.parse(cpiMetadataEntity.fileChecksum).toHexString()
        return CpiMetadataLite(cpiId, fileChecksum, cpiMetadataEntity.groupId, cpiMetadataEntity.groupPolicy)
    }

    /**
     * Reads a holding identity from the database.
     * @param holdingIdentityShortHash Holding identity ID (short hash)
     * @return Holding identity for a given ID (short hash) or null if not found
     */
    internal fun getHoldingIdentity(holdingIdentityShortHash: ShortHash): HoldingIdentity? {
        return entityManagerFactory
            .transaction { entityManager ->
                val hidEntity = entityManager.find(HoldingIdentityEntity::class.java, holdingIdentityShortHash.value)
                    ?: return null
                HoldingIdentity(MemberX500Name.parse(hidEntity.x500Name), hidEntity.mgmGroupId)
            }
    }

    /**
     * Reads a holding identity from the database and returns the holding identity with vault schema connection details.
     *
     * @param holdingIdentityShortHash Holding identity ID (short hash)
     * @return Holding identity with vault connections
     */
    internal fun getHoldingIdentityAndConnections(holdingIdentityShortHash: ShortHash): HoldingIdentityAndConnections? {
        return entityManagerFactory
            .transaction { entityManager ->
                val hidEntity = entityManager.find(HoldingIdentityEntity::class.java, holdingIdentityShortHash.value)
                    ?: return null
                HoldingIdentityAndConnections(
                    HoldingIdentity(
                        MemberX500Name.parse(hidEntity.x500Name),
                        hidEntity.mgmGroupId
                    ),
                    VirtualNodeDbConnections(
                        hidEntity.vaultDDLConnectionId,
                        hidEntity.vaultDMLConnectionId!!, // can these ever be null in db?
                        hidEntity.cryptoDDLConnectionId,
                        hidEntity.cryptoDMLConnectionId!!, // can these ever be null in db?
                        hidEntity.uniquenessDDLConnectionId,
                        hidEntity.uniquenessDMLConnectionId!!, // can these ever be null in db?
                    )
                )
            }
    }

    data class HoldingIdentityAndConnections(
        val holdingId: HoldingIdentity,
        val connections: VirtualNodeDbConnections
    )

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
        val entity = entityManager.find(HoldingIdentityEntity::class.java, holdingIdentity.shortHash.value)?.apply {
            update(
                connections.vaultDdlConnectionId,
                connections.vaultDmlConnectionId,
                connections.cryptoDdlConnectionId,
                connections.cryptoDmlConnectionId,
                connections.uniquenessDdlConnectionId,
                connections.uniquenessDmlConnectionId
            )
        } ?: HoldingIdentityEntity(
            holdingIdentity.shortHash.value,
            holdingIdentity.fullHash,
            holdingIdentity.x500Name.toString(),
            holdingIdentity.groupId,
            connections.vaultDdlConnectionId,
            connections.vaultDmlConnectionId,
            connections.cryptoDdlConnectionId,
            connections.cryptoDmlConnectionId,
            connections.uniquenessDdlConnectionId,
            connections.uniquenessDmlConnectionId,
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
        return entityManagerFactory.use { em ->
            em.transaction {
                val signerSummaryHash = if (cpiId.signerSummaryHash != null) cpiId.signerSummaryHash.toString() else ""
                val hie = it.find(HoldingIdentityEntity::class.java, holdingId.shortHash.value) ?: return false // TODO throw?
                val key = VirtualNodeEntityKey(hie, cpiId.name, cpiId.version, signerSummaryHash)
                it.find(VirtualNodeEntity::class.java, key) != null
            }
        }
    }

    /**
     * Get a virtual node by its holding identity short hash.
     *
     * @param holdingId Holding identity
     * @return lightweight representation of the virtual node
     */
    internal fun findByHoldingIdentity(holdingId: HoldingIdentity): VirtualNodeLite {
        return entityManagerFactory.use { em ->
            em.transaction {
                val queryString = "FROM ${VirtualNodeEntity::class.java.simpleName} v " +
                        "WHERE v.holdingIdentity.holdingIdentityShortHash = :holdingIdentityShortHash"
                val holdingIdentityList = em.createQuery(queryString, VirtualNodeEntity::class.java)
                    .setParameter("holdingIdentityShortHash", holdingId.shortHash.value)
                    .resultList
                if(holdingIdentityList.size > 1) {
                    throw CordaRuntimeException("More than one virtual node for the given holding identity ${holdingId.shortHash.value}")
                }
                holdingIdentityList.first().toVirtualNodeLite()
            }
        }
    }

    private fun VirtualNodeEntity.toVirtualNodeLite() = VirtualNodeLite(
        holdingIdentity.holdingIdentityShortHash,
        cpiName,
        cpiVersion,
        cpiSignerSummaryHash
    )

    data class VirtualNodeLite(
        val holdingIdentityShortHash: String,
        val cpiName: String,
        val cpiVersion: String,
        val cpiSignerSummaryHash: String,
    )

    internal fun updateVirtualNodeCpi(holdingId: HoldingIdentity, cpiId: CpiIdentifier) {
        return entityManagerFactory.use { em ->
            em.transaction {
                val signerSummaryHash = if (cpiId.signerSummaryHash != null) cpiId.signerSummaryHash.toString() else ""
                val hie = it.find(HoldingIdentityEntity::class.java, holdingId.shortHash.value)
                val key = VirtualNodeEntityKey(hie, cpiId.name, cpiId.version, signerSummaryHash)
                val vNodeEntity = it.find(VirtualNodeEntity::class.java, key)
                vNodeEntity.cpiName = cpiId.name
                vNodeEntity.cpiVersion = cpiId.version
                vNodeEntity.cpiSignerSummaryHash = signerSummaryHash
                em.merge(vNodeEntity)
            }
        }
    }

    /**
     * Writes a virtual node to the database.
     * @param holdingId Holding identity
     * @param cpiId CPI identifier
     */
    internal fun putVirtualNode(entityManager: EntityManager, holdingId: HoldingIdentity, cpiId: CpiIdentifier) {
        val signerSummaryHash = cpiId.signerSummaryHash?.toString() ?: ""
        val hie = entityManager.find(HoldingIdentityEntity::class.java, holdingId.shortHash.value)
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
                    VirtualNodeInfo.DEFAULT_INITIAL_STATE.name
                )
            )
        } else {
            log.debug { "vNode for key already exists: $virtualNodeEntityKey" }
        }
    }

    internal fun setVirtualNodeState(entityManager: EntityManager, holdingIdentityShortHash: String, newState: String): VirtualNodeEntity {
        entityManager.transaction {
            // Lookup virtual node and grab the latest one based on the cpi Version.
            val latestVirtualNodeInstance = it.findVirtualNode(holdingIdentityShortHash)
                ?: throw VirtualNodeNotFoundException(holdingIdentityShortHash)
            val updatedVirtualNodeInstance = latestVirtualNodeInstance.apply {
                update(newState)
            }
            return it.merge(updatedVirtualNodeInstance)
        }
    }

    fun HoldingIdentity.toEntity(connections: VirtualNodeDbConnections?) = HoldingIdentityEntity(
        this.shortHash.value,
        this.fullHash,
        this.x500Name.toString(),
        this.groupId,
        connections?.vaultDdlConnectionId,
        connections?.vaultDmlConnectionId,
        connections?.cryptoDdlConnectionId,
        connections?.cryptoDmlConnectionId,
        connections?.uniquenessDdlConnectionId,
        connections?.uniquenessDmlConnectionId,
        null
    )
}
