package net.corda.crypto.persistence.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.config.impl.CryptoSigningServiceConfig
import net.corda.crypto.config.impl.signingService
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.core.fullId
import net.corda.crypto.core.publicKeyFullIdFromBytes
import net.corda.crypto.core.publicKeyShortIdFromBytes
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyFilterMapImpl
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.SigningKeyStatus
import net.corda.crypto.persistence.SigningKeyStore
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.alias
import net.corda.crypto.persistence.category
import net.corda.crypto.persistence.createdAfter
import net.corda.crypto.persistence.createdBefore
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityPrimaryKey
import net.corda.crypto.persistence.db.model.SigningKeyEntityStatus
import net.corda.crypto.persistence.externalId
import net.corda.crypto.persistence.masterKeyAlias
import net.corda.crypto.persistence.schemeCodeName
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.v5.crypto.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey
import java.time.Instant
import java.util.concurrent.TimeUnit

@Component(service = [SigningKeyStore::class])
class SigningKeyStoreImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = CryptoConnectionsFactory::class)
    private val connectionsFactory: CryptoConnectionsFactory,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService
) : AbstractConfigurableComponent<SigningKeyStoreImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<SigningKeyStore>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            LifecycleCoordinatorName.forComponent<CryptoConnectionsFactory>()
        )
    ),
    configKeys = setOf(CRYPTO_CONFIG)
), SigningKeyStore {

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = Impl(
        event,
        layeredPropertyMapFactory,
        keyEncodingService,
        connectionsFactory,
        digestService
    )

    override fun save(tenantId: String, context: SigningKeySaveContext) =
        impl.save(tenantId, context)

    override fun find(tenantId: String, alias: String): SigningCachedKey? =
        impl.find(tenantId, alias)

    override fun find(tenantId: String, publicKey: PublicKey): SigningCachedKey? =
        impl.find(tenantId, publicKey)

    override fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>
    ): Collection<SigningCachedKey> =
        impl.lookup(tenantId, skip, take, orderBy, filter)

    // TODO split below API to propagate back to Kafka receivers
    override fun lookup(tenantId: String, ids: List<String>): Collection<SigningCachedKey> =
        if (ids.isNotEmpty()) {
            // Assuming ids are all either short of full ids.
            if (ids[0].length == 12) {
                impl.lookUpByShortIds(tenantId, ids)
            } else {
                impl.lookupByKeyIds(tenantId, ids)
            }
        } else {
            listOf()
        }

    class Impl(
        event: ConfigChangedEvent,
        private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
        private val keyEncodingService: KeyEncodingService,
        private val connectionsFactory: CryptoConnectionsFactory,
        private val digestService: PlatformDigestService
    ) : DownstreamAlwaysUpAbstractImpl() {

        private val config: CryptoSigningServiceConfig = event.config.toCryptoConfig().signingService()

        // TODO make publicKeyId of type SecureHash
        private data class KeyId(val tenantId: String, val publicKeyId: String)
        private var byKeyIdsCache: Cache<KeyId, SigningCachedKey> = createCache()

        // Introduce key short id cache
//        private data class ShortKeyId(val tenantId: String, val publicKeyShortId: String)
//        private var cache: Cache<FullKeyId, SigningCachedKey> = createCache()

        override fun onUpstreamRegistrationStatusChange(isUpstreamUp: Boolean, isDownstreamUp: Boolean?) {
            if (!isUpstreamUp) {
                byKeyIdsCache = createCache()
            }
        }

        fun save(tenantId: String, context: SigningKeySaveContext) {
            val publicKeyId: String
            val publicKeyShortId: String
            val entity = when (context) {
                is SigningPublicKeySaveContext -> {
                    val publicKeyBytes = keyEncodingService.encodeAsByteArray(context.key.publicKey)
                    publicKeyId = publicKeyFullIdFromBytes(publicKeyBytes, digestService)
                    publicKeyShortId = publicKeyShortIdFromBytes(publicKeyBytes)
                    SigningKeyEntity(
                        tenantId = tenantId,
                        keyId = publicKeyId,
                        shortKeyId = publicKeyShortId,
                        timestamp = Instant.now(),
                        category = context.category,
                        schemeCodeName = context.keyScheme.codeName,
                        publicKey = publicKeyBytes,
                        keyMaterial = null,
                        encodingVersion = null,
                        masterKeyAlias = null,
                        alias = context.alias,
                        hsmAlias = context.key.hsmAlias,
                        externalId = context.externalId,
                        hsmId = context.hsmId,
                        status = SigningKeyEntityStatus.NORMAL
                    )
                }
                is SigningWrappedKeySaveContext -> {
                    val publicKeyBytes = keyEncodingService.encodeAsByteArray(context.key.publicKey)
                    publicKeyId = publicKeyFullIdFromBytes(publicKeyBytes, digestService)
                    publicKeyShortId = publicKeyShortIdFromBytes(publicKeyBytes)
                    SigningKeyEntity(
                        tenantId = tenantId,
                        keyId = publicKeyId,
                        shortKeyId = publicKeyShortId,
                        timestamp = Instant.now(),
                        category = context.category,
                        schemeCodeName = context.keyScheme.codeName,
                        publicKey = publicKeyBytes,
                        keyMaterial = context.key.keyMaterial,
                        encodingVersion = context.key.encodingVersion,
                        masterKeyAlias = context.masterKeyAlias,
                        alias = context.alias,
                        hsmAlias = null,
                        externalId = context.externalId,
                        hsmId = context.hsmId,
                        status = SigningKeyEntityStatus.NORMAL
                    )
                }
                else -> throw IllegalArgumentException("Unknown context type: ${context::class.java.name}")
            }
            entityManagerFactory(tenantId).transaction {
                it.persist(entity)
            }
            byKeyIdsCache.put(KeyId(tenantId, publicKeyId), entity.toSigningCachedKey())
            // TODO Populate short key ids cache
        }

        fun find(tenantId: String, alias: String): SigningCachedKey? {
            val result = findByAliases(tenantId, listOf(alias))
            if (result.size > 1) {
                throw IllegalStateException("There are more than one key with alias=$alias for tenant=$tenantId")
            }
            return result.firstOrNull()?.toSigningCachedKey()
        }

        fun find(tenantId: String, publicKey: PublicKey): SigningCachedKey? =
            byKeyIdsCache.get(KeyId(tenantId, publicKey.fullId(keyEncodingService, digestService))) { cacheKey ->
                entityManagerFactory(tenantId).use { em ->
                    em.find(
                        SigningKeyEntity::class.java, SigningKeyEntityPrimaryKey(
                            tenantId = tenantId,
                            keyId = cacheKey.publicKeyId
                        )
                    )?.toSigningCachedKey()
                }
            }

        // TODO check if needs renaming to `lookupByKeyIds`
        fun lookup(
            tenantId: String,
            skip: Int,
            take: Int,
            orderBy: SigningKeyOrderBy,
            filter: Map<String, String>
        ): Collection<SigningCachedKey> = entityManagerFactory(tenantId).use { em ->
            val map = layeredPropertyMapFactory.create<SigningKeyFilterMapImpl>(filter)
            val builder = SigningKeyLookupBuilder(em)
            builder.equal(SigningKeyEntity::tenantId, tenantId)
            builder.equal(SigningKeyEntity::category, map.category)
            builder.equal(SigningKeyEntity::schemeCodeName, map.schemeCodeName)
            builder.equal(SigningKeyEntity::alias, map.alias)
            builder.equal(SigningKeyEntity::masterKeyAlias, map.masterKeyAlias)
            builder.equal(SigningKeyEntity::externalId, map.externalId)
            builder.greaterThanOrEqualTo(SigningKeyEntity::timestamp, map.createdAfter)
            builder.lessThanOrEqualTo(SigningKeyEntity::timestamp, map.createdBefore)
            builder.build(skip, take, orderBy).resultList.map {
                it.toSigningCachedKey()
            }
        }

        /**
         * @param ids Public key hashes of the public keys in form of "<digest algorithm>:<hash in hex string form>"
         */
        // TODO make following ids of type List<SecureHash> and a switch for shortId or fullId
        fun lookupByKeyIds(tenantId: String, ids: List<String>): Collection<SigningCachedKey> {
            require(ids.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
                "The number of ids exceeds $KEY_LOOKUP_INPUT_ITEMS_LIMIT"
            }
            val cached = byKeyIdsCache.getAllPresent(ids.map { KeyId(tenantId, it) })
            if (cached.size == ids.size) {
                return cached.values
            }
            val notFound = ids.filter { id -> !cached.containsKey(KeyId(tenantId, id)) }
            val fetched = findByIds(tenantId, notFound).map {
                it.toSigningCachedKey()
            }.distinctBy {
                it.id
            }
            fetched.forEach {
                byKeyIdsCache.put(KeyId(tenantId, it.id), it)
            }
            return cached.values + fetched
        }

        // TODO Add caching
        fun lookUpByShortIds(tenantId: String, ids: List<String>): Collection<SigningCachedKey> {
            require(ids.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
                "The number of ids exceeds $KEY_LOOKUP_INPUT_ITEMS_LIMIT"
            }

            val fetched = findByShortIds(tenantId, ids).map {
                it.toSigningCachedKey()
            }.distinctBy {
                it.id
            }
            return fetched
        }

        // TODO make ids of type List<SecureHash>
        private fun findByIds(tenantId: String, ids: Collection<String>): Collection<SigningKeyEntity> =
            entityManagerFactory(tenantId).use { em ->
                em.createQuery(
                    "FROM SigningKeyEntity WHERE tenantId=:tenantId AND keyId IN(:ids)",
                    SigningKeyEntity::class.java
                ).also { q ->
                    q.setParameter("tenantId", tenantId)
                    q.setParameter("ids", ids)
                }.resultList
            }

        private fun findByShortIds(tenantId: String, shortIds: Collection<String>): Collection<SigningKeyEntity> =
            entityManagerFactory(tenantId).use { em ->
                em.createQuery(
                    "FROM ${SigningKeyEntity::class.java.simpleName} " +
                            "WHERE tenantId=:tenantId " +
                            "AND shortKeyId IN(:shortIds) " +
                            "ORDER BY timestamp",
                    SigningKeyEntity::class.java
                ).setParameter("tenantId", tenantId)
                    .setParameter("shortIds", shortIds)
                    .resultList
            }

        private fun findByAliases(tenantId: String, aliases: Collection<String>): Collection<SigningKeyEntity> =
            entityManagerFactory(tenantId).use { em ->
                em.createQuery(
                    "FROM SigningKeyEntity WHERE tenantId=:tenantId AND alias IN(:aliases)",
                    SigningKeyEntity::class.java
                ).also { q ->
                    q.setParameter("tenantId", tenantId)
                    q.setParameter("aliases", aliases)
                }.resultList
            }

        private fun SigningKeyEntity.toSigningCachedKey(): SigningCachedKey =
            SigningCachedKey(
                id = keyId,
                shortId = shortKeyId,
                tenantId = tenantId,
                category = category,
                alias = alias,
                hsmAlias = hsmAlias,
                publicKey = publicKey,
                keyMaterial = keyMaterial,
                schemeCodeName = schemeCodeName,
                masterKeyAlias = masterKeyAlias,
                externalId = externalId,
                encodingVersion = encodingVersion,
                timestamp = timestamp,
                hsmId = hsmId,
                status = SigningKeyStatus.valueOf(status.name)
            )

        private fun entityManagerFactory(tenantId: String) = connectionsFactory.getEntityManagerFactory(tenantId)

        private fun createCache(): Cache<KeyId, SigningCachedKey> = CacheFactoryImpl().build(
            "Signing-Key-Cache",
            Caffeine.newBuilder()
                .expireAfterAccess(config.cache.expireAfterAccessMins, TimeUnit.MINUTES)
                .maximumSize(config.cache.maximumSize))
    }
}