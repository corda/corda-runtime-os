package net.corda.crypto.softhsm.impl

import com.typesafe.config.Config
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.component.impl.AbstractComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.softhsm.CryptoServiceProvider
import net.corda.crypto.softhsm.KEY_MAP_CACHING_NAME
import net.corda.crypto.softhsm.KEY_MAP_TRANSIENT_NAME
import net.corda.crypto.softhsm.SoftCacheConfig
import net.corda.crypto.softhsm.SoftCryptoServiceProvider
import net.corda.crypto.softhsm.SoftKeyMap
import net.corda.crypto.softhsm.SoftPrivateKeyWrapping
import net.corda.crypto.softhsm.SoftWrappingKeyMap
import net.corda.crypto.softhsm.SoftWrappingKeyMapConfig
import net.corda.crypto.softhsm.WRAPPING_DEFAULT_NAME
import net.corda.crypto.softhsm.WRAPPING_HSM_NAME
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.getStringOrDefault
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
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
@ServiceRanking(Int.MIN_VALUE)
@Component(service = [CryptoServiceProvider::class, SoftCryptoServiceProvider::class])
open class SoftCryptoServiceProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService,
    @Reference(service = CryptoConnectionsFactory::class)
    private val connectionsFactory: CryptoConnectionsFactory
) : AbstractComponent<SoftCryptoServiceProviderImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = lifecycleCoordinatorName,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<CryptoConnectionsFactory>()
        )
    )
), SoftCryptoServiceProvider {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<SoftCryptoServiceProvider>()
    }

    override fun createActiveImpl(): Impl = Impl(schemeMetadata, digestService, connectionsFactory)

    override fun getInstance(config: SmartConfig): CryptoService = impl.getInstance(config)

    override val lifecycleName: LifecycleCoordinatorName get() = lifecycleCoordinatorName

    class Impl(
        private val schemeMetadata: CipherSchemeMetadata,
        private val digestService: PlatformDigestService,
        private val connectionsFactory: CryptoConnectionsFactory
    ) : AbstractImpl {

        fun getInstance(config: SmartConfig): CryptoService {
            // TODO - find a way to avoid magic strings
            logger.info("Creating instance of the {}", SoftCryptoService::class.java.name)
            val wrappingKeyMap = createSoftWrappingKeyMap(config.getConfig("wrappingKeyMap"))
            val privateKeyWrapping = createSoftPrivateKeyWrapping(config, wrappingKeyMap)
            val keyMapCacheConfig = SoftCacheConfig(
                config.getConfig("keyMap").getConfig("cache").getLong("expireAfterAccessMins"),
                config.getConfig("keyMap").getConfig("cache").getLong("maximumSize")
            )
            val keyMap = createSoftKeyMap(config, privateKeyWrapping, keyMapCacheConfig)
            return SoftCryptoService(
                keyMap = keyMap,
                wrappingKeyMap = wrappingKeyMap,
                schemeMetadata = schemeMetadata,
                digestService = digestService
            )
        }

        private fun createSoftWrappingKeyMap(
            config: SmartConfig
        ): SoftWrappingKeyMap {
            // TODO - find a way to avoid magic strings
            val name = config.getStringOrDefault("name", KEY_MAP_CACHING_NAME)
            val wrappingKeyMapCacheConfig = SoftCacheConfig(
                config.getConfig("cache").getLong("expireAfterAccessMins"),
                config.getConfig("cache").getLong("maximumSize")
            )
            val softWrappingKeyMapConfig = SoftWrappingKeyMapConfig(
                config.getString("salt"),
                config.getString("passphrase"),
                wrappingKeyMapCacheConfig
            )
            val masterKey = createMasterWrappingKey(softWrappingKeyMapConfig)
            logger.info("set up soft wrapping key map with master key $masterKey")
            val finalWrappingKeyMapCacheConfig = when (name) {
                KEY_MAP_TRANSIENT_NAME -> SoftCacheConfig(0, 0)
                else -> wrappingKeyMapCacheConfig
            }
            return CachingSoftWrappingKeyMap(finalWrappingKeyMapCacheConfig, masterKey, connectionsFactory)
        }

        // TODO - rework to use Config directly
        private fun createMasterWrappingKey(config: SoftWrappingKeyMapConfig): WrappingKey {
            logger.info("Deriving master key salt=${config.salt} passphrase=${config.passphrase}")
            val k = WrappingKey.derive(schemeMetadata, config.salt, config.passphrase)
            logger.info("master key is $k")
            return k
        }

        private fun createSoftPrivateKeyWrapping(
            config: Config,
            wrappingKeyMap: SoftWrappingKeyMap
        ): SoftPrivateKeyWrapping = when (config.getConfig("wrapping").getString("name")) {
            // TODO - find a way to avoid magic strings
            WRAPPING_DEFAULT_NAME -> DefaultSoftPrivateKeyWrapping(wrappingKeyMap)
            WRAPPING_HSM_NAME -> throw NotImplementedError(
                "${SoftPrivateKeyWrapping::class.java.simpleName} is not implemented."
            )
            else -> throw IllegalStateException(
                "Unknown configuration value '${config.getConfig("wrapping").getString("name")}' for " +
                        "${SoftPrivateKeyWrapping::class.java.simpleName}, must be " +
                        "$WRAPPING_DEFAULT_NAME or $WRAPPING_HSM_NAME"
            )
        }

        private fun createSoftKeyMap(
            config: Config,
            wrapping: SoftPrivateKeyWrapping,
            cacheConfig: SoftCacheConfig
        ): SoftKeyMap = when (config.getConfig("keyMap").getString("name")) {
            KEY_MAP_TRANSIENT_NAME -> TransientSoftKeyMap(wrapping)
            KEY_MAP_CACHING_NAME -> CachingSoftKeyMap(cacheConfig, wrapping)
            else -> throw IllegalStateException(
                "Unknown configuration value '${config.getConfig("keyMap").getString("cache")}' for " +
                        "${SoftKeyMap::class.java.simpleName}, must be " +
                        "$KEY_MAP_TRANSIENT_NAME or $KEY_MAP_CACHING_NAME."
            )
        }
    }
}