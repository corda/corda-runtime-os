package net.corda.libs.virtualnode.datamodel.repository

import net.corda.db.schema.DbSchema.VNODE_PERSISTENCE_REQUEST_ID_TABLE
import java.util.UUID
import javax.persistence.EntityManager

interface RequestsIdsRepository {
    fun persist(requestId: UUID, em: EntityManager)

    fun deleteRequestsOlderThan(intervalInSeconds: Long, em: EntityManager)
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

    override fun deleteRequestsOlderThan(intervalInSeconds: Long, em: EntityManager) {
        em.createNativeQuery(
            """
                DELETE FROM {h-schema}$VNODE_PERSISTENCE_REQUEST_ID_TABLE
                WHERE insert_ts < NOW() - INTERVAL '1' SECOND * :intervalInSeconds 
            """.trimIndent()
        ).setParameter("intervalInSeconds", intervalInSeconds)
            .executeUpdate()
    }
}