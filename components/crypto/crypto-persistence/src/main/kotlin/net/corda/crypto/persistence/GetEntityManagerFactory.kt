package net.corda.crypto.persistence

import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.ShortHash
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.metrics.CordaMetrics
import net.corda.orm.JpaEntitiesRegistry
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import javax.persistence.EntityManagerFactory

fun getEntityManagerFactory(
    tenantId: String,
    dbConnectionManager: DbConnectionManager,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    jpaEntitiesRegistry: JpaEntitiesRegistry,
): EntityManagerFactory {
    return if (CryptoTenants.isClusterTenant(tenantId)) {
        // tenantID is P2P; let's obtain a connection to our cluster Crypto database
        getClusterDbEntityManagerFactory(dbConnectionManager)
    } else {
        // tenantID is a virtual node; let's connect to one of the virtual node Crypto databases
        getVNodeDbEntityManagerFactory(tenantId, dbConnectionManager, virtualNodeInfoReadService, jpaEntitiesRegistry)
    }

}

fun getClusterDbEntityManagerFactory(dbConnectionManager: DbConnectionManager): EntityManagerFactory {
    return CordaMetrics.Metric.Crypto.EntityManagerFactoryCreationTimer.builder()
        .build()
        .recordCallable {
            dbConnectionManager.getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        }!!
}

private fun getVNodeDbEntityManagerFactory(
    tenantId: String,
    dbConnectionManager: DbConnectionManager,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    jpaEntitiesRegistry: JpaEntitiesRegistry,
): EntityManagerFactory {
    return CordaMetrics.Metric.Crypto.EntityManagerFactoryCreationTimer.builder()
        .withTag(CordaMetrics.Tag.Tenant, tenantId)
        .build()
        .recordCallable {
            // tenantID is a virtual node; let's connect to one of the virtual node Crypto databases
            dbConnectionManager.getOrCreateEntityManagerFactory(
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
                    ),
                // disabling client side connection pool means we can cache the EMFs without "hogging" DB
                //  connections. This should be ok because signing requests will usually be served from keys
                //  in cache.
                enablePool = false
            )
        }!!
}
