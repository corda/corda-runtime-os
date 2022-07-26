package net.corda.crypto.persistence.db.impl.signing

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.impl.config.CryptoSigningPersistenceConfig
import net.corda.crypto.persistence.signing.SigningCachedKey
import net.corda.crypto.persistence.signing.SigningKeyStore
import net.corda.crypto.persistence.signing.SigningKeyStoreActions
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.virtualnode.read.VirtualNodeInfoReadService
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
    private val keys: Cache<String, Cache<String, SigningCachedKey>> = Caffeine.newBuilder()
        .expireAfterAccess(config.vnodesExpireAfterAccessMins, TimeUnit.MINUTES)
        .maximumSize(config.vnodeNumberLimit)
        .build()

    private val connections: Cache<String, EntityManagerFactory> = Caffeine.newBuilder()
        .expireAfterAccess(config.connectionsExpireAfterAccessMins, TimeUnit.MINUTES)
        .maximumSize(config.connectionNumberLimit)
        .evictionListener<String, EntityManagerFactory> { _, value, _ -> value?.close() }
        .build()

    override fun act(tenantId: String): SigningKeyStoreActions {
        val keys = getKeys(tenantId)
        return SigningKeyStoreActionsImpl(
            tenantId = tenantId,
            entityManager = getEntityManagerFactory(tenantId).createEntityManager(),
            cache = keys,
            layeredPropertyMapFactory = layeredPropertyMapFactory,
            keyEncodingService = keyEncodingService
        )
    }

    override fun close() {
        connections.invalidateAll()
        connections.cleanUp()
        keys.invalidateAll()
        keys.cleanUp()
    }

    private fun getKeys(tenantId: String): Cache<String, SigningCachedKey> {
        return keys.get(tenantId) {
            Caffeine.newBuilder()
                .expireAfterAccess(config.keysExpireAfterAccessMins, TimeUnit.MINUTES)
                .maximumSize(config.keyNumberLimit)
                .build()
        }
    }

    private fun getEntityManagerFactory(tenantId: String): EntityManagerFactory =
        if (CryptoTenants.isClusterTenant(tenantId)) {
            dbConnectionOps.getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        } else {
            connections.get(tenantId) {
                createEntityManagerFactory(tenantId)
            }
        }

    private fun createEntityManagerFactory(tenantId: String) = dbConnectionOps.createEntityManagerFactory(
        vnodeInfo.getByHoldingIdentityShortHash(tenantId)?.cryptoDmlConnectionId
            ?: throw throw IllegalStateException(
                "virtual node for $tenantId is not registered."
            ),
        jpaEntitiesRegistry.get(CordaDb.Crypto.persistenceUnitName)
            ?: throw IllegalStateException(
                "persistenceUnitName ${CordaDb.Crypto.persistenceUnitName} is not registered."
            )
    )
}
