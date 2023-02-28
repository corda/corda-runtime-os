package net.corda.libs.virtualnode.datamodel.repository

import net.corda.crypto.core.ShortHash
import net.corda.virtualnode.HoldingIdentity
import java.util.*
import javax.persistence.EntityManager

// using an interface allows us to easily mock/test
interface HoldingIdentityRepository {
    fun find(entityManager: EntityManager, shortHash: ShortHash): HoldingIdentity?
    @Suppress("LongParameterList")
    fun put(
        entityManager: EntityManager,
        holdingIdentity: HoldingIdentity
    )
}

