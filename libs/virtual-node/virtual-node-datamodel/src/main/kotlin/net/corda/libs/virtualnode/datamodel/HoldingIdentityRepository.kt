package net.corda.libs.virtualnode.datamodel

import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import java.util.*
import javax.persistence.EntityManager

// using an interface allows us to easily mock/test
interface HoldingIdentityRepository {
    fun find(entityManager: EntityManager, shortHash: ShortHash): HoldingIdentity?
    @Suppress("LongParameterList")
    fun put(
        entityManager: EntityManager,
        holdingIdentity: HoldingIdentity,
        vaultDdlConnectionId: UUID?,
        vaultDmlConnectionId: UUID,
        cryptoDdlConnectionId: UUID?,
        cryptoDmlConnectionId: UUID,
        uniquenessDdlConnectionId: UUID?,
        uniquenessDmlConnectionId: UUID?
    )
}

class HoldingIdentityRepositoryImpl: HoldingIdentityRepository {
    /**
     * Find [HoldingIdentity] for given [ShortHash].
     */
    override fun find(entityManager: EntityManager, shortHash: ShortHash): HoldingIdentity? {
        return entityManager.find(HoldingIdentityEntity::class.java, shortHash.value)?.toDTO()
    }

    /**
     * Writes a holding identity to the database.
     */
    @Suppress("LongParameterList")
    override fun put(
        entityManager: EntityManager,
        holdingIdentity: HoldingIdentity,
        vaultDdlConnectionId: UUID?,
        vaultDmlConnectionId: UUID,
        cryptoDdlConnectionId: UUID?,
        cryptoDmlConnectionId: UUID,
        uniquenessDdlConnectionId: UUID?,
        uniquenessDmlConnectionId: UUID?
    ) {
        val entity = entityManager.find(HoldingIdentityEntity::class.java, holdingIdentity.shortHash.value)?.apply {
            update(
                vaultDdlConnectionId,
                vaultDmlConnectionId,
                cryptoDdlConnectionId,
                cryptoDmlConnectionId,
                uniquenessDdlConnectionId,
                uniquenessDmlConnectionId
            )
        } ?: HoldingIdentityEntity(
            holdingIdentity.shortHash.value,
            holdingIdentity.fullHash,
            holdingIdentity.x500Name.toString(),
            holdingIdentity.groupId,
            vaultDdlConnectionId,
            vaultDmlConnectionId,
            cryptoDdlConnectionId,
            cryptoDmlConnectionId,
            uniquenessDdlConnectionId,
            uniquenessDmlConnectionId,
            null
        )
        entityManager.persist(entity)
    }
}