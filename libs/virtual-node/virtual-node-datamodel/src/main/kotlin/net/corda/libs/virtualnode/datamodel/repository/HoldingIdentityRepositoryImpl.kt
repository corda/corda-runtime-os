package net.corda.libs.virtualnode.datamodel.repository

import net.corda.crypto.core.ShortHash
import net.corda.libs.virtualnode.datamodel.entities.HoldingIdentityEntity
import net.corda.virtualnode.HoldingIdentity
import java.util.*
import javax.persistence.EntityManager

class HoldingIdentityRepositoryImpl: HoldingIdentityRepository {
    /**
     * Find [HoldingIdentity] for given [ShortHash].
     */
    override fun find(entityManager: EntityManager, shortHash: ShortHash): HoldingIdentity? {
        return entityManager.find(HoldingIdentityEntity::class.java, shortHash.value)?.toHoldingIdentity()
    }

    /**
     * Writes a holding identity to the database.
     */
    @Suppress("LongParameterList")
    override fun put(
        entityManager: EntityManager,
        holdingIdentity: HoldingIdentity,
    ) {
        val entity = entityManager.find(HoldingIdentityEntity::class.java, holdingIdentity.shortHash.value) ?: HoldingIdentityEntity(
            holdingIdentity.shortHash.value,
            holdingIdentity.fullHash,
            holdingIdentity.x500Name.toString(),
            holdingIdentity.groupId,
            null
        )
        entityManager.persist(entity)
    }
}
