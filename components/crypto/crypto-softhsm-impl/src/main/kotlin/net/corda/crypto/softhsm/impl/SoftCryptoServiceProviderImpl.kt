package net.corda.crypto.softhsm.impl

import com.typesafe.config.Config
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.CryptoServiceProvider
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.component.impl.AbstractComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_SERVICE_NAME
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.persistence.WrappingKeyStore
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
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.InvalidParameterException

/**
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
    @Reference(service = WrappingKeyStore::class)
    private val store: WrappingKeyStore
) : AbstractComponent<SoftCryptoServiceProviderImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = lifecycleCoordinatorName,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<WrappingKeyStore>()
        )
    )
), SoftCryptoServiceProvider {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<SoftCryptoServiceProvider>()
    }

    override fun createActiveImpl(): Impl = Impl(schemeMetadata, digestService, store)

    override val name: String = SOFT_HSM_SERVICE_NAME
    override fun getInstance(config: SmartConfig): CryptoService = impl.getInstance(config)

    override val lifecycleName: LifecycleCoordinatorName get() = lifecycleCoordinatorName

    class Impl(
        private val schemeMetadata: CipherSchemeMetadata,
        private val digestService: PlatformDigestService,
        private val store: WrappingKeyStore
    ) : AbstractImpl {

        fun getInstance(config: SmartConfig): CryptoService {
            // TODO - find a way to avoid magic strings
            logger.info("Creating instance of the {}", SoftCryptoService::class.java.name)
            val wrappingKeyMap = createSoftWrappingKeyMap(config.getConfig("wrappingKeyMap"))
            val privateKeyWrapping = createSoftPrivateKeyWrapping(config, wrappingKeyMap)
            val keyMapCacheConfig = SoftCacheConfig(
                config.getConfig("keyMap").getLong("expireAfterAccessMins"),
                config.getConfig("keyMap").getLong("maximumSize")
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
            config: Config?
        ): SoftWrappingKeyMap {
            // TODO - find a way to avoid magic strings
            if (config == null) throw InvalidParameterException("wrappingKeyMap not configured")
            val name = config.getString("name")
            val subConfig = config.getConfig("wrappingKeyMap")
            val wrappingKeyMapCacheConfig = SoftCacheConfig(
                subConfig.getConfig("cache").getLong("expireAfterAccessMins"),
                subConfig.getConfig("maximumSize").getLong("maximumSize")
            )
            val softWrappingKeyMapConfig = SoftWrappingKeyMapConfig(
                subConfig.getString("name"),
                subConfig.getString("salt"),
                subConfig.getString("passphrase"),
                wrappingKeyMapCacheConfig
            )
            return when (name) {
                KEY_MAP_TRANSIENT_NAME -> TransientSoftWrappingKeyMap(
                    store,
                    createMasterWrappingKey(softWrappingKeyMapConfig)
                )
                KEY_MAP_CACHING_NAME -> CachingSoftWrappingKeyMap(
                    wrappingKeyMapCacheConfig,
                    store,
                    createMasterWrappingKey(softWrappingKeyMapConfig)
                )
                else -> throw IllegalStateException(
                    "Unknown configuration value '$name}' for " +
                            "${SoftWrappingKeyMap::class.java.simpleName}, must be " +
                            "$KEY_MAP_TRANSIENT_NAME or $KEY_MAP_CACHING_NAME."
                )
            }
        }

        // TODO - rework to use Config directly
        private fun createMasterWrappingKey(config: SoftWrappingKeyMapConfig) =
            WrappingKey.derive(schemeMetadata, config.salt, config.passphrase)

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