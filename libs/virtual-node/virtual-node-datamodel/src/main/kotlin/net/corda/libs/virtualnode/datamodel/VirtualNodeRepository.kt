package net.corda.libs.virtualnode.datamodel

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.orm.utils.transaction
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.VirtualNodeState
import java.util.stream.Stream
import javax.persistence.EntityManager

// using an interface allows us to easily mock/test
interface VirtualNodeRepository {
    fun findAll(entityManager: EntityManager): Stream<VirtualNodeInfo>
    fun find(entityManager: EntityManager, holdingIdentityShortHash: ShortHash): VirtualNodeInfo?
    fun put(entityManager: EntityManager, holdingId: HoldingIdentity, cpiId: CpiIdentifier)
    fun updateVirtualNodeState(entityManager: EntityManager, holdingIdentityShortHash: String, newState: VirtualNodeState): VirtualNodeInfo
}

class VirtualNodeRepositoryImpl : VirtualNodeRepository {
    /**
     * If you change this function ensure that you check the generated SQL from
     * hibnernate in the "findAll test" in
     */
    override fun findAll(entityManager: EntityManager): Stream<VirtualNodeInfo> {
        val query = entityManager.criteriaBuilder!!.createQuery(VirtualNodeEntity::class.java)!!
        val root = query.from(VirtualNodeEntity::class.java)
        root.fetch<Any, Any>("holdingIdentity")
        query.select(root)

        return entityManager.createQuery(query).resultStream.map { it.toVirtualNodeInfo() }
    }

    override fun find(entityManager: EntityManager, holdingIdentityShortHash: ShortHash): VirtualNodeInfo? {
        val queryBuilder = with(entityManager.criteriaBuilder!!) {
            val queryBuilder = createQuery(VirtualNodeEntity::class.java)!!
            val root = queryBuilder.from(VirtualNodeEntity::class.java)
            root.fetch<Any, Any>("holdingIdentity")
            queryBuilder.where(
                equal(
                    root.get<HoldingIdentityEntity>("holdingIdentity").get<String>("holdingIdentityShortHash"),
                    parameter(String::class.java, "shortId")
                )
            ).orderBy(desc(root.get<String>("cpiVersion")))
            queryBuilder
        }

        return entityManager.createQuery(queryBuilder)
            .setParameter("shortId", holdingIdentityShortHash.value.uppercase())
            .setMaxResults(1)
            .resultList
            .singleOrNull()
            ?.toVirtualNodeInfo()
    }

    /**
     * Writes a virtual node to the database.
     * @param holdingId Holding identity
     * @param cpiId CPI identifier
     */
    override fun put(entityManager: EntityManager, holdingId: HoldingIdentity, cpiId: CpiIdentifier) {
        val signerSummaryHash = cpiId.signerSummaryHash?.toString() ?: ""
        val hie = entityManager.find(HoldingIdentityEntity::class.java, holdingId.shortHash.value)
            ?: throw CordaRuntimeException("Could not find holding identity")

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
        }
    }

    override fun updateVirtualNodeState(
        entityManager: EntityManager,
        holdingIdentityShortHash: String,
        newState: VirtualNodeState
    ): VirtualNodeInfo {
        entityManager.transaction {
            // Lookup virtual node and grab the latest one based on the cpi Version.
            val latestVirtualNodeInstance = findEntity(entityManager, holdingIdentityShortHash)
                ?: throw VirtualNodeNotFoundException(holdingIdentityShortHash)
            val updatedVirtualNodeInstance = latestVirtualNodeInstance.apply {
                update(newState.name)
            }
            return it.merge(updatedVirtualNodeInstance).toVirtualNodeInfo()
        }
    }

    private fun findEntity(entityManager: EntityManager, holdingIdentityShortHash: String): VirtualNodeEntity? {
        val queryBuilder = with(entityManager.criteriaBuilder!!) {
            val queryBuilder = createQuery(VirtualNodeEntity::class.java)!!
            val root = queryBuilder.from(VirtualNodeEntity::class.java)
            root.fetch<Any, Any>("holdingIdentity")
            queryBuilder.where(
                equal(
                    root.get<HoldingIdentityEntity>("holdingIdentity").get<String>("holdingIdentityShortHash"),
                    parameter(String::class.java, "shortId")
                )
            ).orderBy(desc(root.get<String>("cpiVersion")))
            queryBuilder
        }

        return entityManager.createQuery(queryBuilder)
            .setParameter("shortId", holdingIdentityShortHash.uppercase())
            .setMaxResults(1)
            .resultList
            .singleOrNull()
    }
}

