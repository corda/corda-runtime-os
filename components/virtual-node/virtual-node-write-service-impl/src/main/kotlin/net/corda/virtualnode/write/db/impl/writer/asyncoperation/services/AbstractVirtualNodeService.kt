package net.corda.virtualnode.write.db.impl.writer.asyncoperation.services

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.connection.manager.VirtualNodeDbType.CRYPTO
import net.corda.db.connection.manager.VirtualNodeDbType.UNIQUENESS
import net.corda.db.connection.manager.VirtualNodeDbType.VAULT
import net.corda.db.core.DbPrivilege
import net.corda.db.core.DbPrivilege.DDL
import net.corda.db.core.DbPrivilege.DML
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.repository.HoldingIdentityRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDb
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbConnections
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManager

internal abstract class AbstractVirtualNodeService(
    private val dbConnectionManager: DbConnectionManager,
    private val holdingIdentityRepository: HoldingIdentityRepository,
    private val virtualNodeRepository: VirtualNodeRepository,
    private val publisher: Publisher
) {
    private companion object {
        const val PUBLICATION_TIMEOUT_SECONDS = 30L
    }

    fun persistHoldingIdAndVirtualNode(
        holdingIdentity: HoldingIdentity,
        vNodeDbs: Map<VirtualNodeDbType, VirtualNodeDb>,
        cpiId: CpiIdentifier,
        updateActor: String,
        externalMessagingRouteConfig: String?
    ): VirtualNodeDbConnections {
        try {
            return dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().transaction { em ->
                val dbConnections = VirtualNodeDbConnections(
                    vaultDdlConnectionId = persistConnection(em, vNodeDbs, VAULT, DDL, updateActor),
                    vaultDmlConnectionId = persistConnection(em, vNodeDbs, VAULT, DML, updateActor)!!,
                    cryptoDdlConnectionId = persistConnection(em, vNodeDbs, CRYPTO, DDL, updateActor),
                    cryptoDmlConnectionId = persistConnection(em, vNodeDbs, CRYPTO, DML, updateActor)!!,
                    uniquenessDdlConnectionId = persistConnection(em, vNodeDbs, UNIQUENESS, DDL, updateActor),
                    uniquenessDmlConnectionId = persistConnection(em, vNodeDbs, UNIQUENESS, DML, updateActor)
                )

                holdingIdentityRepository.put(em, holdingIdentity)

                virtualNodeRepository.put(
                    em,
                    holdingIdentity,
                    cpiId,
                    dbConnections.vaultDdlConnectionId,
                    dbConnections.vaultDmlConnectionId,
                    dbConnections.cryptoDdlConnectionId,
                    dbConnections.cryptoDmlConnectionId,
                    dbConnections.uniquenessDdlConnectionId,
                    dbConnections.uniquenessDmlConnectionId,
                    externalMessagingRouteConfig = externalMessagingRouteConfig
                )

                dbConnections
            }
        } catch (e: Exception) {
            throw VirtualNodeWriteServiceException(
                "Error persisting virtual node for holding identity $holdingIdentity",
                e
            )
        }
    }

    private fun persistConnection(
        entityManager: EntityManager,
        vNodeDbs: Map<VirtualNodeDbType, VirtualNodeDb>,
        dbType: VirtualNodeDbType,
        dbPrivilege: DbPrivilege,
        updateActor: String
    ): UUID? {
        return vNodeDbs[dbType]?.let { vNodeDb ->
            vNodeDb.dbConnections[dbPrivilege]?.let { dbConnection ->
                dbConnectionManager.putConnection(
                    entityManager,
                    dbConnection.name,
                    dbPrivilege,
                    dbConnection.config,
                    dbConnection.description,
                    updateActor
                )
            }
        }
    }

    @Suppress("SpreadOperator")
    fun publishRecords(records: List<Record<*, *>>) {
        CompletableFuture
            .allOf(*publisher.publish(records).toTypedArray())
            .orTimeout(PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .join()
    }
}
