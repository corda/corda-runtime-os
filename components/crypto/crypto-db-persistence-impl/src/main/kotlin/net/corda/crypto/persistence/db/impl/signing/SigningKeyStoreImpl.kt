package net.corda.crypto.persistence.db.impl.signing

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.impl.config.CryptoSigningPersistenceConfig
import net.corda.crypto.persistence.signing.SigningKeyStore
import net.corda.crypto.persistence.signing.SigningKeyStoreActions
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory

@Suppress("LongParameterList")
class SigningKeyStoreImpl(
    private val config: CryptoSigningPersistenceConfig,
    private val dbConnectionOps: DbConnectionOps,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    private val keyEncodingService: KeyEncodingService,
    private val vnodeInfo: VirtualNodeInfoReadService
) : SigningKeyStore {
    private val cache = ConcurrentHashMap<String, SigningKeyCache>()

    override fun act(tenantId: String): SigningKeyStoreActions {
        val cache = getCache(tenantId)
        return SigningKeyStoreActionsImpl(
            tenantId = cache.tenantId,
            entityManager = cache.entityManagerFactory.createEntityManager(),
            cache = cache.keys,
            layeredPropertyMapFactory = layeredPropertyMapFactory,
            keyEncodingService = keyEncodingService
        )
    }

    override fun close() {
        cache.values.forEach {
            it.clean()
        }
        cache.clear()
    }

    private fun getCache(tenantId: String): SigningKeyCache {
        return cache.computeIfAbsent(tenantId) {
            SigningKeyCache(
                tenantId = it,
                entityManagerFactory = getEntityManagerFactory(it),
                keys = Caffeine.newBuilder()
                    .expireAfterAccess(config.expireAfterAccessMins, TimeUnit.MINUTES)
                    .maximumSize(config.maximumSize)
                    .build()
            )
        }
    }
    private fun getEntityManagerFactory(tenantId: String): EntityManagerFactory =
        if (CryptoTenants.isClusterTenant(tenantId)) {
            dbConnectionOps.getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        } else {
            dbConnectionOps.createEntityManagerFactory(
                vnodeInfo.getById(tenantId)?.cryptoDmlConnectionId
                    ?: throw throw IllegalStateException(
                        "virtual node for $tenantId is not registered."
                    ),
                jpaEntitiesRegistry.get(CordaDb.Crypto.persistenceUnitName)
                    ?: throw IllegalStateException(
                        "persistenceUnitName ${CordaDb.Crypto.persistenceUnitName} is not registered."
                    )
            )
        }
}