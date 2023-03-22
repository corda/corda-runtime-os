package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.security.PublicKey
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.config.impl.CryptoSigningServiceConfig
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.HSMUsage
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.CryptoRepository
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.v5.crypto.SecureHash

@Suppress("LongParameterList")
class V1CryptoRepositoryImpl(
    private val entityManagerFactory: EntityManagerFactory,
    cache: Cache<V1SigningKeyStore.CacheKey, SigningCachedKey>,
    keyEncodingService: KeyEncodingService,
    digestService: PlatformDigestService,
    layeredPropertyMapFactory: LayeredPropertyMapFactory,
) : CryptoRepository {

    companion object {
        fun createCache(config: CryptoSigningServiceConfig): Cache<V1SigningKeyStore.CacheKey, SigningCachedKey> =
            CacheFactoryImpl().build(
                "Signing-Key-Cache",
                Caffeine.newBuilder()
                    .expireAfterAccess(config.cache.expireAfterAccessMins, TimeUnit.MINUTES)
                    .maximumSize(config.cache.maximumSize)
            )
    }

    private val wrappingKeyStore = V1WrappingKeyStore(entityManagerFactory)

    private val signingKeyStore = V1SigningKeyStore(
        cache,
        layeredPropertyMapFactory,
        keyEncodingService,
        entityManagerFactory,
        digestService,
        SigningKeysRepositoryImpl,
    )
    private val hsmStore = V1HSMStore(entityManagerFactory)

    override fun saveWrappingKey(alias: String, key: WrappingKeyInfo) = wrappingKeyStore.saveWrappingKey(alias, key)
    override fun findWrappingKey(alias: String): WrappingKeyInfo? = wrappingKeyStore.findWrappingKey(alias)
    override fun close() = entityManagerFactory.close()

    /**
     * If short key id clashes with existing key for this [tenantId], [saveSigningKey] will fail. It will
     * fail upon persisting to the DB due to unique constraint of <tenant id, short key id>.
     */
    override fun saveSigningKey(tenantId: String, context: SigningKeySaveContext) =
        signingKeyStore.save(tenantId, context)

    override fun findSigningKey(tenantId: String, alias: String): SigningCachedKey? =
        signingKeyStore.find(tenantId, alias)

    override fun findSigningKey(tenantId: String, publicKey: PublicKey): SigningCachedKey? =
        signingKeyStore.lookupByKey(tenantId, publicKey)

    override fun lookupSigningKey(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningCachedKey> =
        signingKeyStore.lookup(tenantId, skip, take, orderBy, filter)

    override fun lookupSigningKeysByIds(tenantId: String, keyIds: Set<ShortHash>): Collection<SigningCachedKey> =
        signingKeyStore.lookupByKeyIds(tenantId, keyIds)

    override fun lookupSigningKeysByFullIds(
        tenantId: String,
        fullKeyIds: Set<SecureHash>,
    ): Collection<SigningCachedKey> =
        signingKeyStore.lookupByFullKeyIds(tenantId, fullKeyIds)

    override fun findTenantAssociation(tenantId: String, category: String): HSMAssociationInfo? =
        hsmStore.findTenantAssociation(tenantId, category)

    override fun getHSMUsage(): List<HSMUsage> = hsmStore.getHSMUsage()

    override fun associate(
        tenantId: String,
        category: String,
        hsmId: String,
        masterKeyPolicy: MasterKeyPolicy,
    ): HSMAssociationInfo =
        hsmStore.associate(tenantId, category, hsmId, masterKeyPolicy)

}
