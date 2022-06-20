package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.transaction
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

interface PersistenceHandler<REQUEST> {
    fun invoke(context: MembershipRequestContext, request: REQUEST): Any?
}

abstract class BasePersistenceHandler<REQUEST>(
    private val persistenceHandlerServices: PersistenceHandlerServices
) : PersistenceHandler<REQUEST> {

    companion object {
        val logger = contextLogger()
    }

    private val dbConnectionManager get() = persistenceHandlerServices.dbConnectionManager
    private val jpaEntitiesRegistry get() = persistenceHandlerServices.jpaEntitiesRegistry
    private val virtualNodeInfoReadService get() = persistenceHandlerServices.virtualNodeInfoReadService
    val clock get() = persistenceHandlerServices.clock
    val cordaAvroSerializationFactory get() = persistenceHandlerServices.cordaAvroSerializationFactory
    val memberInfoFactory get() = persistenceHandlerServices.memberInfoFactory

    fun <R> transaction(holdingIdentityId: String, block: (EntityManager) -> R): R {
        val virtualNodeInfo = virtualNodeInfoReadService.getById(holdingIdentityId)
            ?: throw MembershipPersistenceException(
                "Virtual node info can't be retrieved for " +
                        "holding identity ID $holdingIdentityId"
            )

        return getEntityManagerFactory(virtualNodeInfo).transaction(block)
    }

    private fun getEntityManagerFactory(info: VirtualNodeInfo): EntityManagerFactory {
        return dbConnectionManager.createEntityManagerFactory(
            info.vaultDmlConnectionId,
            jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
                ?: throw java.lang.IllegalStateException(
                    "persistenceUnitName ${CordaDb.Vault.persistenceUnitName} is not registered."
                )
        )
    }
}

data class PersistenceHandlerServices(
    val clock: Clock,
    val dbConnectionManager: DbConnectionManager,
    val jpaEntitiesRegistry: JpaEntitiesRegistry,
    val memberInfoFactory: MemberInfoFactory,
    val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    val virtualNodeInfoReadService: VirtualNodeInfoReadService
)