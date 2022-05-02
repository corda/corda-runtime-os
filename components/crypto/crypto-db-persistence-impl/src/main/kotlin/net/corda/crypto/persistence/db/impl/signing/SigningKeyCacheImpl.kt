package net.corda.crypto.persistence.db.impl.signing

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.impl.config.signingPersistence
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyCache
import net.corda.crypto.persistence.SigningKeyCacheActions
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.JpaEntitiesRegistry
import net.corda.v5.cipher.suite.KeyEncodingService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory

class SigningKeyCacheImpl(
    private val config: SmartConfig,
    private val dbConnectionOps: DbConnectionOps,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    private val keyEncodingService: KeyEncodingService
) : SigningKeyCache {
    private val cache = ConcurrentHashMap<String, Cache<String, SigningCachedKey>>()

    override fun act(tenantId: String): SigningKeyCacheActions {
        return SigningKeyCacheActionsImpl(
            tenantId = tenantId,
            entityManager = getEntityManagerFactory(tenantId).createEntityManager(),
            cache = buildCache(tenantId),
            layeredPropertyMapFactory = layeredPropertyMapFactory,
            keyEncodingService = keyEncodingService
        )
    }

    override fun close() {
        cache.values.forEach {
            it.invalidateAll()
            it.cleanUp()
        }
        cache.clear()
    }

    private fun getEntityManagerFactory(tenantId: String): EntityManagerFactory =
        when (tenantId) {
            CryptoConsts.CLUSTER_TENANT_ID -> dbConnectionOps.getOrCreateEntityManagerFactory(
                CordaDb.Crypto,
                DbPrivilege.DML
            )
            else -> dbConnectionOps.getOrCreateEntityManagerFactory(
                    "vnode_crypto_$tenantId",
                    DbPrivilege.DML,
                    jpaEntitiesRegistry.get(CordaDb.Crypto.persistenceUnitName)
                        ?: throw java.lang.IllegalStateException(
                            "persistenceUnitName ${CordaDb.Crypto.persistenceUnitName} is not registered."
                        )
                )
        }

    private fun buildCache(tenantId: String): Cache<String, SigningCachedKey> {
        val persistenceConfig = config.signingPersistence()
        return cache.computeIfAbsent(tenantId) {
            Caffeine.newBuilder()
                .expireAfterAccess(persistenceConfig.expireAfterAccessMins, TimeUnit.MINUTES)
                .maximumSize(persistenceConfig.maximumSize)
                .build()
        }
    }
}