package net.corda.crypto.softhsm.impl

import java.security.PublicKey
import javax.persistence.EntityManagerFactory
import net.corda.cache.caffeine.CacheFactory
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
import net.corda.crypto.persistence.impl.SigningKeysRepositoryImpl
import net.corda.crypto.softhsm.CryptoRepository
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.v5.crypto.SecureHash

class V1CryptoRepositoryImpl(
    private val entityManagerFactory: EntityManagerFactory,
    private val cacheFactory: CacheFactory,
    keyEncodingService: KeyEncodingService,
    digestService: PlatformDigestService,
    layeredPropertyMapFactory: LayeredPropertyMapFactory,
    config: SmartConfig,
) : CryptoRepository {

    private val wrappingKeyStore = WrappingKeyStore(entityManagerFactory)

    private val signingKeyStore = SigningKeyStore(
        CryptoSigningServiceConfig(config),
        layeredPropertyMapFactory,
        keyEncodingService,
        entityManagerFactory,
        digestService,
        SigningKeysRepositoryImpl,
    )
    private val hsmStore = HSMStore(entityManagerFactory)

    override fun saveWrappingKey(alias: String, key: WrappingKeyInfo) = wrappingKeyStore.saveWrappingKey(alias, key)
    override fun findWrappingKey(alias: String): WrappingKeyInfo? = wrappingKeyStore.findWrappingKey(alias)
    override fun close() = entityManagerFactory.close()

    data class CacheKey(val tenantId: String, val publicKeyId: ShortHash)

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
