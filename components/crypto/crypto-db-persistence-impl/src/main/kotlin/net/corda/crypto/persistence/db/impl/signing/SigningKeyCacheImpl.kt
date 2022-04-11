package net.corda.crypto.persistence.db.impl.signing

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyCache
import net.corda.crypto.persistence.SigningKeyCacheActions
import net.corda.crypto.persistence.config.softPersistence
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.v5.cipher.suite.KeyEncodingService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory

class SigningKeyCacheImpl(
    private val config: SmartConfig,
    private val dbConnectionOps: DbConnectionOps,
    private val keyEncodingService: KeyEncodingService
) : SigningKeyCache {
    private val cache = ConcurrentHashMap<String, Cache<String, SigningCachedKey>>()

    override fun act(tenantId: String): SigningKeyCacheActions {
        return SigningKeyCacheActionsImpl(
            tenantId = tenantId,
            entityManager = getEntityManagerFactory(tenantId).createEntityManager(),
            cache = getCache(tenantId),
            keyEncodingService = keyEncodingService
        )
    }

    override fun close() {
        cache.values.forEach {
            it.invalidateAll()
        }
        cache.clear()
    }

    private fun getEntityManagerFactory(tenantId: String): EntityManagerFactory =
        when (tenantId) {
            CryptoConsts.CLUSTER_TENANT_ID -> dbConnectionOps.getOrCreateEntityManagerFactory(
                CordaDb.Crypto,
                DbPrivilege.DML
            )
            else -> throw NotImplementedError()
        }

    private fun getCache(tenantId: String): Cache<String, SigningCachedKey> =
        cache.computeIfAbsent(tenantId) {
            Caffeine.newBuilder()
                .expireAfterAccess(config.softPersistence.expireAfterAccessMins, TimeUnit.MINUTES)
                .maximumSize(config.softPersistence.maximumSize)
                .build()
        }
}