package net.corda.libs.virtualnode.datamodel.repository

import net.corda.db.core.utils.transaction
import net.corda.db.schema.DbSchema.VNODE_PERSISTENCE_REQUEST_ID_TABLE
import java.util.UUID
import javax.persistence.EntityManager
import javax.sql.DataSource

interface RequestsIdsRepository {
    fun persist(requestId: UUID, em: EntityManager)

    fun deleteRequestsOlderThan(intervalInSeconds: Long, ds: DataSource)
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

    override fun deleteRequestsOlderThan(intervalInSeconds: Long, ds: DataSource) {
        ds.connection.transaction { connection ->
            connection.prepareStatement(
                """
                DELETE FROM $VNODE_PERSISTENCE_REQUEST_ID_TABLE
                WHERE insert_ts < NOW() - INTERVAL '1' SECOND * ? 
                """.trimIndent()
            ).also {
                it.setLong(1, intervalInSeconds)
            }.executeUpdate()
        }
    }
}
