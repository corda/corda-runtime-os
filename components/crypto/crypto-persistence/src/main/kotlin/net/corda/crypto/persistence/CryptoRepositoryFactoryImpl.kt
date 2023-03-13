package net.corda.crypto.persistence

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactory
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.CryptoTenants.REST
import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.v1schema.V1CryptoRepositoryImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege

import net.corda.db.schema.CordaDb
import net.corda.orm.JpaEntitiesRegistry
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory


@Component(service = [CryptoRepositoryFactory::class])
class CryptoRepositoryFactoryImpl
@Activate constructor(
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CacheFactory::class)
    private val cacheFactory: CacheFactory
) : CryptoRepositoryFactory {
    private val connectionsCache: Cache<String, EntityManagerFactory> = cacheFactory.build(
        "Crypto-Db-Connections-Cache",
        Caffeine.newBuilder()
            .expireAfterAccess(60 * 24, TimeUnit.MINUTES) // TODO dynamic config
            .maximumSize(3600) // TODO dynamic config
            .evictionListener { _, value, _ -> value?.close() })

    override fun create(tenantId: String): CryptoRepository {
        val entityManagerFactory = if (CryptoTenants.isClusterTenant(tenantId)) {
            // tenantID is crypto, P2P or REST; let's obtain connect to our cluster Crypto database 
            dbConnectionManager.getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        } else {
            // tenantID is a virtual node; let's connect to one of the virtual node Crypto databases
            connectionsCache.get(tenantId) {
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
        }
//        // somehow figure out which version we want, e.g.
//        when (entityManagerFactory.getSchemaVersion()) {
//            1 -> V1CryptoRepositoryImpl(entityManagerFactory)
//            else -> V2CryptoRepositoryImpl(entityManagerFactory)
//        }
        return V1CryptoRepositoryImpl({ entityManagerFactory.createEntityManager() })
    }
}