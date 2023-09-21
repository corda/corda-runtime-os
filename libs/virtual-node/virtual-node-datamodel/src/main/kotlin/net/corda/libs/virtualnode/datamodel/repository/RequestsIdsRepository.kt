package net.corda.libs.virtualnode.datamodel.repository

import net.corda.db.schema.DbSchema.VNODE_PERSISTENCE_REQUEST_ID_TABLE
import java.util.UUID
import javax.persistence.EntityManager

interface RequestsIdsRepository {
    fun persist(requestId: UUID, em: EntityManager)
}

class RequestsIdsRepositoryImpl : RequestsIdsRepository {
    override fun persist(requestId: UUID, em: EntityManager) {
        em.createNativeQuery(
            """
            INSERT INTO {h-schema}$VNODE_PERSISTENCE_REQUEST_ID_TABLE(request_id)
            VALUES (:requestId)
        """.trimIndent()
        ).setParameter("requestId", requestId.toString())
            .executeUpdate()
    }
}