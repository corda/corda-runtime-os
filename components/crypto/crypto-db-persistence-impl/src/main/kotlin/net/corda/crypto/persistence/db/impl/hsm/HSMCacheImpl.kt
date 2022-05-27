package net.corda.crypto.persistence.db.impl.hsm

import net.corda.crypto.persistence.hsm.HSMCache
import net.corda.crypto.persistence.hsm.HSMCacheActions
import javax.persistence.EntityManagerFactory

class HSMCacheImpl(
    private val entityManagerFactory: EntityManagerFactory
) : HSMCache {
    override fun act(): HSMCacheActions =
        HSMCacheActionsImpl(
            entityManager = entityManagerFactory.createEntityManager()
        )

    override fun close() = Unit
}