package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.typesafe.config.ConfigException
import java.security.InvalidParameterException
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
import net.corda.crypto.config.impl.hsm
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.persistence.getEntityManagerFactory
import net.corda.crypto.softhsm.CryptoServiceProvider
import net.corda.crypto.softhsm.SoftCryptoServiceProvider
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
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
        private fun openRepository(tenantId: String) = WrappingRepositoryImpl(
            getEntityManagerFactory(
                tenantId,
                dbConnectionManager,
                virtualNodeInfoReadService,
                jpaEntitiesRegistry
            ),
            tenantId
        )

        @Suppress("ThrowsCount")
        fun getInstance(config: SmartConfig): CryptoService {
            logger.info("Creating instance of the {}", SoftCryptoService::class.java.name)
            val cachingConfig = config.getConfig("caching")
            val expireAfterAccessMins = cachingConfig.getConfig("expireAfterAccessMins").getLong("default")
            val maximumSize = cachingConfig.getConfig("maximumSize").getLong("default")
            val hsmConfig = config.hsm()
            val keysList = try {
                val hsmConfigObject = config.getConfig("hsm")
                hsmConfigObject.getConfigList("wrappingKeys")
            } catch (e: ConfigException) {
                throw InvalidParameterException("invalid wrappingKeys list: $e")
            }
            val unmanagedWrappingKeys: Map<String, WrappingKey> =
                keysList.map {
                    val alias = try {
                        it.getString("alias")
                    } catch (e: ConfigException) {
                        throw InvalidParameterException("alias missing or invalid: $e")
                    }
                    val salt = try {
                        it.getString("salt")
                    } catch (e: ConfigException) {
                        throw InvalidParameterException("salt missing or invalid: $e")
                    }
                    val passphrase = try {
                        it.getString("passphrase")
                    } catch (e: ConfigException) {
                        throw InvalidParameterException("passphrase missing or invalid: $e")
                    }
                    alias to WrappingKeyImpl.derive(schemeMetadata, passphrase, salt)
                }.toMap()
            val defaultUnmanagedWrappingKeyName = hsmConfig.defaultWrappingKey
            require(unmanagedWrappingKeys.containsKey(defaultUnmanagedWrappingKeyName)) {
                "default key $defaultUnmanagedWrappingKeyName must be in wrappingKeys"
            }
            // TODO drop this compatibility code that supports name between KEY_MAP_TRANSIENT_NAME and if so disables caching
            val wrappingKeyCache: Cache<String, WrappingKey> = cacheFactoryImpl.build(
                "HSM-Wrapping-Keys-Map",
                Caffeine.newBuilder()
                    .expireAfterAccess(expireAfterAccessMins, TimeUnit.MINUTES)
                    .maximumSize(maximumSize)
            )

            val privateKeyCache: Cache<PublicKey, PrivateKey> = CacheFactoryImpl().build(
                "HSM-Soft-Keys-Map",
                Caffeine.newBuilder()
                    .expireAfterAccess(expireAfterAccessMins, TimeUnit.MINUTES)
                    .maximumSize(maximumSize)
            )
            return SoftCryptoService(
                wrappingRepositoryFactory = ::openRepository,
                schemeMetadata = schemeMetadata,
                digestService = digestService,
                defaultUnmanagedWrappingKeyName = defaultUnmanagedWrappingKeyName,
                unmanagedWrappingKeys = unmanagedWrappingKeys,
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