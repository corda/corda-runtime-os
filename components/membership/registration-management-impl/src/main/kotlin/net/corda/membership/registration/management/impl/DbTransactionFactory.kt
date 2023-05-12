package net.corda.membership.registration.management.impl

import net.corda.data.identity.HoldingIdentity
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import javax.persistence.EntityManager

internal class DbTransactionFactory(
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val dbConnectionManager: DbConnectionManager,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
) {
    fun <R> transaction(holdingIdentity: HoldingIdentity, block: (EntityManager) -> R): R {
        val hash = holdingIdentity.toCorda().shortHash
        val virtualNodeInfo = virtualNodeInfoReadService.getByHoldingIdentityShortHash(hash)
            ?: throw CordaRuntimeException(
                "Virtual node info can't be retrieved for " +
                    "holding identity $holdingIdentity",
            )
        return dbConnectionManager.createEntityManagerFactory(
            connectionId = virtualNodeInfo.vaultDmlConnectionId,
            entitiesSet = jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
                ?: throw CordaRuntimeException("persistenceUnitName ${CordaDb.Vault.persistenceUnitName} is not registered."),
        ).use { factory ->
            factory.transaction(block)
        }
    }
}
