package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.util.concurrent.TimeUnit
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.persistence.getEntityManagerFactory
import net.corda.crypto.softhsm.CryptoServiceProvider
import net.corda.crypto.softhsm.SoftCryptoServiceProvider
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.getStringOrDefault
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

const val KEY_MAP_TRANSIENT_NAME = "TRANSIENT"
const val KEY_MAP_CACHING_NAME = "CACHING"
const val WRAPPING_DEFAULT_NAME = "DEFAULT"

/**
 * A factory that creates [SoftCryptoService] instances given configuration.
 *
 * The service ranking is set to the smallest possible number to allow the upstream implementation to pick other
 * providers as this one will always be present in the deployment.
 */

// This class is really a simple factory routine with dynamic configuration, lifecycle, and OSGi dependency
// injection. It is hard to unit test, since it requires a lot of code to instantiate in a useful way. So,
// the QA strategy for this code is to rely in smoke tests and e2e tests.

@Suppress("LongParameterList")
@ServiceRanking(Int.MIN_VALUE)
@Component(service = [CryptoServiceProvider::class, SoftCryptoServiceProvider::class])
open class SoftCryptoServiceProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = ConfigurationReadService::class)
    private val configReadService: ConfigurationReadService,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) : AbstractConfigurableComponent<SoftCryptoServiceProviderImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = lifecycleCoordinatorName,
    configurationReadService = configReadService,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        )
    ),
    configKeys = setOf(CRYPTO_CONFIG)
), SoftCryptoServiceProvider {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<SoftCryptoServiceProvider>()
    }

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = Impl(
        schemeMetadata,
        digestService,
        CacheFactoryImpl(),
        dbConnectionManager,
        virtualNodeInfoReadService,
        jpaEntitiesRegistry
    )

    override fun getInstance(config: SmartConfig): CryptoService = impl.getInstance(config)

    override val lifecycleName: LifecycleCoordinatorName get() = lifecycleCoordinatorName

    class Impl(
        private val schemeMetadata: CipherSchemeMetadata,
        private val digestService: PlatformDigestService,
        private val cacheFactoryImpl: CacheFactoryImpl,
        private val dbConnectionManager: DbConnectionManager,
        private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
        private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    ) : AbstractImpl {
        private fun openRepository(tenantId: String) =  WrappingRepositoryImpl(
            getEntityManagerFactory(
                tenantId,
                dbConnectionManager,
                virtualNodeInfoReadService,
                jpaEntitiesRegistry
            )
        )

        fun getInstance(config: SmartConfig): CryptoService {
            logger.info("Creating instance of the {}", SoftCryptoService::class.java.name)
            val wrappingKeyMapConfig = config.getConfig("wrappingKeyMap")
            val rootWrappingKey =
                WrappingKeyImpl.derive(
                    schemeMetadata,
                    wrappingKeyMapConfig.getString("salt"),
                    wrappingKeyMapConfig.getString("passphrase")
                )
            val wrappingKeyCacheConfig = wrappingKeyMapConfig.getConfig("cache")
            // TODO drop this compatibility code that supports name between KEY_MAP_TRANSIENT_NAME and if so disables caching
            val wrappingKeyCache: Cache<String, WrappingKey> = cacheFactoryImpl.build(
                "HSM-Wrapping-Keys-Map",
                Caffeine.newBuilder()
                    .expireAfterAccess(wrappingKeyCacheConfig.getLong("expireAfterAccessMins"), TimeUnit.MINUTES)
                    .maximumSize(
                        if (wrappingKeyCacheConfig.getStringOrDefault("name", "") == KEY_MAP_TRANSIENT_NAME) 0
                        else wrappingKeyCacheConfig.getLong("maximumSize")
                    )
            )

            val privateKeyMapName = config.getConfig("keyMap").getStringOrDefault("name", KEY_MAP_CACHING_NAME)
            if (privateKeyMapName != KEY_MAP_CACHING_NAME && privateKeyMapName != KEY_MAP_TRANSIENT_NAME)
                throw java.lang.IllegalStateException("unknown name $privateKeyMapName")
            val privateKeyMapCacheConfig = config.getConfig("keyMap").getConfig("cache")
            val privateKeyCache: Cache<PublicKey, PrivateKey> = CacheFactoryImpl().build(
                "HSM-Soft-Keys-Map",
                Caffeine.newBuilder()
                    .expireAfterAccess(privateKeyMapCacheConfig.getLong("expireAfterAccessMins"), TimeUnit.MINUTES)
                    .maximumSize(
                        if (privateKeyMapName == KEY_MAP_TRANSIENT_NAME) 0 else privateKeyMapCacheConfig.getLong(
                            "maximumSize"
                        )
                    )
            )
            return SoftCryptoService(
                wrappingRepositoryFactory = ::openRepository,
                schemeMetadata = schemeMetadata,
                digestService = digestService,
                rootWrappingKey = rootWrappingKey,
                wrappingKeyCache = wrappingKeyCache,
                privateKeyCache = privateKeyCache,
                keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
                    KeyPairGenerator.getInstance(algorithm, provider)
                },
                wrappingKeyFactory = {
                    WrappingKeyImpl.generateWrappingKey(it)
                }
            )
        }

        override val downstream: DependenciesTracker
            get() = DependenciesTracker.AlwaysUp()

    }
}