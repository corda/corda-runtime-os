package net.corda.crypto.persistence

import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.CryptoTenants.REST
import net.corda.crypto.core.ShortHash
import net.corda.db.connection.manager.DbConnectionManager

import net.corda.db.schema.CordaDb
import net.corda.orm.JpaEntitiesRegistry
import org.osgi.service.component.annotations.Reference

//class CryptoRepositoryFactoryImpl(
//    @Reference(service = DbConnectionManager::class)
//    dbConnectionManager: DbConnectionManager,
//    @Reference(service = JpaEntitiesRegistry::class)
//    jpaEntitiesRegistry: JpaEntitiesRegistry,
//) : CryptoRepositoryFactory {
//    override fun create(tenantId: String): CryptoRepository {
//        val entityManagerFactory = if (CryptoTenants.isClusterTenant(tenantId)) {
//            // tenantID is crypto, P2P or REST; let's obtain connect to our cluster Crypto database 
//            dbConnectionManager.getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
//        } else {
//            // tenantID is a virtual node; let's connect to one of the virtual node Crypto databases
//            connectionsCache.let {
//                requireNotNull(it) {
//                    "${CryptoConnectionsFactoryImpl::connectionsCache::name} found null " +
//                            "Current component state is ${coordinator::status}"
//                }
//                it.get(tenantId) {
//                    dbConnectionManager.createEntityManagerFactory(
//                        connectionId = virtualNodeInfoReadService.getByHoldingIdentityShortHash(
//                            ShortHash.of(
//                                tenantId
//                            )
//                        )?.cryptoDmlConnectionId
//                            ?: throw IllegalStateException(
//                                "virtual node for $tenantId is not registered."
//                            ),
//                        entitiesSet = jpaEntitiesRegistry.get(CordaDb.Crypto.persistenceUnitName)
//                            ?: throw IllegalStateException(
//                                "persistenceUnitName ${CordaDb.Crypto.persistenceUnitName} is not registered."
//                            )
//                    )
//                }
//            }
//        }
//        // somehow figure out which version we want for  
//        when (tenantId) {
//            REST -> net.corda.crypto.persistence.v50ga.CryptoRepositoryImpl(entityManagerFactory)
//            else -> net.corda.crypto.persistence.v50beta2.CryptoRepositoryImpl(entityManagerFactory)
//        }
//    }
//}