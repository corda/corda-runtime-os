package net.corda.libs.virtualnode.datamodel.repository

import net.corda.libs.virtualnode.datamodel.standaloneentities.PersistenceRequestIdEntity
import java.util.UUID
import javax.persistence.EntityManager

interface RequestsIdsRepository {
    fun put(requestId: UUID, em: EntityManager)
}

class RequestsIdsRepositoryImpl : RequestsIdsRepository {
    override fun put(requestId: UUID, em: EntityManager) {
        em.persist(
            PersistenceRequestIdEntity(
                requestId.toString()
            )
        )
    }
}