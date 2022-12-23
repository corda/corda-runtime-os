package net.corda.virtualnode.write.db.impl.writer

import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntity
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
import net.corda.libs.virtualnode.datamodel.OperationalStatus
import net.corda.virtualnode.write.db.impl.writer.dto.HoldingIdentityLite
import net.corda.virtualnode.write.db.impl.writer.dto.VirtualNodeLite

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
     * Writes a holding identity to the database.
     *
     * @param entityManager [EntityManager]
     * @param holdingIdentity Holding identity
     */
    internal fun putHoldingIdentity(
        entityManager: EntityManager,
        holdingIdentity: HoldingIdentity
    ) {
        if (entityManager.find(HoldingIdentityEntity::class.java, holdingIdentity.shortHash.value) != null) {
            log.info("Holding identity ${holdingIdentity.shortHash.value} already exists.")
        }

        entityManager.persist(
            HoldingIdentityEntity(
                holdingIdentity.shortHash.value,
                holdingIdentity.fullHash,
                holdingIdentity.x500Name.toString(),
                holdingIdentity.groupId,
                null
            )
        )
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
//                val signerSummaryHash = if (cpiId.signerSummaryHash != null) cpiId.signerSummaryHash.toString() else ""
//                val hie = it.find(HoldingIdentityEntity::class.java, holdingId.shortHash.value) ?: return false // TODO throw?
//                val key = VirtualNodeEntityKey(hie, cpiId.name, cpiId.version, signerSummaryHash)
                it.find(VirtualNodeEntity::class.java, holdingId.shortHash.value) != null
            }
        }
    }

    internal fun getVirtualNode(holdingIdentityShortHash: String): VirtualNodeInfo {
        entityManagerFactory.transaction {
            val virtualNodeEntity = it.findVirtualNode(holdingIdentityShortHash)
                ?: throw VirtualNodeNotFoundException(holdingIdentityShortHash)

            return virtualNodeEntity.run {
                val evaluatedCpiSignerSummaryHash = if (cpiSignerSummaryHash.isEmpty()) null else SecureHash.parse(cpiSignerSummaryHash)
                VirtualNodeInfo(
                    HoldingIdentity(MemberX500Name.parse(holdingIdentity.x500Name), holdingIdentity.mgmGroupId),
                    CpiIdentifier(cpiName, cpiVersion, evaluatedCpiSignerSummaryHash),
                    virtualNodeEntity.vaultDDLConnectionId,
                    virtualNodeEntity.vaultDMLConnectionId!!,
                    virtualNodeEntity.cryptoDDLConnectionId,
                    virtualNodeEntity.cryptoDMLConnectionId!!,
                    virtualNodeEntity.uniquenessDDLConnectionId,
                    virtualNodeEntity.uniquenessDMLConnectionId!!,
                    holdingIdentity.hsmConnectionId,
                    flowP2pOperationalStatus.name,
                    flowStartOperationalStatus.name,
                    flowOperationalStatus.name,
                    vaultDbOperationalStatus.name,
                    operationInProgress?.id,
                    entityVersion,
                    insertTimestamp!!
                )
            }
        }
    }

    /**
     * Get a virtual node by its holding identity short hash.
     *
     * @param holdingId Holding identity
     * @return lightweight representation of the virtual node
     */
    internal fun findByHoldingIdentity(holdingIdShortHash: String): VirtualNodeLite? {
        return entityManagerFactory.use { em ->
            em.transaction {
                em.find(VirtualNodeEntity::class.java, holdingIdShortHash)?.toVirtualNodeLite()
            }
        }
    }

    private fun VirtualNodeEntity.toVirtualNodeLite() = VirtualNodeLite(
        holdingIdentityId,
        HoldingIdentityLite(
            holdingIdentity.x500Name,
            holdingIdentity.mgmGroupId,
            holdingIdentity.hsmConnectionId?.toString()
        ),
        cpiName,
        cpiVersion,
        cpiSignerSummaryHash,
        flowP2pOperationalStatus.name,
        flowStartOperationalStatus.name,
        flowOperationalStatus.name,
        vaultDbOperationalStatus.name,
        vaultDDLConnectionId?.toString(),
        vaultDMLConnectionId?.toString(),
        cryptoDDLConnectionId?.toString(),
        cryptoDMLConnectionId?.toString(),
        uniquenessDDLConnectionId?.toString(),
        uniquenessDMLConnectionId?.toString(),
        operationInProgress?.toString(),
        entityVersion,
        insertTimestamp
    )

    internal fun updateVirtualNodeCpi(holdingIdShortHash: String, cpiId: CpiIdentifier): VirtualNodeLite {
        return entityManagerFactory.use { em ->
            em.transaction {
                val signerSummaryHash = cpiId.signerSummaryHash?.toString() ?: ""
                val vNodeEntity = it.find(VirtualNodeEntity::class.java, holdingIdShortHash)
                vNodeEntity.cpiName = cpiId.name
                vNodeEntity.cpiVersion = cpiId.version
                vNodeEntity.cpiSignerSummaryHash = signerSummaryHash
                em.merge(vNodeEntity)
                vNodeEntity.toVirtualNodeLite()
            }
        }
    }

    /**
     * Writes a virtual node to the database.
     * @param holdingId Holding identity
     * @param cpiId CPI identifier
     */
    internal fun putVirtualNode(
        entityManager: EntityManager,
        holdingId: HoldingIdentity,
        cpiId: CpiIdentifier,
        dbConnections: VirtualNodeDbConnections
    ) {
        val signerSummaryHash = cpiId.signerSummaryHash?.toString() ?: ""
        val hie = entityManager.find(HoldingIdentityEntity::class.java, holdingId.shortHash.value)
            ?: throw CordaRuntimeException("Could not find holding identity") // TODO throw?

//        val virtualNodeEntityKey = VirtualNodeEntityKey(hie, cpiId.name, cpiId.version, signerSummaryHash)
        val foundVNode = entityManager.find(VirtualNodeEntity::class.java, holdingId.shortHash.value)
        if (foundVNode == null) {
            entityManager.persist(
                VirtualNodeEntity(
                    holdingId.shortHash.value,
                    hie,
                    cpiId.name,
                    cpiId.version,
                    signerSummaryHash,
                    OperationalStatus.ACTIVE,
                    OperationalStatus.ACTIVE,
                    OperationalStatus.ACTIVE,
                    OperationalStatus.ACTIVE,
                    dbConnections.vaultDdlConnectionId,
                    dbConnections.vaultDmlConnectionId,
                    dbConnections.cryptoDdlConnectionId,
                    dbConnections.cryptoDmlConnectionId,
                    dbConnections.uniquenessDdlConnectionId,
                    dbConnections.uniquenessDmlConnectionId
                )
            )
        } else {
            log.debug { "vNode for key already exists: ${holdingId.shortHash.value}" }
        }
    }

    internal fun setVirtualNodeState(entityManager: EntityManager, holdingIdentityShortHash: String, newState: String): VirtualNodeEntity {
        entityManager.transaction {
            // Lookup virtual node and grab the latest one based on the cpi Version.
            val latestVirtualNodeInstance = it.findVirtualNode(holdingIdentityShortHash)
                ?: throw VirtualNodeNotFoundException(holdingIdentityShortHash)

            when(newState) {
                "maintenance" -> latestVirtualNodeInstance.applyStrictMaintenanceMode()
                "active" -> latestVirtualNodeInstance.attemptTransitionToActive()
                else -> throw CordaRuntimeException("Virtual node state $newState not allowed.")
            }

            return it.merge(latestVirtualNodeInstance)
        }
    }

    private fun VirtualNodeEntity.attemptTransitionToActive() {
        checkCanTransitionVaultDb()
        vaultDbOperationalStatus = OperationalStatus.ACTIVE
        flowP2pOperationalStatus = OperationalStatus.ACTIVE
        flowStartOperationalStatus = OperationalStatus.ACTIVE
        flowOperationalStatus = OperationalStatus.ACTIVE
    }

    private fun checkCanTransitionVaultDb() {
        // Load the changelog file from the current CPI (cpk_db_change_log).
        //Use liquibase getChangeSetStatuses() API to determine if the changelog file contains changesets that have not yet been run on this virtual node.
        //Reports changesets that are missing from the current vnode_vault_{holdingId} databasechangelog table.
        //Use liquibase listUnexpectedChangesets() API to determine if current schema has changesets applied that are ahead of the current CPI.
        //Reports changesets that are in the vnode_vault_{holdingId} databasechangelog table but aren't in the changelog file.
        //Only when these two API calls reveal that the current db vault schema is in line with the current CPI will we allow transition.
    }

    private fun VirtualNodeEntity.applyStrictMaintenanceMode() {
        flowP2pOperationalStatus = OperationalStatus.INACTIVE
        flowStartOperationalStatus = OperationalStatus.INACTIVE
        flowOperationalStatus = OperationalStatus.INACTIVE
        vaultDbOperationalStatus = OperationalStatus.INACTIVE
    }
}
