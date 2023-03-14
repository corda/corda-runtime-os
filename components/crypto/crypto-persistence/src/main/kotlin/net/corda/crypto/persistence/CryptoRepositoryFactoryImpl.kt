package net.corda.crypto.persistence

import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.v1schema.V1CryptoRepositoryImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.orm.JpaEntitiesRegistry
import net.corda.virtualnode.read.VirtualNodeInfoReadService


class CryptoRepositoryFactoryImpl
constructor(
    private val dbConnectionManager: DbConnectionManager,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) : CryptoRepositoryFactory {
    override fun create(tenantId: String): CryptoRepository {
        val onCluster = CryptoTenants.isClusterTenant(tenantId)
        val entityManagerFactory = if (onCluster) {
            // tenantID is crypto, P2P or REST; let's obtain connect to our cluster Crypto database 
            dbConnectionManager.getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        } else {
            // tenantID is a virtual node; let's connect to one of the virtual node Crypto databases
            dbConnectionManager.createEntityManagerFactory(
                connectionId = virtualNodeInfoReadService.getByHoldingIdentityShortHash(
                    ShortHash.of(
                        tenantId
                    )
                )?.cryptoDmlConnectionId
                    ?: throw IllegalStateException(
                        "virtual node for $tenantId is not registered."
                    ),
                entitiesSet = jpaEntitiesRegistry.get(CordaDb.Crypto.persistenceUnitName)
                    ?: throw IllegalStateException(
                        "persistenceUnitName ${CordaDb.Crypto.persistenceUnitName} is not registered."
                    )
            )
        }

//        // somehow figure out which version we want, e.g.
//        when (entityManagerFactory.getSchemaVersion()) {
//            1 -> V1CryptoRepositoryImpl(entityManagerFactory)
//            else -> V2CryptoRepositoryImpl(entityManagerFactory)
//        }

        return V1CryptoRepositoryImpl(entityManagerFactory)
    }
}