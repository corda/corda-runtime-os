package net.corda.crypto.persistence.db.impl.hsm

import net.corda.crypto.persistence.hsm.HSMStore
import net.corda.crypto.persistence.hsm.HSMStoreActions
import javax.persistence.EntityManagerFactory

class HSMStoreImpl(
    private val entityManagerFactory: EntityManagerFactory
) : HSMStore {
    override fun act(): HSMStoreActions =
        HSMStoreActionsImpl(
            entityManager = entityManagerFactory.createEntityManager()
        )

    override fun close() = Unit
}