package net.corda.crypto.persistence.impl

import java.security.PublicKey
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.SigningKeyStore
import net.corda.crypto.softhsm.CryptoRepository
import net.corda.crypto.softhsm.cryptoRepositoryFactory
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
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
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) : AbstractConfigurableComponent<SigningKeyStoreImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<SigningKeyStore>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
        )
    ),
    configKeys = setOf(CRYPTO_CONFIG)
), SigningKeyStore {

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = Impl(
        cryptoRepository = cryptoRepositoryFactory(
            CryptoTenants.CRYPTO,
            event.config.toCryptoConfig(),
            dbConnectionManager,
            jpaEntitiesRegistry,
            virtualNodeInfoReadService,
            keyEncodingService,
            digestService,
            layeredPropertyMapFactory,
        )
    )

    override fun save(tenantId: String, context: SigningKeySaveContext) =
        impl.save(tenantId, context)

    override fun find(tenantId: String, alias: String): SigningCachedKey? =
        impl.find(tenantId, alias)

    override fun find(tenantId: String, publicKey: PublicKey): SigningCachedKey? =
        impl.lookupByKey(tenantId, publicKey)

    override fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningCachedKey> =
        impl.lookup(tenantId, skip, take, orderBy, filter)

    override fun lookupByIds(tenantId: String, keyIds: List<ShortHash>): Collection<SigningCachedKey> =
        impl.lookupByKeyIds(tenantId, keyIds.toSet())

    override fun lookupByFullIds(tenantId: String, fullKeyIds: List<SecureHash>): Collection<SigningCachedKey> =
        impl.lookupByFullKeyIds(tenantId, fullKeyIds.toSet())

    class Impl(
        private val cryptoRepository: CryptoRepository,
    ) : DownstreamAlwaysUpAbstractImpl() {

        fun save(tenantId: String, context: SigningKeySaveContext) =
            cryptoRepository.saveSigningKey(tenantId, context)

        fun find(tenantId: String, alias: String): SigningCachedKey? =
            cryptoRepository.findSigningKey(tenantId, alias)

        fun lookupByKey(tenantId: String, publicKey: PublicKey): SigningCachedKey? =
            cryptoRepository.findSigningKey(tenantId, publicKey)

        fun lookup(
            tenantId: String,
            skip: Int,
            take: Int,
            orderBy: SigningKeyOrderBy,
            filter: Map<String, String>,
        ): Collection<SigningCachedKey> =
            cryptoRepository.lookupSigningKey(tenantId, skip, take, orderBy, filter)

        fun lookupByKeyIds(tenantId: String, requestedKeyIds: Set<ShortHash>): Collection<SigningCachedKey> =
            cryptoRepository.lookupSigningKeysByIds(tenantId, requestedKeyIds)

        fun lookupByFullKeyIds(tenantId: String, requestedFullKeyIds: Set<SecureHash>): Collection<SigningCachedKey> =
            cryptoRepository.lookupSigningKeysByFullIds(tenantId, requestedFullKeyIds)

        override fun close() {
            super.close()
            cryptoRepository.close()
        }
    }
}